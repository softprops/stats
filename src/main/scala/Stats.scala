package stats

import java.net.{ InetAddress, InetSocketAddress }
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.charset.Charset
import scala.annotation.varargs
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import java.lang.{ Integer => JInt }

/** Sampled instances contain values to be recorded at sampled rates */
abstract class Sampled[T:Value, Self <: Sampled[T, Self]] {
  def add(value: T): Future[Boolean]
  def apply(value: T): Stat
  def sample(rate: Double): Self
  def scopes(sx: Seq[String]): Self
  @varargs
  def scope(sx: String*): Self =
    scopes(sx)
}

abstract class DefaultSampled[T:Value] extends Sampled[T, DefaultSampled[T]]

/** A counter is a sampled instance that supports incr/decr operations */
abstract class Counter[T:RichValue] extends Sampled[T, Counter[T]] {
  private[this] val values = Value.rich[T]
  def incr: Future[Boolean] =
    incr(values.defaultValue)
  def incr(value: T): Future[Boolean] =
    add(value)
  def decr: Future[Boolean] =
    decr(values.defaultValue)
  def decr(value: T): Future[Boolean] =
    incr(values.negate(value))
}

abstract class Gauge[T:Value] extends Sampled[T, Gauge[T]] {
  type Self[T] = Gauge[T]
  def delta(value: T, subtract: Boolean): Stat
  def deltaAdd(value: T): Future[Boolean]
  def deltaSubtract(value: T): Future[Boolean]
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
 * A statsd client that supports the asynchronous sending of
 * both singular and multi-stat requests.
 * All metric values should be serializable as formatted strings.
 * This is enforced via implicit instances of Values for a given type T in scope.
 */
case class Stats(
  address: InetSocketAddress        = new InetSocketAddress(InetAddress.getByName("localhost"), 8125),
  format: Names.Format              = Names.format,
  scopes: Iterable[String]          = Nil,
  packetMax: Short                  = 1500,
  log: Option[Try[Boolean] => Unit] = None)
 (implicit ec: ExecutionContext) {

  private[this] lazy val channel = DatagramChannel.open()

  /** Closes any open connection. Once closed this instance's behavior is undefined */
  def close() = channel.close()

  def addr(host: String, port: Int = 8125) = copy(
    address = new InetSocketAddress(InetAddress.getByName(host), address.getPort)
  )

  def packetMax(max: Short): Stats = copy(packetMax = max)

  def log(l: Try[Boolean] => Unit) = copy(log = Some(l))

  @varargs
  def scope(sx: String*) = copy(scopes = scopes ++ sx)

  def formatNames(fmt: Names.Format) = copy(format = fmt)

  /** Captures a metric unit, value, sampleRate and one or more keys to associate with it */
  case class Metric[@specialized(Int, Double, Float) T: Value](
    unit: String, name: Iterable[String], value: T, sampleRate: Double) extends Stat {
    private[this] val values = Value[T]

    def sampled =
      (sampleRate >= 1
       || Random.nextDouble <= sampleRate)

    lazy val str =
      s"${format(name)}:${values(value)}|$unit${if (sampleRate < 1) "@"+sampleRate else ""}"
  }

  private[this] def newCounter[T:RichValue]
    (name: Iterable[String], rate: Double = 1D): Counter[T] =
      new Counter[T] {
        def add(value: T): Future[Boolean] =
          send(apply(value))
        def apply(value: T): Stat =
          Metric("c", name, value, rate)
        def sample(rate: Double): Counter[T] =
          newCounter(name, rate)
        def scopes(sx: Seq[String]): Counter[T] =
          newCounter(name ++ sx, rate)
      }
  
  private[this] def newSampled[T:Value]
    (unit: String, name: Iterable[String], rate: Double = 1D): DefaultSampled[T] =
      new DefaultSampled[T] {
        def add(value: T): Future[Boolean] =
          send(apply(value))
        def apply(value: T): Stat =
          Metric(unit, name, value, rate)
        def sample(rate: Double) =
          newSampled[T](unit, name, rate)
        def scopes(sx: Seq[String]) =
          newSampled[T](unit, name ++ sx, rate)
      }

  private[this] def newGauge[T:RichValue]
   (name: Iterable[String], rate: Double = 1D): Gauge[T] =
     new Gauge[T] {
       private[this] val values = Value.rich[T]
       def add(value: T): Future[Boolean] =
         send(apply(value))
       def apply(value: T): Stat =
         Metric("g", name, value, rate)
       def sample(rate: Double): Gauge[T] =
         newGauge(name, rate)
       def scopes(sx: Seq[String]): Gauge[T] =
         newGauge(name ++ sx, rate)
       def deltaAdd(value: T): Future[Boolean] =
         send(delta(value, false))
       def deltaSubtract(value: T): Future[Boolean] =
         send(delta(value, true))
       def delta(value: T, subtract: Boolean): Stat =
         Metric(
           "g", name,
           if (subtract) "-${values(values.negate(value))}" else "+${values(value)}",
           rate)
     }

  /** https://github.com/etsy/statsd/blob/master/docs/metric_types.md#counting */
  @varargs
  def counter(name: String*): Counter[Int] =
    newCounter[Int](scopes ++ name)

  /** like counter but for java integers to preserve parametric types */
  @varargs
  def jcounter(name: String*): Counter[JInt] =
    newCounter[JInt](scopes ++ name)

  /** https://github.com/etsy/statsd/blob/master/docs/metric_types.md#sets */
  @varargs
  def set[T:Value](name: String*) =
    newSampled[T]("s", scopes ++ name)

  /** https://github.com/etsy/statsd/blob/master/docs/metric_types.md#gauges */
  @varargs
  def gauge[T:RichValue](name: String*) =
    newGauge[T](scopes ++ name)

  /** https://github.com/etsy/statsd/blob/master/docs/metric_types.md#timing */
  @varargs
  def time(name: String*) =
    newSampled[FiniteDuration]("ms", scopes ++ name)

  /** Exports multiple status in one request. Consideration of network mtu sizes
   *  should be taken into account when exporting many stats.
   */
  @varargs
  def multi(stats: Stat*) =
    send(stats:_*)

  private[this] def send(stats: Stat*): Future[Boolean] =
    stats.filter(_.sampled) match {
      case Nil =>
        Stats.Success
      case xs  =>
        val delivery = Future {
          val packets = Packet.grouped(packetMax)(xs.map(_.str))
          val (sent, expected) = ((0,0) /: packets) {
            case ((s,e), packet) =>
              val bytes = Packet.bytes(packet)
              val sent = channel.send(ByteBuffer.wrap(bytes), address)
              (s + sent, e + bytes.length)
          }
          sent == expected
        }
        log.foreach(delivery.onComplete)
        delivery
    }
}
