import Main.Params.{DateFilter, Display, ToParams}
import googleappscript.xml.{Document, Element, XmlService}
import googleappscript.{Browser, Logger, SpreadsheetApp, URLFetchRequestOptions, UrlFetchApp}

import java.time.Instant
import scala.::
import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import scala.scalajs.js.{JSON, RegExp, UndefOr, UndefOrOps}
import scala.scalajs.js.URIUtils.encodeURIComponent
import scala.scalajs.js.annotation.{JSExportTopLevel, JSGlobal}

object Main {

  implicit class TraverseList[A](list: List[A]) {
    def customTraverseList[B](f: A => Result[B]): Result[List[B]] = {
      val lb = ListBuffer.empty[B]
      for (a <- list)
        f(a) match {
          case Right(value) => lb.addOne(value)
          case Left(err)    => return Left(err)
        }
      Right(lb.toList)
      // def go(rem: List[A], acc: List[B]): Result[List[B]] =
      //  rem match {
      //    case a :: t => f(a).flatMap(b => go(t, acc.appended(b)))
      //    case Nil    => Right(acc)
      //  }
      // go(list, Nil)
    }
  }

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

  sealed trait HTTPMethod extends Product with Serializable
  object HTTPMethod {
    case object Put extends HTTPMethod
    case object Get extends HTTPMethod
  }

  def fromOption[A](a: Option[A]): js.UndefOr[A] =
    a match {
      case Some(value) => value
      case None        => js.undefined
    }

  def http(
    method: HTTPMethod,
    host: String,
    path: String,
    queryParams: Map[String, String],
    body: Option[String]
  ): Result[String] = {
    log(queryParams)
    val url = host + "/" + path + "?" + queryParams
      .map(pair => encodeURIComponent(pair._1) + "=" + encodeURIComponent(pair._2))
      .mkString("&")
    val resp = time(url) {
      val options = method match {
        case HTTPMethod.Put =>
          new URLFetchRequestOptions {
            override val method: js.UndefOr[String]           = "PUT"
            override val muteHttpExceptions: UndefOr[Boolean] = true
            override val contentType: UndefOr[String]         = "text/xml"
            override val payload: UndefOr[String]             = fromOption(body)
          }
        case HTTPMethod.Get =>
          new URLFetchRequestOptions {
            override val method: js.UndefOr[String]           = "GET"
            override val muteHttpExceptions: UndefOr[Boolean] = true
          }
      }
      log(s"${options.method} ${url}")
      UrlFetchApp.fetch(url, options)
    }
    if (resp.getResponseCode() / 100 != 2) {
      log(resp.getContentText())
      fail(s"http code ${resp.getResponseCode()}")
    } else {
      ok(resp.getContentText())
    }
  }

  def put(resource: Resource, params: Map[String, String], body: Document): Result[Document] = {
    val params2    = Map("ws_key" -> Config.key) ++ params
    val bodyString = XmlService.getCompactFormat().format(body)
    log(s"sending [${bodyString}]")
    http(HTTPMethod.Put, Config.host, s"/api/${resource.name}", params2, Some(bodyString)).map { str =>
      log(s"received [${str}]")
      XmlService.parse(str)
    }
  }

  def get(resource: Resource, params: Map[String, String]): Result[Document] = {
    val params2 = Map("ws_key" -> Config.key) ++ params
    http(HTTPMethod.Get, Config.host, s"/api/${resource.name}", params2, None)
      .map(XmlService.parse)
  }

  case class Params(
    map: Map[String, String],
  ) {
    def add[P <: ToParams](p: P): Params = Params(map ++ p.toParams)
  }

  object Params {
    sealed trait ToParams {
      def toParams: Map[String, String]
    }
    def empty: Params = Params(Map.empty)

    sealed trait Display extends Product with Serializable with ToParams {
      self =>
      def toParams: Map[String, String] =
        self match {
          case Display.Full          => Map("display" -> "full")
          case Display.Fields(names) => Map("display" -> names.mkString("[", ",", "]"))
        }
    }

    object Display {
      case object Full extends Display

      case class Fields(names: List[String]) extends Display
      def from(fields: List[Field]): Display = Fields(fields.map(_.name))
    }

    case class OrFilter(field: String, values: List[String]) extends ToParams {
      def toParams: Map[String, String] =
        Map(s"filter[${field}]" -> values.mkString("[", "|", "]"))
    }

