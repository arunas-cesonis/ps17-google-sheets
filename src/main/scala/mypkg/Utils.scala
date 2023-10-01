package mypkg

import mypkg.Result.{Result, error, fail, ok}
import mypkg.googleappscript.Logger

import scala.collection.mutable.ListBuffer
import scala.scalajs.js

object Utils {
  object implicits {
    implicit class TraverseList[A](list: List[A]) {
      def customTraverseList[B](f: A => Result[B]): Result[List[B]] = {
        val lb = ListBuffer.empty[B]
        for (a <- list)
          f(a) match {
            case Right(value) => lb.addOne(value)
            case Left(err) => return Left(err)
          }
        Right(lb.toList)
      }
    }
  }


  def fromOption[A](a: Option[A]): js.UndefOr[A] =
    a match {
      case Some(value) => value
      case None => js.undefined
    }

  def log1(arg: Any): Unit =
    log(arg)

  def log(args: Any*): Unit =
    Logger.log(
      args.toList
        .map(x =>
          (if (x == null) {
            "null"
          }
          else {
            x.toString
          })
        )
        .mkString(" ")
    )

  def time[A](title: String)(f: => A): A = {
    val start = System.nanoTime()
    val result = f
    val elapsed = System.nanoTime() - start
    log(f"elapsed: ${elapsed.toDouble * 10e-9}%.4fs ${title}")
    result
  }

  def writeFile(path: String,data:String): Unit = {
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
}
