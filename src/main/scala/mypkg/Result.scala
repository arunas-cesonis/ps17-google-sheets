type Result[A] = Either[Error, A]
object Result {
  abstract class Error(val msg: String) extends RuntimeException(msg)

  def error(msg: String): Error = new Error(msg) {}

  def ok[A](a: A): Either[Error, A] = Right(a)

  def ensuring[A](a: A)(cond: A => Boolean, msg: => String): Either[Error, A] =
    if (cond(a)) {
      ok(a)
    } else {
      fail(msg)
    }

  def fail[A](msg: String): Either[Error, A] = Left(error(msg))
}