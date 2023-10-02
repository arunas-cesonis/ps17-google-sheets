package mypkg

import cats.Eq

import scala.scalajs.js

// FIXME: research whether js.Dynamic should be used instead of js.Any here and other similar cases
// https://github.com/scala-js/scala-js/issues/4026
case class Values(rows: js.Array[js.Array[js.Any]]) extends AnyVal {
}
object Values {
  def create: Values = Values(js.Array())

}
