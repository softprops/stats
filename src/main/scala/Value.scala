package stats

import scala.concurrent.duration.FiniteDuration
import java.text.NumberFormat
import java.util.Locale
import java.lang.{ Integer => JInt }

/** A marker type class for recordable values */
trait Value[T] {
  def apply(value: T): String
}

/** RichValue type is a Value with the ability to negate a typed value
 *  and provides a defaultValue */
trait RichValue[T] extends Value[T] {
  def defaultValue: T
  def negate(value: T): T
}

object Value {

  def format(f: NumberFormat => String) = {
    val nf = NumberFormat.getInstance(Locale.US)
    nf.setGroupingUsed(false)
    nf.setMaximumFractionDigits(19)
    f(nf)
  }

  def apply[T:Value] = implicitly[Value[T]]

  def rich[T:RichValue] = implicitly[RichValue[T]]

  // for gauge'd deltas
  implicit val strings: Value[String] =
    new Value[String] {
      def apply(value: String) = value
    }

  implicit val durations: Value[FiniteDuration] =
    new Value[FiniteDuration] {
      def apply(value: FiniteDuration) =
        format(_.format(value.toMillis))
    }

  implicit val longs: RichValue[Long] =
    new RichValue[Long] {
      def defaultValue = 1
      def negate(value: Long) = -value
      def apply(value: Long) = format(_.format(value))
    }

  implicit val ints: RichValue[Int] =
    new RichValue[Int] {
      def defaultValue = 1
      def negate(value: Int) = -value
      def apply(value: Int) = format(_.format(value))
    }

  implicit val integers: RichValue[JInt] =
    new RichValue[JInt] {
      def defaultValue = 1
      def negate(value: JInt) = -value
      def apply(value: JInt) = format(_.format(value))
    }

  implicit val doubles: RichValue[Double] =
    new RichValue[Double] {
      def defaultValue = 1D
      def negate(value: Double) = -value
      def apply(value: Double) = format(_.format(value))
    }

  implicit val floats: RichValue[Float] =
    new RichValue[Float] {
      def defaultValue = 1F
      def negate(value: Float) = -value
      def apply(value: Float) = format(_.format(value))
    }
}
