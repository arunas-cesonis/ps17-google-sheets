package mypkg

import cats.implicits.toTraverseOps
import mypkg.Result.Result
import mypkg.Xml.{Cdata, Document, Element}

object Data {
  def xmlToTable(schema: Schema, doc: Document): Result[Vec.Table] = {
    val builders = schema.fields.map { f =>
      f.name -> Vector.newBuilder[String]
    }
    val items = doc.root
      .singleElementByName(schema.resource)
      .map(_.getElements(schema.itemName))
      .getOrElse(doc.root.getElements(schema.itemName))
    for {
      _ <- items.toList.traverse { el =>
        builders.traverse { case (name, builder) =>
          el.getElementR(name).map(e => builder.addOne(e.text))
        }
      }
    } yield Vec.Table.from(builders.map(pair => pair._1 -> Vec.Dyn.String(Vec.from(pair._2.result()))))
  }

  def tableToXml(schema: Schema, table: Vec.Table): Result[Document] =
    table.columns
      .traverse { case (name, v) =>
        // only for verification
        schema.fields
          .find(_.name == name)
          .toRight(Result.error(s"field '${name}' not found in schema"))
          .map(_ => name -> v)
      }
      .map { columns =>
        val seqs = columns.map(pair => pair._1 -> pair._2.toIndexedSeq)
        val numRows = seqs.head._2.length
        val items = Element.create(schema.resource)
        for (row <- (0 until numRows)) {
          val item = Element.create(schema.itemName)
          for ((name, values) <- seqs) {
            val el = Element.create(name)
            el.content.push(Cdata(values(row)))
            item.content.push(el)
          }
          items.content.push(item)
        }
        Document(items)
      }

}
