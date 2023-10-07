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
    def xtest(name: String)(f: => Unit): Unit =
      println(s"ignoring test ${name}")

    def assertRoundTripTableToXml(schema: Schema, doc: Document): Result[Vec.Table] =
      for {
        t    <- Data.xmlToTable(schema, doc)
        doc2 <- Data.tableToXml(schema, t)
        t2   <- Data.xmlToTable(schema, doc2)
        _ = assert(t == t2)
        _ = assert(Utils.renderTable(t) == Utils.renderTable(t2))
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
        List(Schema.Field("a", readOnly = false, "format", multilingual = false), Schema.Field("b", readOnly = false, "format", multilingual = false)),
        Nil
      )
      assertRoundTripTableToXml(schema, Xml.parse(xml))
    }
    val http = Http.implNodeSyncRequest
    val api  = Api.create(config, http)

    test("roundtrip dry GET/PUT") {
      (for {
        resources      <- api.getAvailableResources
        schemas        <- resources.traverse(api.fetchResourceSchema)
        docs           <- resources.traverse(api.fetchResource)
        tables         <- docs.zip(schemas).traverse(a => assertRoundTripTableToXml(a._2, a._1))
        toSend         <- tables.zip(schemas).traverse(a => Data.tableToXml(a._2, a._1))
        docsFromSend   <- toSend.zip(schemas).traverse(a => api.sendResource(a._2.resource, a._1))
        tablesFromSend <- docsFromSend.zip(schemas).traverse(a => assertRoundTripTableToXml(a._2, a._1))
        notEqual = tables
          .zip(tablesFromSend)
          .flatMap { case (a, b) =>
            a.equalsTo(b).columns.collect { case (name, Vec.Dyn.Bool(vec)) if vec.vector.exists(x => !x) => name }
          }
          .distinct
        _ = assert(notEqual == List("date_upd"))
      } yield ()).toTry.get
    }

    test("product price GET/PUT") {
      (for {
        schema <- api.fetchResourceSchema("products")
        doc    <- api.fetchResource("products")
        table  <- Data.xmlToTable(schema, doc)
        newTable = Vec.Table.from(table.columns.map {
          case (name @ "price", Vec.Dyn.String(vec)) =>
            name -> Vec.Dyn.String(Vec.from(vec.vector.map(x => (x.toDouble + 1).toString)))
          case other => other
        })
        doc2         <- Data.tableToXml(schema, newTable)
        updatedTable <- api.sendResource("products", doc2).flatMap(Data.xmlToTable(schema, _))
        diff = table
          .equalsTo(updatedTable)
          .columns
          .collect {
            case (name, Vec.Dyn.Bool(v)) if v.vector.exists(x => !x) => name
          }
          .toSet
        _ = assert(diff == Set("price", "date_upd"))
      } yield ()).toTry.get
    }

  }

}
