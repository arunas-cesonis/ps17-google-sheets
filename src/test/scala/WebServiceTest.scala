import cats.implicits.toTraverseOps
import mypkg.{Config, Http, Params, Resource, Result, Schema, Xml}
import mypkg.Http.Method
import mypkg.Result.Result
import mypkg.Xml.Document
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
      Schema.from(data)
    }

    def fetchResource(resource: String): Result[Document] = {
      val params = Params.empty
        .add(Params.WsKey(config.key))
        .add(Params.Display.Full)
      val resp = http.request(Method.Get, config.host, s"/api/${resource}", params.map, None).toTry.get
      val data = Xml.parse(resp)
      Result.ok(data)
    }

    def sendResource(resource: String, doc: Document): Result[Document] = {
      val params = Params.empty
        .add(Params.WsKey(config.key))
      val resp = http.request(Method.Put, config.host, s"/api/${resource}", params.map, Some(doc.print)).toTry.get
      val data = Xml.parse(resp)
      Result.ok(data)
    }

    def replaceRootWithSingleChild(doc: Document): Document = {
      val maybleSingle = doc.root.ensuring(r => r.name == "prestashop").children.toList
      val item         = maybleSingle.ensuring(_.length == 1, "expected single child in <prestashop/>").head
      Document(item)
    }

    def valuesToXml(schema: Schema, values: js.Array[js.Array[js.Any]]): Document =

    }

    def resourceResponseToValues(schema: Schema, doc: Document): js.Array[js.Array[js.Any]] = {
      val fields = schema.getWritableFieldsWithId
      val doc2   = replaceRootWithSingleChild(doc)
      def findAndRemove[A](a: A, s: List[A]): Option[(A, List[A])] = {
        @tailrec
        def go(acc: List[A], rem: List[A]): Option[(A, List[A])] =
          rem match {
            case h :: t if a == h => Some((a, acc ++ t))
            case h :: t           => go(h :: acc, t)
            case Nil              => None
          }
        go(Nil, s)
      }
      val rows = js.Array[js.Array[js.Any]]()
      doc.root.children.take(1).toList.head.children.foreach { recEl =>
        val row: js.Array[js.Any] = js.Array()
        fields.foreach { f =>
          row.push(
            recEl.children
              .find(_.name == f.name)
              .ensuring(
                _.isDefined,
                s"field ${f.name} not found in ${recEl.children.map(_.name).toList.sorted.mkString("\n")}"
              )
              .get
              .text
          )
        }
        rows.push(row)
      }
      rows
    }

    def roundtripResource(resource: String): Result[Unit] =
      for {
        schema <- fetchResourceSchema(resource)
        doc    <- fetchResource(resource)
        values = resourceResponseToValues(schema, doc)
        _      = println(resource, values.map(_.length).sum)
        // doc2 <- sendResource(resource, sendable)
      } yield ()

    def getAvailableResources: Result[List[String]] = {
      val params = Params.empty
        .add(Params.WsKey(config.key))
        .add(Params.Schema.Synopsis)
      val resp = http.request(Method.Get, config.host, "/api", params.map, None).toTry.get
      val doc  = Xml.parse(resp)
      Result.ok(doc.root.getChild("api").get.children.map(_.name).toList)
    }

    test("vec") {
      val result = for {
        availableResources <- getAvailableResources
        pairs              <- availableResources.traverse(roundtripResource)
      } yield ()
      val () = result.toTry.get
      // roundtripResource(Resource.products)
    }
  }

}
