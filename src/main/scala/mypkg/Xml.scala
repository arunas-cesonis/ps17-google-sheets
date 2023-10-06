package mypkg

import cats.Eq
import cats.implicits.catsSyntaxEq
import mypkg.Result.Result

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSImport}

object Xml {
  private def escapeString(str: String): String =
    str
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  case class Document(root: Element) {
    def print: String = {
      val sb    = new StringBuilder()
      val stack = scala.collection.mutable.Stack.empty[Either[String, Content]]
      stack.push(Right(root))
      while (stack.nonEmpty)
        stack.pop() match {
          case Left(value)     => sb.addAll(s"</${value}>")
          case Right(Chars(s)) => sb.addAll(s)
          case Right(Cdata(s)) => sb.addAll("<![CDATA[").addAll(s).addAll("]]>")
          case Right(Element(name, attributes, content)) =>
            sb.addAll(s"<${name}")
            for ((k, v) <- attributes) {
              val vstr = "\"" + escapeString(v) + "\""
              sb.addAll(s" ${k}=${vstr}")
            }
            sb.addOne('>')
            stack.push(Left(name))
            stack.pushAll(content.map(Right(_)).reverse)
        }
      sb.toString()
    }
  }
  object Document {

    implicit val documentEq: Eq[Document] = new Eq[Document] {
      override def eqv(x: Document, y: Document): Boolean =
        Content.contentEq.eqv(x.root, y.root)
    }
  }
  sealed trait Content        extends Serializable with Product
  case class Chars(s: String) extends Content
  case class Cdata(s: String) extends Content
  case class Element(name: String, attributes: js.Array[(String, String)], content: js.Array[Content]) extends Content {
    def debug: String = {
      def go(e: Element, path: List[String]): List[String] =
        List(
          path.mkString(".") + " " + e.name + " " + e.attributes.map(kv => kv._1 + "=" + kv._2).mkString(" ")
        ) ++ e.elements.flatMap(x => go(x, path :+ e.name))
      go(this, Nil).mkString("\n")
    }
    def text: String = content.collect {
      case c: Chars  => c.s
      case cd: Cdata => cd.s
    }.mkString

    def getElementR(name: String): Result[Element] =
      getElement(name).toRight(Result.error(s"element '${name}' not found"))

    def getElement(name: String): Option[Element] = content.collectFirst {
      case e: Element if e.name == name => e
    }

    def singleElement: Option[Element] = elements.toList match {
      case single :: Nil => Some(single)
      case _             => None
    }

    def singleElementByName(name: String): Option[Element] = elements.toList.filter(_.name == name) match {
      case single :: Nil => Some(single)
      case _             => None
    }

    def getAttribute(name: String): Option[String] = attributes.find(_._1 == name).map(_._2)

    def getElements(name: String): js.Array[Element] = content.collect {
      case e: Element if e.name == name => e
    }
    def findMapElement[A](f: Element => Option[A]): Option[A] = {
      def go(e: Element): Option[A] =
        f(e).orElse(
          e.elements.collectFirst { case e: Element =>
            go(e)
          }.flatten
        )
      go(this)
    }

    def elements: Iterable[Element] = content.collect { case e: Element =>
      e
    }
  }
  object Element {
    def create(name: String): Element = Element(name, js.Array(), js.Array())
  }
  object Content {

    def arrayEq[A: Eq]: Eq[js.Array[A]] = new Eq[js.Array[A]] {
      override def eqv(x: js.Array[A], y: js.Array[A]): Boolean =
        x.length == y.length && x.zip(y).forall { case (a, b) =>
          Eq[A].eqv(a, b)
        }
    }
    implicit val contentEq: Eq[Content] = new Eq[Content] {
      override def eqv(x: Content, y: Content): Boolean =
        (x, y) match {
          case (Chars(a), Chars(b)) => a == b
          case (Cdata(a), Cdata(b)) => a == b
          case (Element(name, attrs, content), Element(name2, attrs2, content2)) =>
            name == name2 && attrs.length == attrs2.length && content.length == content2.length && arrayEq[
              (String, String)
            ].eqv(
              attrs,
              attrs2
            ) && arrayEq(contentEq).eqv(content, content2)
          case _ => false
        }
    }
  }

  def parse(s: String): Document = {
    var doc: Option[Document]      = None
    val warnings: js.Array[String] = js.Array()
    val stack                      = scala.collection.mutable.Stack.empty[Element]
    val parser = new Xml.SaxParser(r => {
      r.onStartElementNS { (name, attributes, _, _, _) =>
        val el = Element(name, attributes.map(a => (a(0), a(1))), js.Array())
        if (stack.nonEmpty) {
          stack.head.content.push(el)
        }
        stack.push(el)
      }
      r.onEndElementNS({ (name, _, _) =>
        val el = stack.pop()
        assert(el.name == name)
        if (stack.isEmpty) {
          doc = Some(Document(el))
        }
      })
      r.onCharacters(a => stack.headOption.foreach(_.content.push(Chars(a))))
      r.onCdata(a => stack.headOption.foreach(_.content.push(Cdata(a))))
      r.onWarning(a => warnings.push(a))
      r.onError(a => throw new RuntimeException("SaxParser: " + a))
    })
    parser.parseString(s)
    doc.get
  }

  trait SaxParserCallbacks extends js.Object {
    def onStartDocument(f: js.Function0[Unit]): Unit
    def onEndDocument(f: js.Function0[Unit]): Unit
    def onStartElementNS(f: js.Function5[String, js.Array[js.Array[String]], js.Any, js.Any, js.Any, Unit]): Unit
    def onEndElementNS(f: js.Function3[String, js.Any, js.Any, Unit]): Unit
    def onCharacters(f: js.Function1[String, Unit]): Unit
    def onCdata(f: js.Function1[String, Unit]): Unit
    def onWarning(f: js.Function1[String, Unit]): Unit
    def onError(f: js.Function1[String, Unit]): Unit
  }

  @js.native
  @JSImport("sax-parser", "SaxParser")
  class SaxParser(cb: js.Function1[SaxParserCallbacks, Unit]) extends js.Object {
    def parseString(input: String): Unit = js.native
  }

}
