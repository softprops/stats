package stats

import java.util.{ Random => JRandom }
import scala.util.control.NonFatal

private[stats] trait Random {
  def nextDouble: Double
}

private[stats] object Random {
  private[this] lazy val impl: Random = try new Random {
    private[this] val rand =
      Class.forName("java.util.concurrent.ThreadLocalRandom")
    private[this] val currentMethod =
      rand.getDeclaredMethod("current", null)
    private[this] val nextMethod =
      rand.getDeclaredMethod("nextDouble", null)
    private[this] val current =
      currentMethod.invoke(null, null)

    def nextDouble = nextMethod.invoke(current, null).asInstanceOf[Double]
  } catch {
    case NonFatal(e) =>
      new Random {
        private[this] lazy val doubles = new JRandom()
        def nextDouble = doubles.nextDouble
      }
  }

  def nextDouble = impl.nextDouble
}

