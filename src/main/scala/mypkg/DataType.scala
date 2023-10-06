package mypkg

import cats.implicits.toTraverseOps
import mypkg.Result.Result
import mypkg.Xml.{Document, Element}

sealed trait DataType extends Product with Serializable
object DataType {
  case class Field(name: java.lang.String, dataType: DataType, readOnly: Boolean = false, required: Boolean = false)
  case class String()                    extends DataType
  case class Date()                      extends DataType
  case class Array(item: DataType)       extends DataType
  case class Struct(fields: List[Field]) extends DataType

  def wrapResource(name: java.lang.String, dataType: DataType): DataType =
    Struct(List(Field(name, Array(dataType))))

  def fromResourceSynopsis(doc: Document): Result[DataType] = {
    val idField: Field = Field("id", String())

    def ensureIdField(s: List[Field]): Result[List[Field]] =
      if (s.exists(_.name == "id")) {
        Result.fail("expected field 'id' to be missing in schema")
      } else {
        Result.ok(idField :: s)
      }

    def parseBoolAttribute(e: Element, attrName: java.lang.String): Result[Boolean] =
      e.getAttribute(attrName) match {
        case Some("true")  => Result.ok(true)
        case Some("false") => Result.ok(false)
        case None          => Result.ok(false)
        case _             => Result.fail(s"attribute '${attrName}' value '${}' is not true/false")
      }

    def parseField(e: Element): Result[Field] =
      if (e.name == "associations") {
        val dataType = e.elements.toList
          .traverse { x =>
            for {
              nodeType    <- x.getAttribute("nodeType").toRight(Result.error("no attribute 'node_type'"))
              nodeElement <- x.getElement(nodeType).toRight(Result.error(s"no element '${nodeType}'"))
              fields <- nodeElement.elements.toList.traverse { y =>
                Result.ok(Field(y.name, String()))
              }
            } yield Field(
              x.name,
              Struct(List(Field(nodeType, Array(Struct(fields))))),
            )
          }
          .map(fields => Struct(fields))
        dataType.map(d => Field(e.name, d))
      } else {
        val dataType = e.getAttribute("format") match {
          case Some("isDate") => Date()
          case _              => String()
        }
        for {
          readOnly <- parseBoolAttribute(e, "read_only")
          required <- parseBoolAttribute(e, "required")
        } yield Field(e.name, dataType, readOnly = readOnly, required = required)
      }

    for {
      root         <- Result.ensuring[Element](doc.root)(_.name == "prestashop", "no element 'prestashop'")
      el           <- root.singleElement.toRight(Result.error("expected single element"))
      fields       <- el.elements.toList.traverse(parseField)
      fieldsWithId <- ensureIdField(fields)
    } yield Struct(List(Field(el.name, Struct(fieldsWithId))))
  }

}
