package mypkg

import cats.implicits.toTraverseOps
import mypkg.Result._
import mypkg.Utils._
import mypkg.Xml.Document
import mypkg.googleappscript.{Browser, SpreadsheetApp}

import scala.annotation.unused
import scala.scalajs.js
import scala.scalajs.js.Date
import scala.scalajs.js.annotation.{JSExportTopLevel, JSImport}

object Main {
  private val http = Http.implAppScript
  @js.native
  @JSImport("../../../config.js", "Config")
  object MyConfig extends Config
  private val config: MyConfig.type = MyConfig
  private val api: Api              = Api.create(config, Http.implAppScript)

  def fetchResource(resource: String): Unit = {
    val prefix = s"fetchResource($resource):"
    log(prefix)
    val (schema, table) = (for {
      schema <- api.fetchResourceSchema(resource)
      _ = log(s"${prefix} fetchResourceSchema")
      doc <- api.fetchResource(resource)
      _ = log(s"${prefix} fetchResource")
      table <- Data.xmlToTable(schema, doc)
      _ = log(s"${prefix} xmlToTable")
    } yield (schema, table)).toTry.get
    val sheet = SpreadsheetApp.getActiveSpreadsheet().insertSheet(resource)
    log(s"${prefix} insertSheet")
    // extra row for header: table.numRows + 1
    val range = sheet.getRange(1, 1, table.numRows + 1, table.numColumns)
    log(s"${prefix} getRange")
    range.setValues(table.toJSArrayWithHeader)
    log(s"${prefix} setValues")
    val dateColumns = schema.fields.zipWithIndex.flatMap(x => Option.when(x._1.format == "isDate")(x._2))
    dateColumns.foreach { i =>
      val columnRange =
        range.getSheet().getRange(1, i + 1, range.getNumRows(), 1)
      columnRange.setNumberFormat("yyyy-mm-dd hh:mm:ss")
    }
  }

  def checkTableSchema(schema: Schema, table: Vec.Table): Unit =
    assert(schema.fields.map(_.name).zip(table.columns.map(_._1)).forall { case (a, b) =>
      a == b
    })

  def checkTableIds(table: Vec.Table): Unit = {
    val seq = table.columns.find(_._1 == "id").get._2.toIndexedSeq.map(_.toInt)
    val set = seq.toSet
    assert(set.size == seq.length)
  }

  def runUpdate(): Unit = {
    val sheet    = SpreadsheetApp.getActiveSheet()
    val resource = sheet.getName()
    for {
      schema       <- api.fetchResourceSchema(resource)
      currentTable <- api.fetchResource(resource).flatMap(Data.xmlToTable(schema, _))
      range = sheet.getRange(1, 1, sheet.getLastRow(), schema.fields.length)
      _     = sheet.setActiveSelection(range)
      table = Vec.Table.fromJSArrayWithHeader(range.getDisplayValues())
      diff = Vec.Table.from(currentTable.equalsTo(table).columns.collect {
        case tmp @ (_, Vec.Dyn.Bool(v)) if v.vector.exists(!_) => tmp
      })
      _ = log(Utils.renderTable(diff))
      _ = checkTableSchema(schema, table)
      _ = checkTableIds(table)
      doc <- Data.tableToXml(schema, table)
      _ = doc.print.split("\n").foreach(s => log(s))
      doc2   <- api.sendResource(resource, doc)
      table2 <- Data.xmlToTable(schema, doc2)
    } yield ()
  }

  @JSExportTopLevel("deploy_fetchProducts", moduleID = "main")
  @unused
  def fetchProducts(): Unit =
    fetchResource("products")

  @JSExportTopLevel("deploy_fetchOrders", moduleID = "main")
  @unused
  def fetchOrders(): Unit =
    fetchResource("orders")

  @JSExportTopLevel("deploy_fetchSpecificPrices", moduleID = "main")
  @unused
  def fetchSpecificPrices(): Unit =
    fetchResource("specific_prices")

  @JSExportTopLevel("deploy_fetchStockAvailables", moduleID = "main")
  @unused
  def fetchStockAvailables(): Unit =
    fetchResource("stock_availables")

  @JSExportTopLevel("deploy_updateFromCurrentSheet", moduleID = "main")
  @unused
  def updateFromCurrentSheet(): Unit =
    runUpdate()

  @JSExportTopLevel("deploy_deleteOtherSheets", moduleID = "main")
  @unused
  def deleteOtherSheets(): Unit = {
    val ss       = SpreadsheetApp.getActiveSpreadsheet()
    val activeId = ss.getActiveSheet().getSheetId()
    ss.getSheets().filter(_.getSheetId() != activeId).foreach(ss.deleteSheet)
  }

  @JSExportTopLevel("deploy_onOpen", moduleID = "main")
  def onOpen(): Unit = {
    SpreadsheetApp.getActiveSpreadsheet().removeMenu("PS17")
    val ui = SpreadsheetApp.getUi()
    val menu = ui
      .createMenu("PS17")
      .addItem("Get products", "fetchProducts")
      .addItem("Get orders", "fetchOrders")
      .addItem("Get specific prices", "fetchSpecificPrices")
      .addItem("Get stock availables", "fetchStockAvailables")
      .addItem("Update from current sheet", "updateFromCurrentSheet")
      .addItem("Delete other sheets", "deleteOtherSheets")

    menu.addToUi()
  }
  // Xml.testRoundTrip("big.xml")

}
