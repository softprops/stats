package stats

import java.util.{ Random => JRandom }
import scala.util.control.Exception.allCatch

private[stats] trait Random {
  def nextDouble: Double
}

private[stats] object Random {

   /** A java7 ThreadLocal-based random */
   lazy val threadLocal = new Random {
    private[this] val rand =
      Class.forName("java.util.concurrent.ThreadLocalRandom")
    private[this] val current =
      rand.getDeclaredMethod("current", null)
    private[this] val next =
      rand.getDeclaredMethod("nextDouble", null)
    private[this] val instance =
      current.invoke(null, null)

    def nextDouble = next.invoke(instance, null).asInstanceOf[Double]
  }

  /** A default jdk-based random */
  lazy val fallback = new Random {
    private[this] lazy val doubles = new JRandom()
    def nextDouble = doubles.nextDouble
  }

  private[this] lazy val impl: Random =
    allCatch.opt(threadLocal).getOrElse(fallback)

  def nextDouble = impl.nextDouble
}