    sealed trait DateFilter extends Product with Serializable with ToParams {
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

  case class Resource(name: String, itemName: String)
  object Resource {
    val products        = Resource("products", "product")
    val stockAvailables = Resource("stock_availables", "stock_available")
    val orders          = Resource("orders", "order")
    val byName: Map[String, Resource] = List(
      products,
      stockAvailables,
      orders
    ).map(r => r.name -> r).toMap
  }

  case class Column(name: String, resource: Resource, data: js.Array[js.Any]) { self =>
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
          fail("expected format '$RESOURCE.$FIELD'")
      }
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

    def toSingleResource: Either[Error, Resource] = {
      val resource = columns.map(_.resource).distinct.toList match {
        case single :: Nil => Right(single)
        case other         => fail(s"expected single resource, got: ${other.map(_.name).mkString(", ")}")
      }
      resource
    }

    def toDocument: Either[Error, Document] = {
      val resource = toSingleResource
      def toXml(resource: Resource, itemName: String): Document = {
        val doc = XmlService.createDocument()
        // val root = XmlService.createElement("prestashop")
        // doc.setRootElement(root)
        val resourceRoot = XmlService.createElement(resource.name)
        doc.setRootElement(resourceRoot)
        val n = numRows
        for (i <- 0 until n) {
          val item = XmlService.createElement(itemName)
          columns.foreach { col =>
            val field = XmlService.createElement(col.name)
            val data  = XmlService.createCdata(col.data(i).toString)
            field.addContent(data)
            item.addContent(field)
          }
          resourceRoot.addContent(item)
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
  }

  def anyToInt(x: js.Any): Result[Int] = {
    val t = js.typeOf(x)
    if (t == "number") {
      x.toString.toIntOption.toRight(error(s"can't parse int from string '${x.toString}'"))
    } else {
      fail(s"expected string, got '${t}''")
    }
  }

  def anyToString(x: js.Any): Result[String] = {
    val t = js.typeOf(x)
    if (t == "string") {
      ok(x.toString)
    } else {
      fail(s"expected string, got '${t}''")
    }
  }

  abstract class Error(val msg: String) extends RuntimeException(msg)
  def error(msg: String): Error     = new Error(msg) {}
  def ok[A](a: A): Either[Error, A] = Right(a)
  def ensuring[A](a: A)(cond: A => Boolean, msg: => String): Either[Error, A] =
    if (cond(a)) {
      ok(a)
    } else {
      fail(msg)
    }
  def fail[A](msg: String): Either[Error, A] = Left(error(msg))
  type Result[A] = Either[Error, A]

  // implicit class TraverseList[A](s: List[A]) {
  //  def customTraverseList[B](f: A => Result[B]): Result[List[B]] = {
  //    val lb = ListBuffer.empty[B]
  //    s.
  //    while (p != Nil) {
  //      val b = f()
  //    }
  //
  //  }
  // }

  def listToArray[A](s: List[A]): js.Array[A] = js.Array(s: _*)

  object Table {

    def from(values: js.Array[js.Array[js.Any]]): Result[Table] =
      for {
        columns <- values.head.toList.customTraverseList { s =>
          anyToString(s).flatMap(Column.fromQualifiedName)
        }
        n = columns.length
        _ = values.tail.foreach { row =>
          for (i <- 0 until n)
            columns(i).data.push(row(i))
        }
      } yield Table(listToArray(columns))

    def from(resource: Resource, xml: Document): Result[Table] = {
      val records = time("getChildren")(xml.getRootElement().getChild(resource.name).getChildren(resource.itemName))
      for {
        columns <- makeColumns(resource, records).toRight(error("empty response"))
        _ = fillColumns(columns, records)
      } yield Table(columns)
    }

    def fetchResource(resource: Resource, params: Params): Result[Table] = {
      val map = Map("limit" -> "100000") ++ params.map
      for {
        schema <- get(resource, Map("schema" -> "synopsis")).flatMap(Schema.from(resource, _))
        xml    <- get(resource, map ++ Display.from(schema.getWritableFieldsWithId).toParams)
        table  <- from(resource, xml)
      } yield table
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

  def fetchToCurrentCell(resource: Resource, params: Params): Unit =
    Table.fetchResource(resource, params) match {
      case Right(t) =>
        writeTableAtCurrentCell(t)
      case Left(err) =>
        Browser.msgBox(err.msg)
    }

  @JSExportTopLevel("deploy_getProducts")
  def getProducts(): Unit =
    fetchToCurrentCell(
      Resource.products,
      Params.empty
    )

  @JSExportTopLevel("deploy_getStockAvailables")
  def getStockAvailables(): Unit =
    fetchToCurrentCell(Resource.stockAvailables, Params.empty)

  def formatDate(d: js.Date): String =
    f"${d.getFullYear().toInt}%04d-${d.getMonth().toInt + 1}%02d-${d.getDate().toInt}%02d"

  @JSExportTopLevel("deploy_getOrders")
  def getOrders(): Unit = {
    val start = new js.Date().valueOf() - (86400.0 * 1000.0 * 30.0)
    val end   = new js.Date().valueOf() + (86400.0 * 1000.0 * 1.0)
    val s     = new js.Date(start)
    val e     = new js.Date(end)
    fetchToCurrentCell(Resource.orders, Params.empty.add(DateFilter.Range(formatDate(s), formatDate(e))))
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

  def extendToTop(range: googleappscript.Range): googleappscript.Range =
    range.offset(1 - range.getRow(), 0, range.getRow() + range.getHeight() - 1, range.getWidth())

  def extendToLeft(range: googleappscript.Range): googleappscript.Range =
    range.offset(0, 1 - range.getColumn(), range.getHeight(), range.getColumn() + range.getWidth() - 1)

  def dropPrestashopElement(doc: Document): Unit = {
    val root  = doc.detachRootElement().ensuring(_.getName() == "prestashop")
    val child = root.getChildren().ensuring(_.length == 1).head
    child.detach()
    doc.setRootElement(child)
  }

  case class Field(name: String, readOnly: Boolean, format: Option[String])
  case class Schema(fields: List[Field]) {
    def getWritableFieldsWithId: List[Field] =
      fields.filter(f => f.name == "id" || f.name == "associations" || (!f.readOnly && f.format.isDefined))
  }
  object Schema {
    def from(resource: Resource, doc: Document): Result[Schema] =
      for {
        root <- ensuring[Element](doc.getRootElement())(
          _.getName() == "prestashop",
          "expected 'prestashop' root element"
        )
        _ = log(XmlService.getPrettyFormat().format(doc))
        items <- Option(
          root
            .getChild(resource.itemName)
        )
          .toRight(error(s"expected element '${resource.itemName}' not found"))
        fields = items
          .getChildren()
          .map { item =>
            val readOnly = Option(item.getAttribute("read_only")).map(_.getValue()).contains("true")
            val format   = Option(item.getAttribute("format")).map(_.getValue())
            Field(item.getName(), readOnly = readOnly, format = format)
          }
          .toList
      } yield Schema(Field("id", readOnly = true, format = None) :: fields)
  }

  @JSExportTopLevel("deploy_tableFromSelection")
  def tableFromSelection(): Unit = {
    val result = for {
      selection <- SpreadsheetApp.getActiveSheet().getSelection().getActiveRange().toRight(error("selection is empty"))
      table     <- Table.from(selection.getValues())
      resource  <- table.toSingleResource
      schemaDoc <- get(resource, Map("schema" -> "synopsis"))
      schema <- Schema.from(resource, schemaDoc)
      // idColumn  <- table.columns.find(_.name == "id").toRight(error("no id column found"))
      // ids       <- idColumn.data.toList.customTraverseList(anyToInt)
      // doc       <- table.toDocument
      // p = OrFilter("id", ids.map(_.toString)).toParams ++ Display
      //  .Fields(schema.getWritableFieldsWithId.map(_.name))
      //  .toParams
      // gotDoc <- get(resource, p)
    } yield ()
    result match {
      case Left(value) =>
        log("Error: ", value.msg)
        Browser.msgBox(value.msg)
      case Right(value) =>
        log("Ok: ", value)
    }
    // val cursor      = SpreadsheetApp.getCurrentCell()
    // val cursorToTop = extendToTop(cursor)
    // val cursorToLeft = extendToLeft(cursor)
    // val valuesToTop = cursorToTop.getValues()
    // val row         = valuesToTop.lastIndexWhere(row => Column.fromQualifiedName(row(0).toString).isRight)
    // cursorToLeft.getSheet().setActiveSelection(cursorToLeft)
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
