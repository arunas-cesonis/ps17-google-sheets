import googleappscript.xml.XmlService
import googleappscript.{Browser, Document, Element, Logger, SpreadsheetApp, UrlFetchApp}

import java.time.Instant
import scala.::
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.URIUtils.encodeURIComponent
import scala.scalajs.js.annotation.{JSExportTopLevel, JSGlobal}

object Main {

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

  def get(resource: Resource, params: Map[String, String]): Document = {
    val params2  = Map("ws_key" -> Config.key) ++ params
    val response = httpGet(Config.host, s"/api/${resource.name}", params2)
    time("parse xml") {
      XmlService.parse(response)
    }
  }

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
        case DateFilter.AllTime => Map.empty
        case DateFilter.Range(start, end) =>
          Map("filter[date_upd]" -> s"[${start},${end}]", "date" -> "1")
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

  def makeColumns(resource: Resource, records: js.Array[Element]): Option[js.Array[Column]] =
    records.headOption.map { head =>
      head.getChildren().map(_.getName()).map(Column(_, resource, js.Array()))
    }

  def fillColumns(columns: js.Array[Column], records: js.Array[Element]): Unit =
    time("fill columns") {
      records.map { el =>
        columns
          .foreach { col =>
            val valueElement = el.getChild(col.name)
            val language     = valueElement.getChildren("language")
            val value = if (language.length > 0) {
              language.head.getText()
            } else {
              valueElement.getText()
            }
            col.data.push(value)
          }
      }
    }

  case class Resource(name: String) extends AnyVal
  case class Column(name: String, resource: Resource, data: js.Array[js.Any]) { self =>
    def toQualifiedName: String = s"${resource.name}.${name}"
  }
  case class Table(columns: js.Array[Column]) { self =>
    def debug: String =
      columns
        .map { col =>
          List(col.resource.name, col.name, col.data.take(10).map(_.toString).mkString("[", ", ", "]")).mkString(" ")
        }
        .mkString("\n")
    def toValues: js.Array[js.Array[js.Any]] = {
      val header = columns.map(_.toQualifiedName).map(js.Any.fromString)
      val values = js.Array(header)
      val n      = self.numRows
      for (i <- 0 until n)
        values.push(columns.map(_.data(i)))
      values
    }
    def numRows: Int = columns.head.data.length
    def toStringLists: List[List[String]] =
      toValues.map(_.toList.map(_.toString)).toList

    def toDocument: Document = {
      val doc = XmlService.createDocument()
      // XmlService.createElement()
      // doc.crea
      doc

    }
  }

  def anyToString(x: js.Any): String = {
    val t = js.typeOf(x)
    if (t == "string") {
      x.toString
    } else {
      throw new RuntimeException(s"expected string, got '${t}''")
    }
  }

  object Table {

    def from(values: js.Array[js.Array[js.Any]]): Table = {
      val columns = values.head.map(anyToString).map { name =>
        name.split('.') match {
          case Array(resource, name) =>
            Column(name, Resource(resource), js.Array())
          case _ =>
            Column(name, Resource(""), js.Array())
        }
      }
      val n = columns.length
      values.tail.foreach { row =>
        for (i <- 0 until n)
          columns(i).data.push(row(i))
      }
      Table(columns)
    }

    def from(resource: Resource, itemName: String, xml: Document): Option[Table] = {
      val records = time("getChildren")(xml.getRootElement().getChild(resource.name).getChildren(itemName))
      makeColumns(resource, records) match {
        case Some(columns) =>
          fillColumns(columns, records)
          Some(Table(columns))
        case None =>
          Browser.msgBox("Empty response")
          None
      }
    }

    def fetchResource(resource: Resource, itemName: String, display: Display, dateFilter: DateFilter): Option[Table] = {
      val params = Map("limit" -> "100000") ++ display.toParams ++ dateFilter.toParams
      val xml    = get(resource, params)
      from(resource, itemName, xml)
    }
  }

  def writeTableAtCurrentCell(t: Table): Unit = {
    val values = t.toValues
    val cursor = SpreadsheetApp.getCurrentCell()
    val range  = cursor.offset(0, 0, values.length, t.columns.length)
    range.setValues(values)
    range.setShowHyperlink(false)
    range.getSheet().setActiveSelection(range)
  }

