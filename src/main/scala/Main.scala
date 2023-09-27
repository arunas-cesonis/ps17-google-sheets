import googleappscript.{Document, Element, Logger, SpreadsheetApp, UrlFetchApp, XmlService}

import java.time.Instant
import scala.::
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.URIUtils.encodeURIComponent
import scala.scalajs.js.annotation.{JSExportTopLevel, JSGlobal}

object Main {

  case class Field(
    name: String,
    value: Option[String],
    format: Option[String],
    fields: List[Field],
    attrs: List[(String, String)],
    path: List[String]
  ) extends Iterable[Field] {
    self =>
    def iterator: Iterator[Field] = fields.iterator
    def paths: List[List[String]] =
      path :: iterator.flatMap(_.paths).toList
    def debug: String = {
      def go(d: Int, x: Field): List[String] =
        List(
          "  ".repeat(d) + x.path.mkString(".") + ": " + x.format
            .getOrElse("") + " value[" + x.value.map(_.length).getOrElse(-1) + "]=[" + x.value.getOrElse(
            "null"
          ) + "] " + x.attrs.map(x => x._1 + "=" + x._2).mkString(", ")
        ) ++ x.fields.flatMap(go(d + 1, _))

      val lines = go(0, self)
      lines.mkString("\n")
    }
    def toHeader: List[List[String]] = {
      def go(acc: List[List[String]], f: Field): List[List[String]] =
        if (f.fields.isEmpty) {
          acc.appended(f.path)
        } else {
          f.fields.foldLeft(acc)(go)
        }
      go(Nil, self)

    }
  }

  object Field {
    def from(root: Element): Field = {
      def go(el: Element, path: List[String]): Field = {
        val attrs   = el.getAttributes()
        val name    = el.getName()
        val newPath = path.appended(name)
        val text    = el.getText()
        Field(
          name = name,
          value = Option.when(text.trim.nonEmpty)(text.trim),
          format = attrs.find(_.getName() == "format").map(_.getValue()),
          fields = el.getChildren().map(go(_, newPath)).toList,
          attrs = attrs.map(x => x.getName() -> x.getValue()).toList,
          path = newPath
        )
      }
      go(root, Nil)
    }
  }

  case class Table(
    header: List[String],
    rows: List[List[String]]
  )
  object Table {
    def from(fields: List[Field]): Table = {
      val header = fields.head.paths.map(_.mkString("."))
      val t      = Table(header, Nil)
      t.header.foreach(log1)
      t
    }
  }

  def dump(doc: Document): Unit =
    log(Field.from(doc.getRootElement()))

  @js.native
  @JSGlobal
  object Config extends js.Object {
    def host: String = js.native
    def key: String  = js.native
  }

  def log1(arg: Any): Unit =
    log(arg)

  def log(args: Any*): Unit =
    Logger.log(
      args.toList
        .map(x =>
          (if (x == null) { "null" }
           else { x.toString })
        )
        .mkString(" ")
    )

  def time[A](title: String)(f: => A): A = {
    val start   = System.nanoTime()
    val result  = f
    val elapsed = System.nanoTime() - start
    log(f"elapsed: ${elapsed.toDouble * 10e-9}%.4fs ${title}")
    result
  }

  def httpGet(host: String, path: String, queryParams: Map[String, String]): String = {
    val url = host + "/" + path + "?" + queryParams
      .map(pair => encodeURIComponent(pair._1) + "=" + encodeURIComponent(pair._2))
      .mkString("&")
    val resp = time(url) {
      UrlFetchApp.fetch(url)
    }
    // Logger.log(s"${resp.getResponseCode()} GET ${url}")
    resp.getContentText()
  }

  def get(resource: String, params: Map[String, String]): Document = {
    val params2  = Map("ws_key" -> Config.key) ++ params
    val response = httpGet(Config.host, s"/api/${resource}", params2)
    time("parse xml") {
      XmlService.parse(response)
    }
  }

  sealed trait RecordValue extends Product with Serializable
  object RecordValue {
    case class Multilingual(values: List[(Int, String)])
    case class String(value: java.lang.String)
  }
  case class RecordField(
    path: List[String],
    value: RecordValue
  )

