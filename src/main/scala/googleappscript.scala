import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

object googleappscript {

  @js.native
  @JSGlobal
  object Logger extends js.Object {
    def log(msg: Any): Unit = js.native
  }

  trait URLFetchRequestOptions extends js.Object {
    def method: js.UndefOr[String]
  }

  trait HTTPResponse extends js.Object {
    def getContentText(): String;
    def getContentText(charset: String): String;
    def getHeaders(): js.Object
    def getResponseCode(): Int;
  }

  @js.native
  @JSGlobal
  object UrlFetchApp extends js.Object {
    def fetch(url: String): HTTPResponse                                  = js.native
    def fetch(url: String, options: URLFetchRequestOptions): HTTPResponse = js.native
  }

  @js.native
  @JSGlobal
  object Browser extends js.Object {
    def msgBox(prompt: String): String = js.native;
  }

  trait Attribute extends js.Object {
    def getName(): String;
    def getValue(): String;
    def setName(name: String): Attribute;
    def setValue(value: String): Attribute;
  }

  object xml {

    trait Format extends js.Object  {
      def format(document: Document): String;
      def format(element: Element): String;
      def setEncoding(encoding: String): Format;
      def setIndent(indent: String): Format;
      def setLineSeparator(separator: String): Format;
      def setOmitDeclaration(omitDeclaration: Boolean): Format;
      def setOmitEncoding(omitEncoding: Boolean): Format;
    }
    trait XmlService extends js.Object {
      def createCdata(text: String): Cdata;
      def createComment(text: String): Comment;
      def createDocument(): Document;
      def createDocument(rootElement: Element): Document;
      def createElement(name: String): Element;
      def createText(text: String): Text;
      def getCompactFormat(): Format;
      def getPrettyFormat(): Format;
      def getRawFormat(): Format;
      def parse(xml: String): Document;
    }

    trait Content extends  js.Object  {
      def asCdata(): Cdata;
      def asComment(): Comment;
      //def asDocType(): DocType;
      def asElement(): Element;
      //def asEntityRef(): EntityRef;
      //def asProcessingInstruction(): ProcessingInstruction;
      def asText(): Text;
      def detach(): Content;
      def getParentElement(): Element;
      //def getType(): ContentType;
      def getValue(): String;
    }

    trait Text extends Content {
      def append(text: String): Text;
      def detach(): Content;
      def getParentElement(): Element;
      def getText(): String;
      def getValue(): String;
      def setText(text: String): Text;
    }

    /**
     * A representation of an XML Comment node.
     */
    trait Comment extends Content {
      def detach(): Content;
      def getParentElement(): Element;
      def getText(): String;
      def getValue(): String;
      def setText(text: String): Comment;
    }

    trait Cdata extends js.Object {
      def append(text: String): Text;
      def detach(): Content;
      def getParentElement(): Element;
      def getText(): String;
      def getValue(): String;
      def setText(text: String): Text;
    }

    @js.native
    @JSGlobal
    val XmlService: XmlService = js.native
  }



  trait Element extends js.Object {
    def getAttribute(name: String): Attribute;
    def getAttributes(): js.Array[Attribute];
    def getChild(name: String): Element;
    def getChildText(name: String): String;
    def getChildren(): js.Array[Element];
    def getChildren(name: String): js.Array[Element];
    def getContentSize(): Integer;
    def getDocument(): Document;
    def getName(): String;
    def getParentElement(): Element;
    def getQualifiedName(): String;
    def getText(): String;
    def getValue(): String;
    def isAncestorOf(other: Element): Boolean;
    def isRootElement(): Boolean;
    def removeAttribute(attribute: Attribute): Boolean;
    def removeAttribute(attributeName: String): Boolean;
    def setAttribute(attribute: Attribute): Element;
    def setAttribute(name: String, value: String): Element;
    def setName(name: String): Element;
    def setText(text: String): Element;
  }

  trait Document extends js.Object {
    def getRootElement(): Element;
  }


  @js.native
  trait Menu extends js.Object {
    def addItem(title: String, functionName: String): Menu
    def addToUi(): Unit
  }

  @js.native
  trait Ui extends js.Object {
    def createMenu(title: String): Menu
  }