  def readTableFromSelection(): Option[Table] =
    SpreadsheetApp.getActiveSheet().getSelection().getActiveRange().toOption.map { range =>
      Table.from(range.getValues())
    }

  def fetchToCurrentCell(resource: Resource, itemName: String, display: Display, dateFilter: DateFilter): Unit =
    Table.fetchResource(resource, itemName, display, dateFilter) match {
      case Some(t) =>
        writeTableAtCurrentCell(t)

      // val out = readTableFromSelection().get
      // val tbl = out.toStringLists
      //  .zip(t.toStringLists)
      //  .map { case (a, b) =>
      //    a.zip(b).map { case (x, y) => (x == y).toString }
      //  }
      //  .map(x => js.Array(x.toSeq: _*).map(js.Any.fromString))
      // val blam = js.Array(tbl.toSeq: _*)
      // writeTableAtCurrentCell(Table.from(blam))
      case None =>
        Browser.msgBox("Empty response")
    }

  @JSExportTopLevel("deploy_getProducts")
  def getProducts(): Unit =
    fetchToCurrentCell(
      Resource("products"),
      "product",
      Display.Fields(List("id", "name", "price", "wholesale_price")),
      DateFilter.AllTime
    )

  @JSExportTopLevel("deploy_getStockAvailables")
  def getStockAvailables(): Unit =
    fetchToCurrentCell(Resource("stock_availables"), "stock_available", Display.Full, DateFilter.AllTime)

  def formatDate(d: js.Date): String =
    f"${d.getFullYear().toInt}%04d-${d.getMonth().toInt + 1}%02d-${d.getDate().toInt}%02d"

  @JSExportTopLevel("deploy_getOrders")
  def getOrders(): Unit = {
    val start = new js.Date().valueOf() - (86400.0 * 1000.0 * 30.0)
    val end   = new js.Date().valueOf() + (86400.0 * 1000.0 * 1.0)
    val s     = new js.Date(start)
    val e     = new js.Date(end)
    fetchToCurrentCell(Resource("orders"), "order", Display.Full, DateFilter.Range(formatDate(s), formatDate(e)))
  }

  def trimColumnValues(values: js.Array[js.Any]): js.Array[js.Any] =
    values.dropWhile(_.toString.isEmpty).takeWhile(_.toString.nonEmpty)

  def intersectVertically(ranges: js.Array[googleappscript.Range]): js.Array[googleappscript.Range] = {
    val (bottom, top) = ranges.toList.foldLeft((Int.MaxValue, Int.MinValue)) { case ((min, max), range) =>
      val top    = range.getRow()
      val bottom = top + range.getHeight()
      (min.min(bottom), max.max(top))
    }
    log(ranges)
    log(bottom, top)
    ranges.map { r =>
      r.getSheet().getRange(top, r.getColumn(), bottom - top, r.getWidth())
    }
  }

  def offsetToTop(range: googleappscript.Range): googleappscript.Range =
    range.offset(1 - range.getRow(), 0, range.getRow() + range.getHeight() - 1, range.getWidth())

  @JSExportTopLevel("deploy_tableFromSelection")
  def tableFromSelection(): Unit = {
    val sheet = SpreadsheetApp.getActiveSheet()
    val range = SpreadsheetApp.getActiveSheet().getActiveRange().toOption.get
    val table = Table.from(range.getValues())
    assert(table.columns.map(_.resource.name).distinct.length == 1)
    assert(table.columns.map(_.resource.name).distinct.head.nonEmpty)

    // SpreadsheetApp.getActiveSheet().setActiveSelection(tmp)
    // val ranges = sheet.getActiveRangeList().toOption.map(_.getRanges().toList).toList.flatten
    // val sameHeightRanges = intersectVertically(js.Array(ranges:_*))
    // log(sameHeightRanges.map(_.getA1Notation()))
  }

  @JSExportTopLevel("deploy_onOpen")
  def onOpen(): Unit = {
    SpreadsheetApp.getActiveSpreadsheet().removeMenu("PS17")
    val ui = SpreadsheetApp.getUi

    ui.createMenu("PS17")
      .addItem("Get products", "getProducts")
      .addItem("Get stock availability", "getStockAvailables")
      .addItem("Get recent orders", "getOrders")
      .addItem("Table from selection", "tableFromSelection")
      .addToUi()
  }

  def main(args: Array[String]): Unit = {}

}
