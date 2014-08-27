package stats

// validMetricChars - https://github.com/graphite-project/graphite-web/blob/master/webapp/graphite/render/grammar.py#L78
// printables - https://github.com/greghaskins/pyparsing/blob/master/src/pyparsing.py#L166

object Names {
  type Format = Iterable[String] => String
  val Separator = "."
  val NonPrintables = "[^!-~]" // white space is omitted
  val Symbols = """(){},=.'"\\"""
  val SymbolGroup = s"""([$Symbols])"""
  /** strips out anything that's that's not in the ascii printable range
   *  escapes symbols */
  def format(name: Iterable[String]) =
    name.map(_.replaceAll(NonPrintables, "_")
              .replaceAll(SymbolGroup, "_"))
        .mkString(Separator)
}
