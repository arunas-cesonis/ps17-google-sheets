package mypkg

object Result {
  type Result[A] = Either[Error, A]
  abstract class Error(val msg: String) extends RuntimeException(msg)

  def error(msg: String): Error = {
    new Error(msg) {}
  }

  def ok[A](a: A): Result[A] = Right(a)

  def partialOr[A,B](a:A, msg: => String)(f: PartialFunction[A,B]): Either[Error, B] = {
    if (f.isDefinedAt(a)) {
      ok(f(a))
    } else {
      fail(msg)
    }
  }

  def ensuring[A](a: A)(cond: A => Boolean, msg: => String): Result[A] =
    if (cond(a)) {
      ok(a)
    } else {
      fail(msg)
    }

  def fail[A](msg: String): Result[A] = (Left(error(msg)): Either[Error, A])
}