  @js.native
  @JSGlobal
  object SpreadsheetApp extends js.Object {
    // def AutoFillSeries: typeof AutoFillSeries;
    // def BandingTheme: typeof BandingTheme;
    // def BooleanCriteria: typeof BooleanCriteria;
    // def BorderStyle: typeof BorderStyle;
    // def ColorType: typeof Base
    // def CopyPasteType: typeof CopyPasteType;
    // def DataExecutionErrorCode: typeof DataExecutionErrorCode;
    // def DataExecutionState: typeof DataExecutionState;
    // def DataSourceParameterType: typeof DataSourceParameterType;
    // def DataSourceType: typeof DataSourceType;
    // def DataValidationCriteria: typeof DataValidationCriteria;
    // def DeveloperMetadataLocationType: typeof DeveloperMetadataLocationType;
    // def DeveloperMetadataVisibility: typeof DeveloperMetadataVisibility;
    // def Dimension: typeof Dimension;
    // def Direction: typeof Direction;
    // def GroupControlTogglePosition: typeof GroupControlTogglePosition;
    // def InterpolationType: typeof InterpolationType;
    // def PivotTableSummarizeFunction: typeof PivotTableSummarizeFunction;
    // def PivotValueDisplayType: typeof PivotValueDisplayType;
    // def ProtectionType: typeof ProtectionType;
    // def RecalculationInterval: typeof RecalculationInterval;
    // def RelativeDate: typeof RelativeDate;
    // def SheetType: typeof SheetType;
    // def TextDirection: typeof TextDirection;
    // def TextToColumnsDelimiter: typeof TextToColumnsDelimiter;
    // def ThemeColorType: typeof ThemeColorType;
    // def WrapStrategy: typeof WrapStrategy;
    def create(name: String): Spreadsheet                                  = js.native
    def create(name: String, rows: Integer, columns: Integer): Spreadsheet = js.native
    def enableAllDataSourcesExecution(): Unit                              = js.native
    def enableBigQueryExecution(): Unit                                    = js.native
    def flush(): Unit                                                      = js.native
    def getActive(): Spreadsheet                                           = js.native
    def getActiveRange(): Range                                            = js.native
    def getActiveSheet(): Sheet                                            = js.native
    def getActiveSpreadsheet(): Spreadsheet                                = js.native
    def getCurrentCell(): Range                                            = js.native
    def getSelection(): Selection                                          = js.native
    def getUi(): Ui                                                        = js.native
    def openById(id: String): Spreadsheet                                  = js.native
    def openByUrl(url: String): Spreadsheet                                = js.native
    def setActiveRange(range: Range): Range                                = js.native
    def setActiveSheet(sheet: Sheet): Sheet                                = js.native
    def setActiveSheet(sheet: Sheet, restoreSelection: Boolean): Sheet     = js.native
    def setActiveSpreadsheet(newActiveSpreadsheet: Spreadsheet): Unit      = js.native
    def setCurrentCell(cell: Range): Range                                 = js.native
  }

  trait RangeList extends js.Object {
    def activate(): RangeList;
    def breakApart(): RangeList;
    def check(): RangeList;
    def clear(): RangeList;
    def clearContent(): RangeList;
    def clearDataValidations(): RangeList;
    def clearFormat(): RangeList;
    def clearNote(): RangeList;
    def getRanges(): js.Array[Range];
    def insertCheckboxes(): RangeList;
    def removeCheckboxes(): RangeList;
    def setBackgroundRGB(red: Integer, green: Integer, blue: Integer): RangeList;
    def setValue(value: js.Any): RangeList;
    def setVerticalText(isVertical: Boolean): RangeList;
    def setWrap(isWrapEnabled: Boolean): RangeList;
    def trimWhitespace(): RangeList;
    def uncheck(): RangeList;
  }

