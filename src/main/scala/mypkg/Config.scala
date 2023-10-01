package mypkg

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
@js.native
trait Config extends js.Object {
  def host: String = js.native

  def key: String = js.native
}

@js.native
@JSGlobal
object Config extends Config
