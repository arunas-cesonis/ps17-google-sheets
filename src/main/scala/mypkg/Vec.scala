package mypkg

case class Vec[A](vector: Vector[A]) extends AnyVal

object Vec {
  def empty[A]: Vec[A]                 = Vec(Vector.empty[A])
  def apply[A](as: A*): Vec[A]         = Vec(as.toVector)
  def from[A](as: Iterable[A]): Vec[A] = Vec(as.toVector)
  def from[A](as: Vector[A]): Vec[A]   = Vec(as)

  sealed trait Dyn extends Product with Serializable {
    def length: Int
    def toIndexedSeq: IndexedSeq[String]
    def toStringArray: Array[String] = toIndexedSeq.toArray
  }
  object Dyn {
    case class String(vec: Vec[java.lang.String]) extends Dyn {
      override def length: scala.Int                       = vec.vector.length
      override def toIndexedSeq: IndexedSeq[Predef.String] = vec.vector
    }
    case class Int(vec: Vec[scala.Int]) extends Dyn {
      override def length: scala.Int                       = vec.vector.length
      override def toIndexedSeq: IndexedSeq[Predef.String] = vec.vector.map(_.toString)
    }
    case class Double(vec: Vec[scala.Double]) extends Dyn {
      override def length: scala.Int                       = vec.vector.length
      override def toIndexedSeq: IndexedSeq[Predef.String] = vec.vector.map(_.toString)
    }
  }

  case class Table(columns: List[(String, Dyn)]) extends AnyVal
  object Table {
    def from(columns: List[(String, Dyn)]): Table = {
      assert(columns.map(_._2.length).distinct.length == 1)
      Table(columns)
    }
  }

}