  trait Range extends js.Object {
    def activate(): Range;
    def activateAsCurrentCell(): Range;
    def addDeveloperMetadata(key: String): Range;
    def breakApart(): Range;
    def canEdit(): Boolean;
    def check(): Range;
    def clear(): Range;
    def clearContent(): Range;
    def clearDataValidations(): Range;
    def clearFormat(): Range;
    def clearNote(): Range;
    def collapseGroups(): Range;
    def copyValuesToRange(sheet: Sheet, column: Integer, columnEnd: Integer, row: Integer, rowEnd: Integer): Unit;
    def expandGroups(): Range;
    def getA1Notation(): String;
    def getBackground(): String;
    def getCell(row: Integer, column: Integer): Range;
    def getColumn(): Integer;
    def getDataRegion(): Range;
    def getDataSourceUrl(): String;
    def getDisplayValue(): String;
    def getFontColor(): String;
    def getFontFamily(): String;
    def getFontSize(): Integer;
    def getFormula(): String;
    def getGridId(): Integer;
    def getHeight(): Integer;
    def getHorizontalAlignment(): String;
    def getLastColumn(): Integer;
    def getLastRow(): Integer;
    def getNote(): String;
    def getNumColumns(): Integer;
    def getNumRows(): Integer;
    def getNumberFormat(): String;
    def getRow(): Integer;
    def getRowIndex(): Integer;
    def getSheet(): Sheet;
    def getVerticalAlignment(): String;
    def getWidth(): Integer;
    def getWrap(): Boolean;
    def insertCheckboxes(): Range;
    def isBlank(): Boolean;
    def isEndColumnBounded(): Boolean;
    def isEndRowBounded(): Boolean;
    def isPartOfMerge(): Boolean;
    def isStartColumnBounded(): Boolean;
    def isStartRowBounded(): Boolean;
    def merge(): Range;
    def mergeAcross(): Range;
    def mergeVertically(): Range;
    def moveTo(target: Range): Unit;
    def offset(rowOffset: Integer, columnOffset: Integer): Range;
    def offset(rowOffset: Integer, columnOffset: Integer, numRows: Integer): Range;
    def offset(rowOffset: Integer, columnOffset: Integer, numRows: Integer, numColumns: Integer): Range;
    def setValues(values: js.Array[js.Array[js.Any]]): Range;
    def getValues(): js.Array[js.Array[js.Any]];
    def randomize(): Range;
    def removeCheckboxes(): Range;
    def removeDuplicates(): Range;
    def setBackgroundRGB(red: Integer, green: Integer, blue: Integer): Range;
    def setFontSize(size: Integer): Range;
    def setFormula(formula: String): Range;
    def setFormulaR1C1(formula: String): Range;
    def setNumberFormat(numberFormat: String): Range;
    def setShowHyperlink(showHyperlink: Boolean): Range;
    def setTextRotation(degrees: Integer): Range;
    def setVerticalText(isVertical: Boolean): Range;
    def setWrap(isWrapEnabled: Boolean): Range;
    def shiftColumnGroupDepth(delta: Integer): Range;
    def shiftRowGroupDepth(delta: Integer): Range;
    def splitTextToColumns(): Unit;
    def splitTextToColumns(delimiter: String): Unit;
    def trimWhitespace(): Range;
    def uncheck(): Range;
  }

