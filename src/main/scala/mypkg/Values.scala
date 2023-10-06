package mypkg

import cats.Eq

import scala.scalajs.js

// FIXME: research whether js.Dynamic should be used instead of js.Any here and other similar cases
// https://github.com/scala-js/scala-js/issues/4026
case class Values(rows: js.Array[js.Array[js.Any]]) extends AnyVal {
  def columnCount: Int = rows(0).length
  def appendRowsInPlace(newRows: js.Array[js.Array[js.Any]]): Unit = {
    val cc = columnCount
    for (row <- newRows) {
      assert(cc == row.length)
      rows.push(row)
    }
  }
  def getColumn(index: Int): js.Array[js.Any]   = rows.map(_(index))
  def copy: Values = {
    val newRows: js.Array[js.Array[js.Any]] = js.Array()
    for (row <- rows) {
      val newRow: js.Array[js.Any] = js.Array()
      for (value <- row) {
        newRow.push(value)
      }
      newRows.push(newRow)
    }
    Values(newRows)
  }

  def modifyColumnInPlace(index:Int)(f: js.Any=>js.Any): Unit   = {
    for (row <- rows) {
      row(index) = f(row(index))
    }
  }
  def getRow(index: Int): js.Array[js.Any]      = rows(index)
  def getColumnToList(index: Int): List[String] = getColumn(index).map(_.toString).toList
  def getRowToList(index: Int): List[String]    = getRow(index).map(_.toString).toList
  def toArray: Array[Array[String]] = rows.map(_.map(_.toString).toArray).toArray
}

object Values {
  def create: Values = Values(js.Array())
  def from(iter: Iterable[Iterable[String]]): Values = {
    val rows = js.Array(iter.map(v => js.Array(v.map(js.Any.fromString).toSeq: _*)).toSeq: _*)
    Values(rows)
  }
}
