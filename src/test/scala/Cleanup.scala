package stats

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

trait Cleanup {
  Cleanup.dirties.put(this, ())
  def cleanup()
}

object Cleanup {
  private val dirties = new ConcurrentHashMap[Cleanup, Unit]

  def cleanup() {
    try {
      dirties.keySet.asScala.foreach { _.cleanup() }
    } catch {
      case e: Exception =>
        println("Error on cleanup")
        e.printStackTrace
    }
  }
}


