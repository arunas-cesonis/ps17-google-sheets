package mypkg

import cats.implicits.toTraverseOps
import mypkg.Result.Result
import mypkg.Schema.Field
import mypkg.Xml.{Cdata, Document, Element}

object Data {
  val languageId = 1
  def xmlToTable(schema: Schema, doc: Document): Result[Vec.Table] = {
    def readValue(f: Field, e: Element): Result[String] =
      if (f.multilingual) {
        e.getElements("language")
          .find(_.getAttribute("id").contains(languageId.toString))
          .toRight(Result.error("language not found"))
          .map(_.text)
      } else {
        Result.ok(e.text)
      }

    val builders = schema.fields.map { f =>
      f -> Vector.newBuilder[String]
    }
    val items = doc.root
      .singleElementByName(schema.resource)
      .map(_.getElements(schema.itemName))
      .getOrElse(doc.root.getElements(schema.itemName))
    for {
      _ <- items.toList.traverse { el =>
        builders.traverse { case (f, builder) =>
          for {
            el    <- el.getElementR(f.name)
            value <- readValue(f, el)
          } yield builder.addOne(value)
        }
      }
    } yield Vec.Table.from(builders.map(pair => pair._1.name -> Vec.Dyn.String(Vec.from(pair._2.result()))))
  }

  def tableToXml(schema: Schema, table: Vec.Table): Result[Document] =
    table.columns
      .traverse { case (name, v) =>
        // only for verification
        schema.fields
          .find(_.name == name)
          .toRight(Result.error(s"field '${name}' not found in schema"))
          .map(f => f -> v)
      }
      .map { columns =>
        val seqs    = columns.map(pair => pair._1 -> pair._2.toIndexedSeq)
        val numRows = seqs.head._2.length
        val items   = Element.create(schema.resource)
        for (row <- (0 until numRows)) {
          val item = Element.create(schema.itemName)
          for ((f, values) <- seqs) {
            val el    = Element.create(f.name)
            val value = Cdata(values(row))
            if (f.multilingual) {
              val lang = Element.create("language")
              lang.content.push(value)
              lang.attributes.push(("id", languageId.toString))
              el.content.push(lang)
            } else {
              el.content.push(value)
            }
            item.content.push(el)
          }
          items.content.push(item)
        }
        Document(items)
      }

}
