package stats

import java.net.{ InetAddress, InetSocketAddress }

import java.nio.charset.Charset
import scala.annotation.varargs
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import java.lang.{ Integer => JInt }

/** Sampled instances contain values to be recorded at sampled rates */
abstract class Sampled[T:Value, Self <: Sampled[T, Self]] {
  def record(value: T): Future[Boolean]
  def apply(value: T): Stat
  def sample(rate: Double): Self
  /** here to avoid static forwarding issue with implementations of `scope` */
  def scopes(sx: Seq[String]): Self
  @varargs
  def scope(sx: String*): Self =
    scopes(sx)
}

/** A simplied type signature for concrete extension for
 *  clients to avoid `typing` out the f-bounded polymophic
 *  signature above */
abstract class Sampling[T:Value] extends Sampled[T, Sampling[T]]

/** A counter is a sampled instance that supports incr/decr operations */
abstract class Counter[T:RichValue] extends Sampled[T, Counter[T]] {
  private[this] val values = Value.rich[T]
  def incr: Future[Boolean] =
    incr(values.defaultValue)
  def incr(value: T): Future[Boolean] =
    record(value)
  def decr: Future[Boolean] =
    decr(values.defaultValue)
  def decr(value: T): Future[Boolean] =
    incr(values.negate(value))
}

abstract class Gauge[T:Value] extends Sampled[T, Gauge[T]] {
  def add(value: T): Future[Boolean]
  def subtract(value: T): Future[Boolean]
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
  log: Option[Try[(Seq[Stat], Boolean)] => Unit] = None,
  transporter: Transport.Factory    = Transport.datagram)
 (implicit ec: ExecutionContext) {

  private[this] lazy val transport = transporter(address, packetMax, ec)

  /** Closes any open connection. Once closed this instance's behavior is undefined */
  def close() = transport.close()

  def addr(host: String, port: Int = 8125) = copy(
    address = new InetSocketAddress(InetAddress.getByName(host), address.getPort)
  )

  def packetMax(max: Short): Stats = copy(packetMax = max)

  def log(l: Try[(Seq[Stat], Boolean)] => Unit) = copy(log = Some(l))

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

      lazy val str = {
        val samplingSuffix = if (sampleRate < 1) "@" + sampleRate else ""
        s"${format(name)}:${values(value)}|$unit$samplingSuffix"
      }
  }

  private[this] trait Recorder[T] { self: Sampled[T, _] =>
    def record(value: T) = send(apply(value))
  }

  private[this] def newCounter[T:RichValue]
    (name: Iterable[String], rate: Double = 1D): Counter[T] =
      new Counter[T] with Recorder[T] {
        def apply(value: T): Stat =
          Metric("c", name, value, rate)
        def sample(rate: Double): Counter[T] =
          newCounter(name, rate)
        def scopes(sx: Seq[String]): Counter[T] =
          newCounter(name ++ sx, rate)
      }
  
  private[this] def newSampled[T:Value]
    (unit: String, name: Iterable[String], rate: Double = 1D): Sampling[T] =
      new Sampling[T] with Recorder[T] {
        def apply(value: T): Stat =
          Metric(unit, name, value, rate)
        def sample(rate: Double) =
          newSampled[T](unit, name, rate)
        def scopes(sx: Seq[String]) =
          newSampled[T](unit, name ++ sx, rate)
      }

  private[this] def newGauge[T:RichValue]
   (name: Iterable[String], rate: Double = 1D): Gauge[T] =
     new Gauge[T] with Recorder[T] {
       private[this] val values = Value.rich[T]
       def apply(value: T): Stat =
         Metric("g", name, value, rate)
       def sample(rate: Double): Gauge[T] =
         newGauge(name, rate)
       def scopes(sx: Seq[String]): Gauge[T] =
         newGauge(name ++ sx, rate)
       def add(value: T): Future[Boolean] =
         send(delta(value, false))
       def subtract(value: T): Future[Boolean] =
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
  //@varargs (scala 2.10.4 prevents this)
  def set[T:Value](name: String*) =
    newSampled[T]("s", scopes ++ name)

  /** https://github.com/etsy/statsd/blob/master/docs/metric_types.md#gauges */
  //@varargs (scala 2.10.4 prevents this)
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
        val delivery = transport.send(xs)
        log.foreach(delivery.onComplete)
        delivery.map { case (_, s) => s }
    }
}
