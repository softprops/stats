package stats

import java.net._
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.Random //import java.util.concurrent.ThreadLocalRandom ( added in java 7 )
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

abstract class Sampled[T:Serialize] {
  def sample(rate: Double): Sampled[T]
  def add(value: T)(implicit ec: ExecutionContext): Future[Unit]
  def apply(value: T): Stat
}

abstract class Counter[T:Numeric] {
  val num = implicitly[Numeric[T]]
  def sample(rate: Double): Counter[T]
  def incr(implicit ec: ExecutionContext): Future[Unit] =
    incr(num.default)(ec)
  def incr(value: T)(implicit ec: ExecutionContext): Future[Unit]
  def decr(implicit ec: ExecutionContext): Future[Unit] =
    incr(num.negate(num.default))(ec)
  def apply(value: T): Stat
}

trait Stat {
  def lines: Iterable[String]
  def sampled: Boolean
}

/**
 * An statsd client that supports asyncronous sending of singular and multi stats.
 * Connections to remote host is lazily evaluated until an actual request to send.
 * Supports numeric counters, finite duration guages, and arbitraryily valued guages and sets.
 * All metric values should be serializable as formated strings. This is enforced via
 * implicit instances of Serialize for a given type T in scope.
 */
case class Stats(
  host: String = "0.0.0.0",
  port: Int = 8125,
  packetSize: Int = 1024) {
  import stats.Serialize._

  private[this] lazy val rand    = new Random()
  private[this] lazy val buffer  = ByteBuffer.allocate(packetSize)
  private[this] lazy val address = new InetSocketAddress(InetAddress.getByName(host), port)
  private[this] lazy val channel = DatagramChannel.open()

  def nextDouble = rand.nextDouble // ThreadLocalRandom.current().nextDouble ( java 7 )

  /** A stat captures a metric unit, value, sampleRate and one or more keys to associate with it */
  case class Lines[T: Serialize](
    unit: String, keys: List[String], value: T, sampleRate: Double) extends Stat {
    private[this] val ser = implicitly[Serialize[T]]

    def sampled =
      (sampleRate >= 1
       || nextDouble <= sampleRate)

    lazy val lines =
      keys.map(key => s"$key:${ser(value)}|$unit${if (sampleRate < 1) "|@"+sampleRate else ""}")
  }

  private[this] def newCounter[T:Numeric]
    (keys: List[String], rate: Double = 1D): Counter[T] = new Counter[T] {
      def sample(rate: Double): Counter[T] =
        newCounter[T](keys, rate)
      def incr(value: T)(implicit ec: ExecutionContext): Future[Unit] =
        send(apply(value))
      def apply(value: T): Stat =
        Lines("c", keys, value, rate)
    }
  
  private[this] def newSampled[T:Serialize]
    (unit: String, keys: List[String], rate: Double = 1D): Sampled[T] = new Sampled[T] {
      def sample(rate: Double): Sampled[T] =
        newSampled[T](unit, keys, rate)
      def add(value: T)(implicit ec: ExecutionContext) =
        send(apply(value))
      def apply(value: T): Stat =
        Lines(unit, keys, value, rate)
    }

  def counter(keys: String*): Counter[Int] =
    newCounter[Int](keys.toList)

  def set[T:Numeric](keys: String*): Sampled[T] =
    newSampled[T]("s", keys.toList)

  def gauge[T:Numeric](keys: String*): Sampled[T] =
    newSampled[T]("g", keys.toList)

  def time(key: String, tailKeys: String*): Sampled[FiniteDuration] =
    newSampled[FiniteDuration]("ms", key :: tailKeys.toList)

  def multi(stats: Stat*)(implicit ec: ExecutionContext) =
    send(stats:_*)
    
  private[this] def send(stats: Stat*)(implicit ec: ExecutionContext): Future[Unit] =
    stats.filter(_.sampled) match {
      case Nil => Future.successful(())
      case xs  => Future {
        def flush() = {
          val size = buffer.position()
          if (size > 0) {
            buffer.flip()
            val sent = channel.send(buffer, address)
            buffer.limit(buffer.capacity())
            buffer.rewind()
            if (size != sent) { /*failed*/ }
          }
        }
        val bytes = xs.map(_.lines.mkString("\n")).mkString("\n").getBytes("utf-8")
        // capacity check
        if (buffer.remaining() < bytes.size) flush()
        buffer.put(bytes)
        flush()
      }
    }
}
