package stats

object Names {
  type Format = Iterable[String] => String
  val DefaultDisallows = """[^a-zA-Z0-9\-_\.]"""
  val Separator = "."
  /** strips out anything that's that's not in the ascii printable range
   *  escapes symbols */
  def format(name: Iterable[String]) =
    name.map(_.replaceAll(DefaultDisallows, "_"))
        .mkString(Separator)
}
