package stats

import java.net._
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.Random
//import java.util.concurrent.ThreadLocalRandom ( added in java 7 )
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

abstract class Counter[T:Numeric] {
  val num = implicitly[Numeric[T]]
  def sample(rate: Double): Counter[T]
  def incr(implicit ec: ExecutionContext): Future[Unit] =
    incr(num.default)(ec)
  def incr(value: T)(implicit ec: ExecutionContext): Future[Unit]
  def decr(implicit ec: ExecutionContext): Future[Unit] =
    incr(num.negate(num.default))(ec)
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
  case class Stat[T: Serialize](
    unit: String,
    keys: List[String],
    sampleRate: Double = 1D,
    value: Option[T] = None)  {
    private[this] val ser = implicitly[Serialize[T]]

    lazy val sampled =
      (sampleRate >= 1
       || nextDouble <= sampleRate)

    lazy val line: Option[String] =
      value.map { value => keys.map(key => s"$key:${ser(value)}|$unit${if (sampleRate < 1) "|@"+sampleRate else ""}").mkString("\n") }

    def sample(rate: Double) = copy(sampleRate = rate)

    def add(value: T)(implicit ev: ExecutionContext): Future[Unit] =
      send(this.copy(value = Some(value)))
  }

  private[this] def newCounter[T:Numeric](stat: Stat[T]): Counter[T] = new Counter[T] {
    def sample(rate: Double) = newCounter(stat.copy(sampleRate = rate))
    def incr(by: T)(implicit ec: ExecutionContext): Future[Unit] = stat.add(by)
  }

  def counter[T:Numeric](key: String, tailKeys: String*) =
    newCounter(Stat[T]("c", key :: tailKeys.toList))

  def set[T:Numeric](key: String, tailKeys: String*)  = Stat[T]("s", key :: tailKeys.toList)

  def gauge[T:Numeric](key: String, tailKeys: String*)  = Stat[T]("g", key :: tailKeys.toList)

  def time(key: String, tailKeys: String*) = Stat[FiniteDuration]("ms", key :: tailKeys.toList)

  def multi(head: Stat[_], tail: Stat[_]*)(implicit ec: ExecutionContext) =
    send((head :: tail.toList):_*)
    
  private[this] def send(stats: Stat[_]*)(implicit ec: ExecutionContext): Future[Unit] =
    stats.filter(s => s.sampled && s.value.isDefined) match {
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
        val bytes = xs.map(_.line).mkString("\n").getBytes("utf-8")
        // capacity check
        if (buffer.remaining() < bytes.size) flush()
        buffer.put(bytes)
        flush()
      }
    }
}
