package stats

import org.scalacheck.Gen
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

object KeyProperties extends Properties("Keys") {
  property("formats") = forAll(Gen.alphaStr) { (str: String) =>
    // todo: impl me
    str == str
  }
}
