package mypkg

import cats.implicits.toTraverseOps
import mypkg.Result.{ensuring, error, partialOr, Result}
import mypkg.Schema.{Association, Field}
import mypkg.Xml.{Document, Element}

case class Schema(resource: String, itemName: String, fields: List[Field], associations: List[Association]) {
  def debug: String = {
    val a = fields.map(f => resource + ".field." + f.name + ": " + f.format).mkString("\n")
    val b = associations
      .map { a =>
        val prefix = resource + ".association." + a.name + ".field."
        a.fields.map(f => prefix + f.name).mkString("\n")
      }
      .mkString("\n")
    a + b
  }

  def getWritableFields: List[Field] =
    fields.filter(f => !f.readOnly)

  def getWritableFieldsWithId: List[Field] =
    Field.id :: getWritableFields
}

object Schema {

  case class Association(name: String, nodeType: String, fields: List[AssociationField])
  case class AssociationField(name: String)
  case class Field(name: String, readOnly: Boolean, format: String)
  object Field {
    val id: Field = Field("id", readOnly = true, format = "isUnsignedId")
  }

  def parseAssociation(e: Element): Option[Association] = {
    val name = e.name
    for {
      nodeType <- e.getAttribute("nodeType")
      node     <- e.getElement(nodeType)
      fields = node.elements.map(parseAssociationField).toList
    } yield Association(name, nodeType, fields)
  }

  def parseAssociationField(e: Element): AssociationField = {
    val name = e.name
    AssociationField(name)
  }

  def parseFields(e: Iterable[Element]): List[Field] =
    e.toList
      .flatMap { item =>
        val readOnly = item.getAttribute("read_only").contains("true")
        item.getAttribute("format") match {
          case Some(format) =>
            Some(Field(item.name, readOnly = readOnly, format = format))
          case None =>
            None
        }
      }
  def from(resource: String, doc: Document): Result[Schema] =
    for {
      root <- ensuring[Element](doc.root)(
        _.name == "prestashop",
        "expected 'prestashop' root element"
      )
      items <-
        partialOr[List[Element], Element](root.elements.toList, "expected single child") { case single :: Nil =>
          single
        }
      assoctiations <- items.getElement("associations") match {
        case Some(el) => el.elements.toList.traverse(parseAssociation).toRight(error("failed parsing associations"))
        case None     => Result.ok(Nil)
      }
      fields = parseFields(items.elements)
    } yield Schema(resource, items.name, List(Field.id).appendedAll(fields), assoctiations)

}
