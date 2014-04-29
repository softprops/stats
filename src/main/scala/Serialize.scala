package stats

import scala.concurrent.duration.FiniteDuration

trait Serialize[T] {
  def apply(value: T): String
}

trait Numeric[T] extends Serialize[T] {
  def default: T
  def negate(value: T): T
}

object Serialize {
  implicit object FiniteDurationSerializes extends Serialize[FiniteDuration] {
    def apply(value: FiniteDuration) = value.toMillis.toString
  }
  implicit object IntSerializes extends Numeric[Int] {
    def default = 1
    def negate(value: Int) = -value
    def apply(value: Int) = value.toString
  }
  implicit object DoubleSerializes extends Numeric[Double] {
    def default = 1D
    def negate(value: Double) = -value
    def apply(value: Double) = value.toString
  }
  implicit object FloatSerializes extends Numeric[Float] {
    def default = 1F
    def negate(value: Float) = -value
    def apply(value: Float) = value.toString
  }
}
