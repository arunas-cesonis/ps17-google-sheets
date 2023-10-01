package mypkg

import cats.implicits.toTraverseOps
import mypkg.Result.{Error, Result, error, fail, ok}
import mypkg.Table.Column
import mypkg.Utils.{anyToInt, anyToString, listToArray, time}
import mypkg.Xml.{Cdata, Document, Element}

import scala.scalajs.js

case class Table(columns: js.Array[Column]) {
  self =>
  def debug: String =
    columns
      .map { col =>
        List(col.resource.name, col.name, col.data.take(10).map(_.toString).mkString("[", ", ", "]")).mkString(" ")
      }
      .mkString("\n")

  def toValues: js.Array[js.Array[js.Any]] = {
    val header = columns.map(_.toQualifiedName).map(js.Any.fromString)
    val values = js.Array(header)
    val n = self.numRows
    for (i <- 0 until n)
      values.push(columns.map(_.data(i)))
    values
  }

  def numRows: Int = columns.head.data.length

  def toStringLists: List[List[String]] =
    toValues.map(_.toList.map(_.toString)).toList

  def toSingleResource: Either[Error, Resource] = {
    val resource = columns.map(_.resource).distinct.toList match {
      case single :: Nil => Right(single)
      case other => fail(s"expected single resource, got: ${other.map(_.name).mkString(", ")}")
    }
    resource
  }

  def toDocument: Either[Error, Xml.Document] = {
    val resource = toSingleResource

    def toXml(resource: Resource, itemName: String): Xml.Document = {
      val root = Element.create(resource.name)
      val doc = Document(root)
      val n = numRows
      for (i <- 0 until n) {
        val item = Element(itemName, js.Array(), js.Array())
        columns.foreach { col =>
          val field = Element.create(col.name)
          val data = Cdata(col.data(i).toString)
          field.content.push(data)
          item.content.push(field)
        }
        root.content.push(item)
      }
      doc
    }

    for {
      r <- resource
      _ <- columns.find(_.name == "id").toRight(error("no id column found"))
    } yield toXml(r, r.itemName)
    // val doc       = XmlService.createDocument()
    // val root      = XmlService.createElement("prestashop")
    // val resources = XmlService.createElement(resource.name)
    //// XmlService.createElement()
    //// doc.crea
    // Right(doc)
  }

  def sameColumns(b: Table): Boolean =
    self.columns.map(x => (x.name, x.resource)).toSet == b.columns.map(x => (x.name, x.resource)).toSet
}

object Table {

  def fromDisplayValues(values: js.Array[js.Array[String]]): Result[Table] =
    from(values.map(_.map(js.Any.fromString)))

  def from(values: js.Array[js.Array[js.Any]]): Result[Table] =
    for {
      columns <- values.head.toList.traverse { s =>
        anyToString(s).flatMap(Column.fromQualifiedName)
      }
      idColumnIndex <- columns.indexWhere(_.name == "id") match {
        case -1 => fail("no id column found")
        case i => ok(i)
      }
      n = columns.length
      _ = values.tail.toIterable.takeWhile(row => anyToInt(row(idColumnIndex)).isRight).foreach { row =>
        for (i <- 0 until n)
          columns(i).data.push(row(i))
      }
    } yield Table(listToArray(columns))

  def from(resource: Resource, xml: Document): Result[Table] = {
    val records =
      Utils.time("getChildren")(
        xml.root
          .getChild(resource.name)
          .toRight(error("document does not match resource"))
          .map(_.getChildren(resource.itemName))
      )
    for {
      r <- records
      columns <- time("makeColumns")(makeColumns(resource, r).toRight(Result.error("empty response")))
      _ = fillColumns(columns, r)
    } yield Table(columns)
  }

  private def fieldNames(a: js.Array[Element]): Option[js.Array[String]] =
    a.headOption.map(s => js.Array(s.children.map(_.name).toList: _*))

  def makeColumns(resource: Resource, records: js.Array[Element]): Option[js.Array[Column]] =
    fieldNames(records).map { names =>
      names.map(Column(_, resource, js.Array()))
    }

  def fillColumns(columns: js.Array[Column], records: js.Array[Element]): Unit =
    time("fill columns") {
      records.zipWithIndex.foreach { case (el, i) =>
        val rec = el.children.map(c => c.name -> c).toMap
        columns
          .foreach { col =>
            val valueElement = rec(col.name)
            val language = valueElement.children.filter(_.name == "language")
            val value = language.headOption match {
              case Some(value) => value.text
              case None => valueElement.text
            }
            col.data.push(value)
          }
      }
    }

  case class Column(name: String, resource: Resource, data: js.Array[js.Any]) {
    self =>
    def toQualifiedName: String = s"${resource.name}.${name}"
  }

  object Column {
    def fromQualifiedName(qname: String): Result[Column] =
      qname.split('.') match {
        case Array(resource, name) =>
          Resource.byName
            .get(resource)
            .map(r => Column(name, r, js.Array()))
            .toRight(error(s"resource ${name} not found"))
        case _ =>
          fail(s"expected format '$$RESOURCE.$$FIELD', input '${qname}' does not match")
      }
  }
}