  trait Spreadsheet extends js.Object {
    def addDeveloperMetadata(key: String): Spreadsheet;
    def addDeveloperMetadata(key: String, value: String): Spreadsheet;
    def addEditor(emailAddress: String): Spreadsheet;
    def addViewer(emailAddress: String): Spreadsheet;
    def autoResizeColumn(columnPosition: Integer): Sheet;
    def copy(name: String): Spreadsheet;
    def deleteActiveSheet(): Sheet;
    def deleteColumn(columnPosition: Integer): Sheet;
    def deleteColumns(columnPosition: Integer, howMany: Integer): Unit;
    def deleteRow(rowPosition: Integer): Sheet;
    def deleteRows(rowPosition: Integer, howMany: Integer): Unit;
    def deleteSheet(sheet: Sheet): Unit;
    def duplicateActiveSheet(): Sheet;
    def getActiveCell(): Range;
    def getActiveSheet(): Sheet;
    def getColumnWidth(columnPosition: Integer): Integer;
    def getDataRange(): Range;
    def getFrozenColumns(): Integer;
    def getFrozenRows(): Integer;
    def getId(): String;
    def getLastColumn(): Integer;
    def getLastRow(): Integer;
    def getMaxIterativeCalculationCycles(): Integer;
    def getName(): String;
    def getNumSheets(): Integer;
    def getRange(a1Notation: String): Range;
    def getRangeByName(name: String): js.UndefOr[Range];
    def getRowHeight(rowPosition: Integer): Integer;
    def getSelection(): Selection;
    def getSheetByName(name: String): js.UndefOr[Sheet];
    def getSheetId(): Integer;
    def getSheetName(): String;
    def getSheetValues(
      startRow: Integer,
      startColumn: Integer,
      numRows: Integer,
      numColumns: Integer
    ): js.Array[js.Array[js.Any]];
    def getSpreadsheetTimeZone(): String;
    def getUrl(): String;
    def hideColumn(column: Range): Unit;
    def hideRow(row: Range): Unit;
    def insertColumnAfter(afterPosition: Integer): Sheet;
    def insertColumnBefore(beforePosition: Integer): Sheet;
    def insertColumnsAfter(afterPosition: Integer, howMany: Integer): Sheet;
    def insertColumnsBefore(beforePosition: Integer, howMany: Integer): Sheet;
    def insertRowAfter(afterPosition: Integer): Sheet;
    def insertRowBefore(beforePosition: Integer): Sheet;
    def insertRowsAfter(afterPosition: Integer, howMany: Integer): Sheet;
    def insertRowsBefore(beforePosition: Integer, howMany: Integer): Sheet;
    def insertSheet(): Sheet;
    def insertSheet(sheetIndex: Integer): Sheet;
    def insertSheet(sheetName: String): Sheet;
    def insertSheet(sheetName: String, sheetIndex: Integer): Sheet;
    def isColumnHiddenByUser(columnPosition: Integer): Boolean;
    def isIterativeCalculationEnabled(): Boolean;
    def isRowHiddenByFilter(rowPosition: Integer): Boolean;
    def isRowHiddenByUser(rowPosition: Integer): Boolean;
    def moveActiveSheet(pos: Integer): Unit;
    def removeEditor(emailAddress: String): Spreadsheet;
    def removeMenu(name: String): Unit;
    def removeNamedRange(name: String): Unit;
    def removeViewer(emailAddress: String): Spreadsheet;
    def rename(newName: String): Unit;
    def renameActiveSheet(newName: String): Unit;
    def setActiveSelection(range: Range): Range;
    def setActiveSelection(a1Notation: String): Range;
    def setActiveSheet(sheet: Sheet): Sheet;
    def setActiveSheet(sheet: Sheet, restoreSelection: Boolean): Sheet;
    def setColumnWidth(columnPosition: Integer, width: Integer): Sheet;
    def setCurrentCell(cell: Range): Range;
    def setFrozenColumns(columns: Integer): Unit;
    def setFrozenRows(rows: Integer): Unit;
    def setIterativeCalculationEnabled(isEnabled: Boolean): Spreadsheet;
    def setMaxIterativeCalculationCycles(maxIterations: Integer): Spreadsheet;
    def setNamedRange(name: String, range: Range): Unit;
    def setRowHeight(rowPosition: Integer, height: Integer): Sheet;
    def setSpreadsheetLocale(locale: String): Unit;
    def setSpreadsheetTimeZone(timezone: String): Unit;
    def sort(columnPosition: Integer): Sheet;
    def sort(columnPosition: Integer, ascending: Boolean): Sheet;
    def toast(msg: String): Unit;
    def toast(msg: String, title: String): Unit;
    def unhideColumn(column: Range): Unit;
    def unhideRow(row: Range): Unit;
  }

  trait Selection extends js.Object {
    def getActiveRange(): js.UndefOr[Range];
    def getActiveSheet(): Sheet;
    def getCurrentCell(): js.UndefOr[Range];
  }

