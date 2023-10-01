package mypkg

import mypkg.Params.ToParams
import mypkg.Schema.Field

case class Params(
  map: Map[String, String],
) {
  def add[P <: ToParams](p: P): Params = Params(map ++ p.toParams)
}

object Params {
  sealed trait ToParams {
    def toParams: Map[String, String]
  }

  def empty: Params = Params(Map.empty)

  sealed trait Schema extends Product with Serializable with ToParams {
    self =>
    def toParams: Map[String, String] =
      self match {
        case Schema.Synopsis => Map("schema" -> "synopsis")
        case Schema.Blank    => Map("schema" -> "blank")
      }
  }

  object Schema {
    case object Synopsis extends Schema

    case object Blank extends Schema
  }

  sealed trait Display extends Product with Serializable with ToParams {
    self =>
    def toParams: Map[String, String] =
      self match {
        case Display.Full          => Map("display" -> "full")
        case Display.Fields(names) => Map("display" -> names.mkString("[", ",", "]"))
      }
  }

  object Display {
    case object Full extends Display

    case class Fields(names: List[String]) extends Display

    def from(fields: List[Field]): Display = Fields(fields.map(_.name))
  }

  case class WsKey(key: String) extends ToParams {
    def toParams: Map[String, String] =
      Map("ws_key" -> key)
  }

  case class OrFilter(field: String, values: List[String]) extends ToParams {
    def toParams: Map[String, String] =
      Map(s"filter[${field}]" -> values.mkString("[", "|", "]"))
  }

  sealed trait DateFilter extends Product with Serializable with ToParams {
    self =>
    def toParams: Map[String, String] =
      self match {
        case DateFilter.AllTime => Map.empty
        case DateFilter.Range(start, end) =>
          Map("filter[date_upd]" -> s"[${start},${end}]", "date" -> "1")
      }
  }

  object DateFilter {
    case object AllTime extends DateFilter

    case class Range(start: String, end: String) extends DateFilter
  }

}
