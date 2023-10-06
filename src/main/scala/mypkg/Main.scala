package mypkg

import cats.implicits.toTraverseOps
import mypkg.Result._
import mypkg.Table.Column
import mypkg.Utils._
import mypkg.Xml.Document
import mypkg.googleappscript.{Browser, SpreadsheetApp}

import scala.scalajs.js
import scala.scalajs.js.Date
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {
  private val http = Http.implAppScript

  def put(resource: Resource, params: Params, body: Document): Result[Document] = {
    val params2    = Map("ws_key" -> Config.key) ++ params.map
    val bodyString = body.print
    http.request(Http.Method.Put, Config.host, s"/api/${resource.name}", params2, Some(bodyString)).map { str =>
      Xml.parse(str)
    }
  }

  def get(resource: Resource, params: Params): Result[Document] = {
    val params2 = Map("ws_key" -> Config.key) ++ params.map
    http
      .request(Http.Method.Get, Config.host, s"/api/${resource.name}", params2, None)
      .map(Xml.parse)
  }

  def fetchTable(resource: Resource, params: Params): Result[(Schema, Table)] =
    for {
      schema <- get(resource, Params.empty.add(Params.Schema.Synopsis)).flatMap(Schema.from(resource, _))
      _ = log("a2")
      xml <- get(resource, Params.empty.add(Params.Display.from(schema.getWritableFieldsWithId)))
      _ = log("a4")
      table <- Table.from(resource, xml)
      _ = log("a5")
    } yield (schema, table)

  def writeTableValues(schema: Schema, t: Table, cursor: googleappscript.Range): Unit = {
    val values = t.toValues
    val range  = cursor.offset(0, 0, values.length, t.columns.length)
    range.setValues(values)
    range.setShowHyperlink(false)
    for (dateField <- schema.fields.filter(_.format == "isDate")) {
      val columnIndex = t.columns.indexWhere(_.name == dateField.name)
      if (columnIndex != -1) {
        val columnRange =
          range.getSheet().getRange(range.getRow(), range.getColumn() + columnIndex, range.getNumRows(), 1)
        columnRange.setNumberFormat("yyyy-mm-dd hh:mm")
        // columnRange.setBackgroundRGB(200, 255, 200)
      }
    }
    // range.addDeveloperMetadata("resource", t.toSingleResource.map(_.name).toTry.get)
    range.getSheet().setActiveSelection(range)
  }

  def writeTableMetadata(schema: Schema, t: Table, cursor: googleappscript.Range): Unit = {
    val resource = t.toSingleResource.toTry.get
    cursor.getSheet().addDeveloperMetadata("resource", resource.name)
    cursor.getSheet().addDeveloperMetadata("time", new Date().valueOf().toString)
    t.columns.zipWithIndex.foreach { case (col, i) =>
      val cellNotation = cursor.getSheet().getRange(1, i + 1).getA1Notation()
      assert(cellNotation.last == '1')
      val letter      = cellNotation.dropRight(1)
      val columnRange = cursor.getSheet().getRange(letter + ":" + letter)
      log(s"metadata for ${col.name} ${columnRange.getA1Notation()}")
    // columnRange.addDeveloperMetadata("resource", resource.name)
    // columnRange.addDeveloperMetadata("field-name", col.name)
    // metadata.setShowInCellDropdown(false)

    }
  }

  def writeTableAtCurrentCell(schema: Schema, t: Table): Unit = {
    val sheet  = SpreadsheetApp.getActiveSpreadsheet().insertSheet(t.toSingleResource.toTry.get.name)
    val cursor = sheet.getRange(1, 1)
    writeTableValues(schema, t, cursor)
    writeTableMetadata(schema, t, cursor)
  }

  def fetchToCurrentCell(resource: Resource, params: Params): Unit =
    fetchTable(resource, params) match {
      case Right((s, t)) =>
        writeTableAtCurrentCell(s, t)
      case Left(err) =>
        Browser.msgBox(err.msg)
    }
  def defaultResourceParams(resource: Resource): Params =
    resource.name match {
      case "orders" =>
        val start = new js.Date().valueOf() - (86400.0 * 1000.0 * 30.0)
        val end   = new js.Date().valueOf() + (86400.0 * 1000.0 * 1.0)
        val s     = new js.Date(start)
        val e     = new js.Date(end)
        Params.empty.add(Params.DateFilter.Range(formatDate(s), formatDate(e)))
      case _ => Params.empty
    }

  @JSExportTopLevel("deploy_getProducts")
  def getProducts(): Unit =
    fetchToCurrentCell(Resource.products, defaultResourceParams(Resource.products))

  @JSExportTopLevel("deploy_getStockAvailables")
  def getStockAvailables(): Unit =
    fetchToCurrentCell(Resource.stockAvailables, defaultResourceParams(Resource.stockAvailables))

  def formatDate(d: js.Date): String =
    f"${d.getFullYear().toInt}%04d-${d.getMonth().toInt + 1}%02d-${d.getDate().toInt}%02d"

  @JSExportTopLevel("deploy_getOrders")
  def getOrders(): Unit =
    fetchToCurrentCell(Resource.orders, defaultResourceParams(Resource.orders))

  def extendToTop(range: googleappscript.Range): googleappscript.Range =
    range.offset(1 - range.getRow(), 0, range.getRow() + range.getHeight() - 1, range.getWidth())

  def extendToLeft(range: googleappscript.Range): googleappscript.Range =
    range.offset(0, 1 - range.getColumn(), range.getHeight(), range.getColumn() + range.getWidth() - 1)

  def getIds(t: Table): Result[List[Int]] =
    t.columns
      .find(_.name == "id")
      .toRight(error("clould not find 'id' column"))
      .flatMap(_.data.toList.traverse(x => stringToInt(x.toString)))

  @JSExportTopLevel("deploy_deleteOtherSheets")
  def deleteOtherSheets(): Unit = {
    val ss     = SpreadsheetApp.getActiveSpreadsheet()
    val active = ss.getActiveSheet()
    val sheets = ss.getSheets()
    for (sheet <- sheets)
      if (active.getSheetId() != sheet.getSheetId()) {
        ss.deleteSheet(sheet)
      }
  }

  def parseResource(a: js.Any): Either[Error, Resource] =
    anyToString(a).flatMap(Resource.byName.get(_).toRight(error(s"resource '${a.toString}' not found")))

  def parseColumn(a: js.Any): Either[Error, Column] =
    anyToString(a).flatMap(Column.fromQualifiedName)

  def selectResourceTable(): Unit = {
    val sheet = SpreadsheetApp.getActiveSheet()
    val result = for {
      resource <- parseResource(sheet.getName())
      columnsNamesRow <- sheet
        .getRange(1, 1, 1, sheet.getLastColumn())
        .getValues()
        .headOption
        .toRight(error("no columns found"))
      columns <- ensuring(
        columnsNamesRow
          .map(s => anyToString(s).flatMap(Column.fromQualifiedName))
          .takeWhile(_.isRight)
          .collect { case Right(a) => a }
      )(_.exists(_.name == "id"), "no id column found")
      columns <- ensuring(columns)(_.forall(_.resource == resource), "not all columns match resource")
      range = sheet.getRange(1, 1, sheet.getLastRow(), columns.length)
      t <- Table.from(range.getValues())
      _ = sheet.setActiveSelection(sheet.getRange(1, 1, t.numRows + 1, columns.length))
      doc <- t.toDocument
      _   <- put(resource, Params.empty, doc)
    } yield ()
    val _ = result.toTry.get
    // val md = SpreadsheetApp.getActiveSheet().createDeveloperMetadataFinder().find()
    // md.foreach(m => log("md", m.getKey(), m.getValue()))
    // val cursor                    = SpreadsheetApp.getCurrentCell()
    // val sheet                     = cursor.getSheet()
    // val (cursorRow, cursorColumn) = (cursor.getRow(), cursor.getColumn())
    // val rangeToTop                = sheet.getRange(1, cursorColumn, cursor.getRow(), 1)
    // val valuesToTop               = rangeToTop.getValues()
    // log("valuesToTop", valuesToTop)
    // log("valuesToTop", valuesToTop.zipWithIndex)
    // rangeToTop.setBackgroundRGB(255, 200, 255)
  }

  @JSExportTopLevel("deploy_tableFromSelection")
  def tableFromSelection(): Unit =
    selectResourceTable()

  @JSExportTopLevel("deploy_onOpen")
  def onOpen(): Unit = {
    SpreadsheetApp.getActiveSpreadsheet().removeMenu("PS17")
    val ui = SpreadsheetApp.getUi

    ui.createMenu("PS17")
      .addItem("Get products", "getProducts")
      .addItem("Get stock availability", "getStockAvailables")
      .addItem("Get last month's orders", "getOrders")
      .addItem("Diff with WebService", "tableFromSelection")
      .addItem("Delete other sheets ", "deleteOtherSheets")
      .addToUi()
  }

  def main(args: Array[String]): Unit = {}
  // Xml.testRoundTrip("big.xml")

}