  trait Sheet extends js.Object {
    def getActiveRangeList(): js.UndefOr[RangeList];
    def getRangeList(a1Notations: js.Array[String]): RangeList;
    def setActiveRangeList(rangeList: RangeList): RangeList;
    def activate(): Sheet;
    def addDeveloperMetadata(key: String): Sheet;
    def addDeveloperMetadata(key: String, value: String): Sheet;
    def appendRow(rowContents: js.Array[String]): Sheet;
    def autoResizeColumn(columnPosition: Integer): Sheet;
    def autoResizeColumns(startColumn: Integer, numColumns: Integer): Sheet;
    def autoResizeRows(startRow: Integer, numRows: Integer): Sheet;
    def clear(): Sheet;
    def clearConditionalFormatRules(): Unit;
    def clearContents(): Sheet;
    def clearFormats(): Sheet;
    def clearNotes(): Sheet;
    def collapseAllColumnGroups(): Sheet;
    def collapseAllRowGroups(): Sheet;
    def copyTo(spreadsheet: Spreadsheet): Sheet;
    def deleteColumn(columnPosition: Integer): Sheet;
    def deleteColumns(columnPosition: Integer, howMany: Integer): Unit;
    def deleteRow(rowPosition: Integer): Sheet;
    def deleteRows(rowPosition: Integer, howMany: Integer): Unit;
    def expandAllColumnGroups(): Sheet;
    def expandAllRowGroups(): Sheet;
    def expandColumnGroupsUpToDepth(groupDepth: Integer): Sheet;
    def expandRowGroupsUpToDepth(groupDepth: Integer): Sheet;
    def getActiveCell(): Range;
    def getActiveRange(): js.UndefOr[Range];
    def getColumnGroupDepth(columnIndex: Integer): Integer;
    def getColumnWidth(columnPosition: Integer): Integer;
    def getCurrentCell(): js.UndefOr[Range];
    def getDataRange(): Range;
    def getFrozenColumns(): Integer;
    def getFrozenRows(): Integer;
    def getIndex(): Integer;
    def getLastColumn(): Integer;
    def getLastRow(): Integer;
    def getMaxColumns(): Integer;
    def getMaxRows(): Integer;
    def getName(): String;
    def getParent(): Spreadsheet;
    def getRange(row: Integer, column: Integer): Range;
    def getRange(row: Integer, column: Integer, numRows: Integer): Range;
    def getRange(row: Integer, column: Integer, numRows: Integer, numColumns: Integer): Range;
    def getRange(a1Notation: String): Range;
    def getRowGroupDepth(rowIndex: Integer): Integer;
    def getRowHeight(rowPosition: Integer): Integer;
    def getSelection(): Selection;
    def getSheetId(): Integer;
    def getSheetName(): String;
    def getSheetValues(
      startRow: Integer,
      startColumn: Integer,
      numRows: Integer,
      numColumns: Integer
    ): js.Array[js.Array[js.Any]];
    def hasHiddenGridlines(): Boolean;
    def hideColumn(column: Range): Unit;
    def hideColumns(columnIndex: Integer): Unit;
    def hideColumns(columnIndex: Integer, numColumns: Integer): Unit;
    def hideRow(row: Range): Unit;
    def hideRows(rowIndex: Integer): Unit;
    def hideRows(rowIndex: Integer, numRows: Integer): Unit;
    def hideSheet(): Sheet;
    def insertColumnAfter(afterPosition: Integer): Sheet;
    def insertColumnBefore(beforePosition: Integer): Sheet;
    def insertColumns(columnIndex: Integer): Unit;
    def insertColumns(columnIndex: Integer, numColumns: Integer): Unit;
    def insertColumnsAfter(afterPosition: Integer, howMany: Integer): Sheet;
    def insertColumnsBefore(beforePosition: Integer, howMany: Integer): Sheet;
    def insertRowAfter(afterPosition: Integer): Sheet;
    def insertRowBefore(beforePosition: Integer): Sheet;
    def insertRows(rowIndex: Integer): Unit;
    def insertRows(rowIndex: Integer, numRows: Integer): Unit;
    def insertRowsAfter(afterPosition: Integer, howMany: Integer): Sheet;
    def insertRowsBefore(beforePosition: Integer, howMany: Integer): Sheet;
    def isColumnHiddenByUser(columnPosition: Integer): Boolean;
    def isRightToLeft(): Boolean;
    def isRowHiddenByFilter(rowPosition: Integer): Boolean;
    def isRowHiddenByUser(rowPosition: Integer): Boolean;
    def isSheetHidden(): Boolean;
    def moveColumns(columnSpec: Range, destinationIndex: Integer): Unit;
    def moveRows(rowSpec: Range, destinationIndex: Integer): Unit;
    def setActiveRange(range: Range): Range;
    def setActiveSelection(range: Range): Range;
    def setActiveSelection(a1Notation: String): Range;
    def setColumnWidth(columnPosition: Integer, width: Integer): Sheet;
    def setColumnWidths(startColumn: Integer, numColumns: Integer, width: Integer): Sheet;
    def setCurrentCell(cell: Range): Range;
    def setFrozenColumns(columns: Integer): Unit;
    def setFrozenRows(rows: Integer): Unit;
    def setHiddenGridlines(hideGridlines: Boolean): Sheet;
    def setName(name: String): Sheet;
    def setRightToLeft(rightToLeft: Boolean): Sheet;
    def setRowHeight(rowPosition: Integer, height: Integer): Sheet;
    def setRowHeights(startRow: Integer, numRows: Integer, height: Integer): Sheet;
    def setRowHeightsForced(startRow: Integer, numRows: Integer, height: Integer): Sheet;
    def showColumns(columnIndex: Integer): Unit;
    def showColumns(columnIndex: Integer, numColumns: Integer): Unit;
    def showRows(rowIndex: Integer): Unit;
    def showRows(rowIndex: Integer, numRows: Integer): Unit;
    def showSheet(): Sheet;
    def sort(columnPosition: Integer): Sheet;
    def sort(columnPosition: Integer, ascending: Boolean): Sheet;
    def unhideColumn(column: Range): Unit;
    def unhideRow(row: Range): Unit;
  }

}
