package mypkg

import scala.scalajs.js

case class Vec[A](vector: Vector[A]) extends AnyVal

object Vec {
  def empty[A]: Vec[A]                 = Vec(Vector.empty[A])
  def apply[A](as: A*): Vec[A]         = Vec(as.toVector)
  def from[A](as: Iterable[A]): Vec[A] = Vec(as.toVector)
  def from[A](as: Vector[A]): Vec[A]   = Vec(as)

  sealed trait Dyn extends Product with Serializable { self =>
    def length: Int
    def toIndexedSeq: IndexedSeq[String]
    def toStringArray: Array[String] = toIndexedSeq.toArray
    def equalsTo(other: Dyn): Dyn =
      (self, other) match {
        case (Dyn.String(v), Dyn.String(t)) => Dyn.Bool(Vec.from(v.vector.zip(t.vector).map(x => x._1 == x._2)))
        case (Dyn.Int(v), Dyn.Int(t))       => Dyn.Bool(Vec.from(v.vector.zip(t.vector).map(x => x._1 == x._2)))
        case (Dyn.Bool(v), Dyn.Bool(t))     => Dyn.Bool(Vec.from(v.vector.zip(t.vector).map(x => x._1 == x._2)))
        case (Dyn.Double(v), Dyn.Double(t)) => Dyn.Bool(Vec.from(v.vector.zip(t.vector).map(x => x._1 == x._2)))
        case (a, b) =>
          throw new RuntimeException(
            s"cannot compare vectors of different types: ${a.productPrefix} vs ${b.productPrefix}"
          )
      }
  }

  object Dyn {
    case class String(vec: Vec[java.lang.String]) extends Dyn {
      override def length: scala.Int                       = vec.vector.length
      override def toIndexedSeq: IndexedSeq[java.lang.String] = vec.vector
    }
    case class Int(vec: Vec[scala.Int]) extends Dyn {
      override def length: scala.Int                       = vec.vector.length
      override def toIndexedSeq: IndexedSeq[java.lang.String] = vec.vector.map(_.toString)
    }

    case class Bool(vec: Vec[scala.Boolean]) extends Dyn {
      override def length: scala.Int                       = vec.vector.length
      override def toIndexedSeq: IndexedSeq[java.lang.String] = vec.vector.map(_.toString)
    }

    case class Double(vec: Vec[scala.Double]) extends Dyn {
      override def length: scala.Int                       = vec.vector.length
      override def toIndexedSeq: IndexedSeq[java.lang.String] = vec.vector.map(_.toString)
    }
  }

  case class Table(columns: List[(String, Dyn)]) extends AnyVal { self =>
    def numRows: Int = columns.head._2.length
    def numColumns: Int = columns.length

    def toJSArrayWithHeader: js.Array[js.Array[js.Any]] = {
      val header = js.Array(columns.map(c => js.Any.fromString(c._1)):_*)
      val body = js.Array(columns.map(c => js.Array(c._2.toStringArray.map(js.Any.fromString):_*)):_*)
      val rows = body.transpose
      rows.unshift(header)
      rows
    }


    def equalsTo(other: Table): Table = {
      val a = columns.map(_._1)
      val b = other.columns.map(_._1)
      if (columns.map(_._1) != other.columns.map(_._1)) {
        throw new RuntimeException(
          s"cannot compare tables with different columns: ${a} vs ${b}"
        )
      } else {
        Table.from(columns.zip(other.columns).map { case ((name, a), (_, b)) =>
          name -> a.equalsTo(b)
        })
      }
    }
  }
  object Table {
    def from(columns: List[(String, Dyn)]): Table = {
      assert(columns.map(_._2.length).distinct.length == 1)
      Table(columns)
    }

    def fromJSArrayWithHeader(values: js.Array[js.Array[String]]): Table = {
      val header = values(0).map(_.toString).toList
      val columns = values.jsSlice(1).transpose.zip(header).map { case (arr, name) =>
        name -> Vec.Dyn.String(Vec.from(arr.map(_.toString)))
      }
      Table.from(columns.toList)
    }
  }

}
