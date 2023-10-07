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

    def xmlToValues(schema: Schema, doc: Document): Values = {
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
