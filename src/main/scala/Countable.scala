package stats

import scala.concurrent.duration.FiniteDuration

trait Countable[T] {
  def apply(value: T): String
}

trait Numeric[T] extends Countable[T] {
  def default: T
  def negate(value: T): T
}

object Countable {
  implicit object FiniteDurationCounts extends Countable[FiniteDuration] {
    def apply(value: FiniteDuration) = value.toMillis.toString
  }
  implicit object IntCounts extends Numeric[Int] {
    def default = 1
    def negate(value: Int) = -value
    def apply(value: Int) = value.toString
  }
  implicit object DoubleCounts extends Numeric[Double] {
    def default = 1D
    def negate(value: Double) = -value
    def apply(value: Double) = value.toString
  }
  implicit object FloatCounts extends Numeric[Float] {
    def default = 1F
    def negate(value: Float) = -value
    def apply(value: Float) = value.toString
  }
}
