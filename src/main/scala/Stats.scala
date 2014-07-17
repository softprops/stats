package stats

import java.net.{ InetAddress, InetSocketAddress }
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.Random //import java.util.concurrent.ThreadLocalRandom ( added in java 7 )
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

/** Sampled instances may have values added at sampled rates */
abstract class Sampled[T:Countable] {
  def sample(rate: Double): Sampled[T]
  def add(value: T): Future[Boolean]
  def apply(value: T): Stat
}

/** A counter is also sampled, but supports incr/decr operations */
abstract class Counter[T:Numeric] {
  private[this] val num = implicitly[Numeric[T]]
  def sample(rate: Double): Counter[T]
  def apply(value: T): Stat
  def incr: Future[Boolean] =
    incr(num.default)
  def incr(value: T): Future[Boolean]
  def decr: Future[Boolean] =
    decr(num.default)
  def decr(value: T): Future[Boolean] =
    incr(num.negate(value))
}

trait Stat {
  def lines: Iterable[String]
  def sampled: Boolean
}

object Stats {
  val Success = Future.successful(true)
}

/**
 * An statsd client that supports asynchronous sending of singular and multi stats.
 * Connections to remote host is lazily evaluated until an actual request to send.
 * Supports numeric counters, finite duration gauges, and arbitrarily valued gauges and sets.
 * All metric values should be serializable as formatted strings. This is enforced via
 * implicit instances of Countable for a given type T in scope.
 */
case class Stats(
  address: InetSocketAddress = new InetSocketAddress(InetAddress.getByName("localhost"), 8125))
 (implicit ec: ExecutionContext) {
  import stats.Countable._

  private[this] lazy val rand    = new Random()
  private[this] lazy val channel = DatagramChannel.open()

  def close() = channel.close()

  def addr(host: String, port: Int = 8125) = copy(address = new InetSocketAddress(InetAddress.getByName(host), address.getPort))

  def nextDouble = rand.nextDouble // ThreadLocalRandom.current().nextDouble ( java 7 )

  /** A stat captures a metric unit, value, sampleRate and one or more keys to associate with it */
  case class Lines[@specialized(Int, Double, Float) T: Countable](
    unit: String, keys: Iterable[String], value: T, sampleRate: Double) extends Stat {
    private[this] val count = implicitly[Countable[T]]

    def sampled =
      (sampleRate >= 1
       || nextDouble <= sampleRate)

    lazy val lines =
      keys.map(key => s"$key:${count(value)}|$unit${if (sampleRate < 1) "@"+sampleRate else ""}")
  }

  private[this] def newCounter[T:Numeric]
    (keys: Iterable[String], rate: Double = 1D): Counter[T] =
      new Counter[T] {
        def sample(rate: Double): Counter[T] =
          newCounter[T](keys, rate)
        def incr(value: T): Future[Boolean] =
          send(apply(value))
        def apply(value: T): Stat =
          Lines("c", keys, value, rate)
      }
  
  private[this] def newSampled[T:Countable]
    (unit: String, keys: Iterable[String], rate: Double = 1D): Sampled[T] =
      new Sampled[T] {
        def sample(rate: Double): Sampled[T] =
          newSampled[T](unit, keys, rate)
        def add(value: T) =
          send(apply(value))
        def apply(value: T): Stat =
          Lines(unit, keys, value, rate)
      }

  def counter(keys: String*): Counter[Int] =
    newCounter[Int](keys)

  def set[T:Numeric](keys: String*): Sampled[T] =
    newSampled[T]("s", keys)

  def gauge[T:Numeric](keys: String*): Sampled[T] =
    newSampled[T]("g", keys)

  def time(keys: String*): Sampled[FiniteDuration] =
    newSampled[FiniteDuration]("ms", keys)

  def multi(stats: Stat*) =
    send(stats:_*)
    
  private[this] def send(stats: Stat*): Future[Boolean] =
    stats.filter(_.sampled) match {
      case Nil =>
        Stats.Success
      case xs  => Future {
        val str = xs.map(_.lines.mkString("\n")).mkString("\n")
        val bytes = str.getBytes()
        val sent = channel.send(ByteBuffer.wrap(bytes), address)
        bytes.size == sent
      }
    }
}
