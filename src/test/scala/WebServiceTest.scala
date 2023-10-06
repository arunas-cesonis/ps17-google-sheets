import cats.implicits.toTraverseOps
import mypkg.Http.Method
import mypkg.Result.{error, Result}
import mypkg.Utils.{pp, writeFile}
import mypkg.Xml.{Cdata, Document, Element}
import mypkg._
import utest.{test, TestSuite, Tests}

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object WebServiceTest extends TestSuite {
  @js.native
  @JSImport("../../../config.js", "Config")
  object MyConfig extends Config
  val config = MyConfig

  val tests: Tests = Tests {
    val http = Http.implNodeSyncRequest

    def fetchResourceSchema1(resource: String): Result[Document] = {
      val params = Params.empty
        .add(Params.WsKey(config.key))
        .add(Params.Schema.Synopsis)
      val resp = http.request(Method.Get, config.host, s"/api/${resource}", params.map, None).toTry.get
      val data = Xml.parse(resp)
      Result.ok(data)
    }

    def fetchResourceSchema(resource: String): Result[Schema] = {
      val params = Params.empty
        .add(Params.WsKey(config.key))
        .add(Params.Schema.Synopsis)
      val resp = http.request(Method.Get, config.host, s"/api/${resource}", params.map, None).toTry.get
      val data = Xml.parse(resp)
      Schema.from(resource, data)
    }

    def fetchResource(resource: String): Result[Document] = {
      val params = Params.empty
        .add(Params.WsKey(config.key))
        .add(Params.Display.Full)
        .add(Params.Limit(5))
      val resp = http.request(Method.Get, config.host, s"/api/${resource}", params.map, None).toTry.get
      val data = Xml.parse(resp)
      Result.ok(data)
    }

    def sendResource(resource: String, doc: Document): Result[Document] = {
      val params = Params.empty
        .add(Params.WsKey(config.key))
        .add(Params.Display.Full)
      val resp = http.request(Method.Put, config.host, s"/api/${resource}", params.map, Some(doc.print)).toTry.get
      val data = Xml.parse(resp)
      Result.ok(data)
    }

    def valuesToXml(schema: Schema, values: Values): Document = {
      val content: js.Array[Xml.Content] = values.rows.map { row =>
        val item = Element.create(schema.itemName)
        schema.fields.zip(row).foreach { case (field, value) =>
          val el = Element.create(field.name)
          el.content.push(Cdata(value.toString))
          item.content.push(el)
        }
        item
      }
      val root = Element.create(schema.resource).copy(content = content)
      Document(root)
    }

    def xmlToHeader(schema: Schema, doc: Document): Values = {
      val row: js.Array[js.Any] = js.Array(schema.getWritableFieldsWithId.map(_.name).map(js.Any.fromString): _*)
      val rows                  = js.Array(row)
      Values(rows)
    }

    def singleElement(e: Element): Result[Element] =
      e.singleElement.toRight {
        val s     = e.elements.toList
        val names = s.map(_.name).distinct.take(5)
        Result.error(s"expected single element, found ${s.length}, first 5 distinct names: ${names}")
      }

    def singleByName(e: Element, name: String): Result[Element] =
      e.singleElementByName(name).toRight {
        val s     = e.elements.toList
        val names = s.map(_.name).distinct
        Result.error(s"expected single element named '${name}', found ${s.length}, first 5 distinct names: ${names}")
      }

    def xmlToAssocationsValues(schema: Schema, doc: Document): Result[List[(String, Values)]] = {
      val assoc = doc.root.singleElement.toList
        .flatMap(_.elements)
        .flatMap { item =>
          val id    = item.singleElementByName("id").get.text
          val assoc = item.singleElementByName("associations").get
          val tmp = schema.associations.map { a =>
            val rows = assoc.getElement(a.name).get
            a.name -> (rows
              .getElements(a.nodeType)
              .map { row =>
                id :: a.fields.map(f => row.singleElementByName(f.name).get.text)
              }
              .toList)
          }
          tmp
        }
      val r = assoc
        .groupMapReduce(_._1)(_._2)(_ ++ _)
        .view
        .map { case (name, rows) =>
          val header = s"__${schema.itemName}_id__" :: schema.associations.find(_.name == name).get.fields.map(_.name)
          val values = Values.from(header :: rows)
          name -> values
        }
        .toList
      Result.ok(r)
    }

    def xmlToAssocationsValuesOpt(schema: Schema, doc: Document): Result[Option[List[(String, Values)]]] =
      if (schema.associations.nonEmpty) {
        xmlToAssocationsValues(schema, doc).map(Some(_))
      } else {
        Result.ok(None)
      }

    def showTable(t: Vec.Table): String = {
      val header = t.columns.map(_._1).toArray
      val body   = t.columns.map(_._2.toStringArray).toArray.transpose
      val table  = header :: body.toList
      Utils.renderTable(table.toArray)
    }

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
          val names   = columns.map(_._1).toArray
          val seqs    = columns.map(pair => pair._1 -> pair._2.toIndexedSeq)
          val numRows = seqs.head._2.length
          val items   = Element.create(schema.resource)
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

    def xmlToValues(schema: Schema, doc: Document): Values = {
      val table  = xmlToTable(schema, doc).toTry.get
      val fields = schema.getWritableFieldsWithId
      val items = doc.root.findMapElement { e =>
        val items = e.getElements(schema.itemName)
        Option.when(items.nonEmpty)(items)
      }
      val values = Values.create
      items.ensuring(_.isDefined, s"could not find any '${schema.itemName}' elements").get.map { recEl =>
        val row: js.Array[js.Any] = js.Array()
        fields.foreach { f =>
          row.push(
            recEl.elements
              .find(_.name == f.name)
              .ensuring(
                _.isDefined,
                s"xmlToValues: field ${f.name} not found in ${recEl.elements.map(_.name).toList.sorted.mkString("\n")}"
              )
              .get
              .text
          )
        }
        values.rows.push(row)
      }
      values
    }

    def columnDiffCounts(schema: Schema, a: Values, b: Values): List[(Int, String)] = {
      assert(a.rows.length == b.rows.length)
      val acols = a.rows.transpose
      val bcols = b.rows.transpose
      assert(bcols.length == acols.length)
      acols
        .zip(bcols)
        .map { case (x, y) =>
          x.zip(y).count { case (q, w) =>
            q.toString != w.toString
          }
        }
        .zip(schema.getWritableFieldsWithId.map(_.name))
        .filter(_._1 > 0)
        .toList
    }

    def sameValues(a: Values, b: Values): Boolean =
      a.rows.length == b.rows.length &&
        a.rows.zip(b.rows).forall { case (x, y) =>
          x.length == y.length && x.zip(y).forall(p => p._1.toString == p._2.toString)
        }

    def getAvailableResources: Result[List[String]] = {
      val params = Params.empty
        .add(Params.WsKey(config.key))
        .add(Params.Schema.Synopsis)
      val resp = http.request(Method.Get, config.host, "/api", params.map, None).toTry.get
      val doc  = Xml.parse(resp)
      Result.ok(doc.root.getElement("api").get.elements.map(_.name).toList)
    }

    def xtest(name: String)(f: => Unit): Unit =
      println(s"ignoring test ${name}")

    def assertRoundTripTableToXml(schema: Schema, doc: Document): Result[Vec.Table] =
      for {
        t    <- xmlToTable(schema, doc)
        doc2 <- tableToXml(schema, t)
        t2   <- xmlToTable(schema, doc2)
        _ = assert(t == t2)
        _ = assert(showTable(t) == showTable(t2))
      } yield t

    test("Vec.Table") {
      val xml =
        """
          <resource>
            <item><a>1</a><b>2</b></item>
            <item><a>2</a><b>3</b></item>
            <item><a>3</a><b>4</b></item>
          </resource>
          """
      val schema = Schema(
        "resource",
        "item",
        List(Schema.Field("a", readOnly = false, "format"), Schema.Field("b", readOnly = false, "format")),
        Nil
      )
      assertRoundTripTableToXml(schema, Xml.parse(xml))
    }

    test("fetch") {
      (for {
        resources <- getAvailableResources
        schemas   <- resources.traverse(fetchResourceSchema)
        docs      <- resources.traverse(fetchResource)
        tables    <- docs.zip(schemas).traverse(a => assertRoundTripTableToXml(a._2, a._1))
        toSend    <- tables.zip(schemas).traverse(a => tableToXml(a._2, a._1))
        _         <- toSend.zip(schemas).traverse(a => sendResource(a._2.resource, a._1))
        // _         <- docs.zip(schemas).traverse(a => xmlToTable(a._2, a._1))
      } yield ()).toTry.get

    }

  }

}
