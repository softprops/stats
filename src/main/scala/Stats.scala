package stats

import java.net.{ InetAddress, InetSocketAddress }
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.charset.Charset
import scala.annotation.varargs
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

/** Sampled instances contain values to be recorded at sampled rates */
abstract class Sampled[T:Countable] {
  def add(value: T): Future[Boolean]
  def apply(value: T): Stat
  def sample(rate: Double): Sampled[T]
  @varargs
  def scope(sx: String*): Sampled[T]
}

/** A counter is also sampled and also supports incr/decr operations */
abstract class Counter[T:Numeric] {
  private[this] val num = implicitly[Numeric[T]]
  def sample(rate: Double): Counter[T]
  @varargs
  def scope(sx: String*): Counter[T]
  def apply(value: T): Stat
  def incr: Future[Boolean] =
    incr(num.defaultValue)
  def incr(value: T): Future[Boolean]
  def decr: Future[Boolean] =
    decr(num.defaultValue)
  def decr(value: T): Future[Boolean] =
    incr(num.negate(value))
}

/** A scalar value which may be recorded if not sampled */
trait Stat {
  def str: String
  def sampled: Boolean
}

object Stats {
  val Success = Future.successful(true)
  val charset = Charset.forName("US-ASCII")
  // convenience for java bootstrapping
  def client = Stats()(ExecutionContext.global)
}

/**
 * A statsd client that supports the asynchronous sending of both singular and multi-stats.
 * All metric values should be serializable as formatted strings. This is enforced via
 * implicit instances of Countable for a given type T in scope.
 */
case class Stats(
  address: InetSocketAddress         = new InetSocketAddress(InetAddress.getByName("localhost"), 8125),
  format: Iterable[String] => String = Names.format,
  scopes: Iterable[String]           = Nil)
 (implicit ec: ExecutionContext) {

  private[this] lazy val channel = DatagramChannel.open()

  def close() = channel.close()

  def addr(host: String, port: Int = 8125) = copy(
    address = new InetSocketAddress(InetAddress.getByName(host), address.getPort)
  )

  @varargs
  def scope(sx: String*) = copy(scopes = scopes ++ sx)

  def formatNames(fmt: Iterable[String] => String) = copy(format = fmt)

  /** A stat captures a metric unit, value, sampleRate and one or more keys to associate with it */
  case class Line[@specialized(Int, Double, Float) T: Countable](
    unit: String, name: Iterable[String], value: T, sampleRate: Double) extends Stat {
    private[this] val count = implicitly[Countable[T]]

    def sampled =
      (sampleRate >= 1
       || Random.nextDouble <= sampleRate)

    lazy val str =
      s"${format(name)}:${count(value)}|$unit${if (sampleRate < 1) "@"+sampleRate else ""}"
  }

  private[this] def newCounter[T:Numeric]
    (name: Iterable[String], rate: Double = 1D): Counter[T] =
      new Counter[T] {
        def apply(value: T): Stat =
          Line("c", name, value, rate)
        def incr(value: T): Future[Boolean] =
          send(apply(value))
        def sample(rate: Double): Counter[T] =
          newCounter[T](name, rate)
        def scope(sx: String*): Counter[T] =
          newCounter[T](name ++ sx, rate)
      }
  
  private[this] def newSampled[T:Countable]
    (unit: String, name: Iterable[String], rate: Double = 1D): Sampled[T] =
      new Sampled[T] {
        def add(value: T) =
          send(apply(value))
        def apply(value: T): Stat =
          Line(unit, name, value, rate)
        def sample(rate: Double): Sampled[T] =
          newSampled[T](unit, name, rate)
        def scope(sx: String*): Sampled[T] =
          newSampled[T](unit, name ++ sx, rate)
      }

  @varargs
  def counter(name: String*): Counter[Int] =
    newCounter[Int](scopes ++ name)

  def set[T:Numeric](name: String*): Sampled[T] =
    newSampled[T]("s", scopes ++ name)

  def gauge[T:Numeric](name: String*): Sampled[T] =
    newSampled[T]("g", scopes ++ name)

  @varargs
  def time(name: String*): Sampled[FiniteDuration] =
    newSampled[FiniteDuration]("ms", scopes ++ name)

  /** Exports multiple status in one request. Consideration of network mtu sizes
   *  should be taken into account when exporting many stats */
  @varargs
  def multi(stats: Stat*) =
    send(stats:_*)
    
  private[this] def send(stats: Stat*): Future[Boolean] =
    stats.filter(_.sampled) match {
      case Nil =>
        Stats.Success
      case xs  => Future {
        val str = xs.map(_.str).mkString("\n")
        val bytes = str.getBytes(Stats.charset)
        val sent = channel.send(ByteBuffer.wrap(bytes), address)
        bytes.size == sent
      }
    }
}
