package stats

import scala.concurrent.duration.FiniteDuration

/** A marker type class for things that be be counted by statsd */
trait Countable[T] {
  def apply(value: T): String
}

/** Numeric type is Countable with the ability to negate a typed value
 *  and provides an defaultValue */
trait Numeric[T] extends Countable[T] {
  def defaultValue: T
  def negate(value: T): T
}

object Countable {
  implicit val finiteDurations: Countable[FiniteDuration] = new Countable[FiniteDuration] {
    def apply(value: FiniteDuration) = value.toMillis.toString
  }
  implicit val ints: Numeric[Int] = new Numeric[Int] {
    def defaultValue = 1
    def negate(value: Int) = -value
    def apply(value: Int) = value.toString
  }
  implicit val doubles: Numeric[Double] = new Numeric[Double] {
    def defaultValue = 1D
    def negate(value: Double) = -value
    def apply(value: Double) = value.toString
  }
  implicit val floats: Numeric[Float] = new Numeric[Float] {
    def defaultValue = 1F
    def negate(value: Float) = -value
    def apply(value: Float) = value.toString
  }
}
