package mypkg

import mypkg.Result.{ensuring, partialOr, Result}
import mypkg.Schema.Field
import mypkg.Xml.{Document, Element}

case class Schema(fields: List[Field]) {
  def getWritableFieldsWithId: List[Field] =
    fields.filter(f => f.name == "id" || !f.readOnly)
}

object Schema {
  import Utils.implicits._
  case class Field(name: String, readOnly: Boolean, format: String)
  object Field {
    val id: Field = Field("id", readOnly = true, format = "isUnsignedId")

  }
  def from(doc: Document): Result[Schema] =
    for {
      root <- ensuring[Element](doc.root)(
        _.name == "prestashop",
        "expected 'prestashop' root element"
      )
      items <-
        partialOr[List[Element], Element](root.children.toList, "expected single child") { case single :: Nil =>
          single
        }
      fields = items.children.toList
        .flatMap { item =>
          val readOnly = item.getAttribute("read_only").contains("true")
          for {
            format <- item.getAttribute("format")
          } yield Field(item.name, readOnly = readOnly, format = format)
        }
    } yield Schema(List(Field.id).appendedAll(fields))

  def from(resource: Resource, doc: Document): Result[Schema] =
    for {
      root <- ensuring[Element](doc.root)(
        _.name == "prestashop",
        "expected 'prestashop' root element"
      )
      items <-
        root
          .getChild(resource.itemName)
          .toRight(Result.error(s"expected element '${resource.itemName}' not found"))
      fields = items.children.toList
        .flatMap { item =>
          val readOnly = item.getAttribute("read_only").contains("true")
          for {
            format <- item.getAttribute("format")
          } yield Field(item.name, readOnly = readOnly, format = format)
        }
    } yield Schema(List(Field.id).appendedAll(fields))
}
