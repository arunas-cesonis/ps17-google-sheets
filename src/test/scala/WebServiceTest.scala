import cats.implicits.toTraverseOps
import mypkg.{Config, Http, Params, Resource, Result, Schema, Values, Xml}
import mypkg.Http.Method
import mypkg.Result.Result
import mypkg.Xml.{Cdata, Document, Element}
import utest.{test, TestSuite, Tests}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.channels.Channels
import java.util.UUID
import scala.annotation.tailrec
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object WebServiceTest extends TestSuite {
  @js.native
  @JSImport("../../../config.js", "Config")
  object MyConfig extends Config
  val config = MyConfig

  val tests: Tests = Tests {
    val http = Http.implNodeSyncRequest

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

    def xmlToValues(schema: Schema, doc: Document): Values = {
      val fields = schema.getWritableFieldsWithId
      // def findAndRemove[A](a: A, s: List[A]): Option[(A, List[A])] = {
      //  @tailrec
      //  def go(acc: List[A], rem: List[A]): Option[(A, List[A])] =
      //    rem match {
      //      case h :: t if a == h => Some((a, acc ++ t))
      //      case h :: t           => go(h :: acc, t)
      //      case Nil              => None
      //    }
      //  go(Nil, s)
      // } val rows = js.Array[js.Array[js.Any]]()
      val items = doc.root.findMapElement { e =>
        val items = e.getChildren(schema.itemName)
        Option.when(items.nonEmpty)(items)
      }
      val values = Values.create
      items.ensuring(_.isDefined, s"could not find any '${schema.itemName}' elements").get.map { recEl =>
        val row: js.Array[js.Any] = js.Array()
        fields.foreach { f =>
          row.push(
            recEl.children
              .find(_.name == f.name)
              .ensuring(
                _.isDefined,
                s"xmlToValues: field ${f.name} not found in ${recEl.children.map(_.name).toList.sorted.mkString("\n")}"
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

    def roundtripResource(resource: String): Result[Unit] =
      for {
        schema <- fetchResourceSchema(resource)
        doc1   <- fetchResource(resource)
        doc2   <- fetchResource(resource)
        values1  = xmlToValues(schema, doc1)
        values2  = xmlToValues(schema, doc2)
        values11 = xmlToValues(schema, valuesToXml(schema, values1))
        values22 = xmlToValues(schema, valuesToXml(schema, values2))
        _        = assert(sameValues(values1, values2), "sanity check")
        _        = assert(sameValues(values1, values11), "xml <> values roundtrip")
        _        = assert(sameValues(values2, values22), "xml <> values roundtrip")
        doc3 <- sendResource(resource, valuesToXml(schema, values1))
        values3  = xmlToValues(schema, doc3)
        dc       = columnDiffCounts(schema, values1, values3)
        expected = schema.fields.find(_.name == "date_upd").map(f => (values1.rows.length, f.name)).toList
        _        = assert(dc == expected, s"expected only date_upd to be different: ${dc}")
      } yield ()

    def getAvailableResources: Result[List[String]] = {
      val params = Params.empty
        .add(Params.WsKey(config.key))
        .add(Params.Schema.Synopsis)
      val resp = http.request(Method.Get, config.host, "/api", params.map, None).toTry.get
      val doc  = Xml.parse(resp)
      Result.ok(doc.root.getChild("api").get.children.map(_.name).toList)
    }

    test("roundtripResource") {
      val result = for {
        availableResources <- getAvailableResources
        pairs              <- availableResources.traverse(roundtripResource)
      } yield ()
      val () = result.toTry.get
      // roundtripResource(Resource.products)
    }
  }

}
