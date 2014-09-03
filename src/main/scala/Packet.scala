package stats

object Packet {
  private[this] val Sep = "\n"
  private[this] val SepLen = Sep.length
  /** udp packets have headers which take up 16 bytes
   *  http://en.wikipedia.org/wiki/User_Datagram_Protocol#Packet_structure */
  private[this] val HeaderLen = 16

  def bytes(lines: Iterable[String]) =
    lines.mkString(Sep).getBytes(Stats.charset)

  /** much like IterableLike#grouped but with statsd network packet sematics. */
  def grouped
   (max: Short)
   (metrics: Iterable[String]): Iterable[List[String]] = {
     val (packets, _) =
       ((List.empty[List[String]], HeaderLen) /: metrics) {
         case ((lines, total), str) =>
           val len = str.length
           lines match {
             case Nil =>
               if (len > max) (Nil, HeaderLen) // we can't actually send this line
               else ((str :: Nil) :: Nil, len + SepLen)
             case head :: tail =>
               val nextTotal = len + total
               if (nextTotal > max) (
                 (str :: Nil) :: head :: tail, len + SepLen
               )
               else ((str :: head) :: tail, nextTotal + SepLen)
           }
       }
     packets.reverse.map(_.reverse)
   }
}
