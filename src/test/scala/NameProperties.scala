package stats

import org.scalacheck.Gen
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

object NameProperties extends Properties("Names") {
  val AsciiPrintables = "[!-~]+"
  val Escapables = Names.Symbols.toArray

  def escaped(str: String) = Escapables.forall { char =>
    !str.contains(char)
  }

  def nonEmptyStr: Gen[String] =
    for(cs <- Gen.nonEmptyListOf(asciiChar)) yield cs.mkString

  def asciiChar: Gen[Char] = Gen.choose(0.toChar, 127.toChar)

  property("formats") = forAll(nonEmptyStr) { (str: String) =>
    val formatted = Names.format(str :: Nil)
    formatted.matches(AsciiPrintables) && escaped(formatted)
  }
}
