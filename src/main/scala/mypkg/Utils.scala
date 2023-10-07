package mypkg

import cats.Eq
import mypkg.Result.{error, fail, ok, Result}
import mypkg.googleappscript.Logger

import scala.collection.mutable.ListBuffer
import scala.scalajs.js

object Utils {

  def pp(a: Any*): Unit =
    a.foreach(x => pprint.pprintln(x))

  def fromOption[A](a: Option[A]): js.UndefOr[A] =
    a match {
      case Some(value) => value
      case None        => js.undefined
    }

  def log1(arg: Any): Unit =
    log(arg)

  def log(args: Any*): Unit = {
    println(
    //Logger.log(
      args.toList
        .map(x =>
          (if (x == null) {
             "null"
           } else {
             x.toString
           })
        )
        .mkString(" ")
    )
  }

  def time[A](title: String)(f: => A): A = {
    val start   = System.nanoTime()
    val result  = f
    val elapsed = System.nanoTime() - start
    log(f"elapsed: ${elapsed.toDouble * 10e-9}%.4fs ${title}")
    result
  }

  def writeFile(path: String, data: String): Unit = {
    import scalajs.js.Dynamic.{global => g}
    val fs = g.require("fs")
    fs.writeFileSync(path, data)
  }

  def readFile(path: String): String = {
    import scalajs.js.Dynamic.{global => g}
    val fs = g.require("fs")
    fs.readFileSync(path).toString
  }

  def stringToInt(x: String): Result[Int] =
    x.toIntOption.toRight(error(s"can't parse int from string '${x.toString}'"))

  def anyToInt(x: js.Any): Result[Int] = {
    val t = js.typeOf(x)
    if (t == "number") {
      stringToInt(x.toString)
    } else {
      fail(s"expected number, got '$t}'")
    }
  }

  def anyToString(x: js.Any): Result[String] = {
    val t = js.typeOf(x)
    if (t == "string") {
      ok(x.toString)
    } else {
      fail(s"expected string, got '${t}'")
    }
  }

  def listToArray[A](s: List[A]): js.Array[A] = js.Array(s: _*)

  def renderTable(table: Array[Array[String]]): String = {
    val vsep      = "| "
    val maxWidths = Array.fill[Int](table(0).length)(0)
    for (row <- table)
      for ((value, i) <- row.zipWithIndex) {
        val width = value.length
        if (width > maxWidths(i)) {
          maxWidths(i) = width
        }
      }
    val sb = new StringBuilder()
    for (row <- table) {
      for ((value, width) <- row.zip(maxWidths)) {
        val pad = width - value.length
        sb.addAll(vsep)
        sb.addAll(" " * pad)
        sb.addAll(value)
      }
      sb.addOne('\n')
    }
    sb.toString()
  }

  def renderTable(t: Vec.Table): String = {
    val header = t.columns.map(_._1).toArray
    val body   = t.columns.map(_._2.toStringArray).toArray.transpose
    val table  = header :: body.toList
    Utils.renderTable(table.toArray)
  }
}
