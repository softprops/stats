package stats

import java.util.{ Random => JRandom } //import java.util.concurrent.ThreadLocalRandom ( added in java 7 )

trait RandomDouble {
  def next: Double
}