  sealed trait Display extends Product with Serializable { self =>
    def toParams: Map[String, String] =
      self match {
        case Display.Full          => Map("display" -> "full")
        case Display.Fields(names) => Map("display" -> names.mkString("[", ",", "]"))
      }
  }
  object Display {
    case object Full                       extends Display
    case class Fields(names: List[String]) extends Display
  }

  sealed trait DateFilter extends Product with Serializable {
    self =>
    def toParams: Map[String, String] =
      self match {
        case DateFilter.AllTime           => Map.empty
        case DateFilter.Range(start, end) =>
          //  Map("filter[date_add]" -> "[2023-09-13,2023-09-21]", "date" -> "1")
          Map("filter[date_upd]" -> s"[${start},${end}]", "date" -> "1")
        // ?filter[date_upd]=[2021-01-01 00:00:00|2021-04-07 00:00:00]&date=1
      }
  }

  object DateFilter {
    case object AllTime extends DateFilter

    case class Range(start: String, end: String) extends DateFilter
  }

  def makeHeader(records: js.Array[Element]): Option[js.Array[String]] =
    records.headOption.map { head =>
      head.getChildren().map(_.getName())
    }

  def makeTable(header: js.Array[String], records: js.Array[Element]): js.Array[js.Array[js.Any]] = {
    val table = time("map records") {
      records.map { el =>
        header
          .map { k =>
            val valueElement = el.getChild(k)
            val language     = valueElement.getChildren("language")
            if (language.length > 0) {
              language.head.getText()
            } else {
              valueElement.getText()
            }
          }
          .map(js.Any.fromString)
      }
    }
    table.unshift(header.map(js.Any.fromString))
    table
  }

  def fetchResource(resource: String, itemName: String, display: Display, dateFilter: DateFilter): Unit = {
    val params  = Map("limit" -> "100000") ++ display.toParams ++ dateFilter.toParams
    val xml     = get(resource, params)
    val records = time("getChildren")(xml.getRootElement().getChild(resource).getChildren(itemName))
    makeHeader(records) match {
      case Some(header) =>
        val table  = makeTable(header, records)
        val cursor = SpreadsheetApp.getCurrentCell()
        val range  = cursor.offset(0, 0, table.length, header.length)
        range.setValues(table)
      case None =>
        val cursor = SpreadsheetApp.getCurrentCell()
        cursor.setValues(js.Array(js.Array(js.Any.fromString("server sent 0 rows"))))
    }
  }

  @JSExportTopLevel("deploy_getAllProducts")
  def getAllProducts(): Unit =
    fetchResource(
      "products",
      "product",
      Display.Fields(List("id", "name", "price", "wholesale_price")),
      DateFilter.AllTime
    )

  @JSExportTopLevel("deploy_getStockAvailables")
  def getStockAvailables(): Unit =
    fetchResource("stock_availables", "stock_available", Display.Full, DateFilter.AllTime)

  def formatDate(d: js.Date): String =
    f"${d.getFullYear().toInt}%04d-${d.getMonth().toInt + 1}%02d-${d.getDate().toInt}%02d"

  @JSExportTopLevel("deploy_getOrders")
  def getOrders(): Unit = {
    val start = new js.Date().valueOf() - (86400.0 * 1000.0 * 30.0)
    val end   = new js.Date().valueOf() + (86400.0 * 1000.0 * 1.0)
    val s     = new js.Date(start)
    val e     = new js.Date(end)
    fetchResource("orders", "order", Display.Full, DateFilter.Range(formatDate(s), formatDate(e)))
  }

  @JSExportTopLevel("deploy_onOpen")
  def onOpen(): Unit = {
    SpreadsheetApp.getActiveSpreadsheet().removeMenu("PS17")
    val ui = SpreadsheetApp.getUi

    ui.createMenu("PS17")
      .addItem("Get all products", "getAllProducts")
      .addItem("Get stock availability", "getStockAvailables")
      .addItem("Get recent orders", "getOrders")
      .addToUi()
  }

  def main(args: Array[String]): Unit = {}

}
