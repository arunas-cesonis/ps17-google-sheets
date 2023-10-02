package mypkg

import mypkg.Result.Result
import mypkg.Utils.fromOption
import mypkg.googleappscript.UrlFetchApp

import scala.scalajs.js
import scala.scalajs.js.UndefOr

trait Http {
  def request(
    method: Http.Method,
    host: String,
    path: String,
    queryParams: Map[String, String],
    body: Option[String]
  ): Result[String]
}

object Http {
  sealed trait Method extends Product with Serializable

  object Method {
    case object Put extends Method
    case object Get extends Method
  }
  private def makeUrl(host: String, path: String, queryParams: Map[String, String]): String = {
    import scala.scalajs.js.URIUtils.encodeURIComponent
    val sep = if (path.startsWith("/")) { "" } else { "/" }
    host + sep + path + "?" + queryParams
      .map(pair => encodeURIComponent(pair._1) + "=" + encodeURIComponent(pair._2))
      .mkString("&")
  }

  def implNodeSyncRequest: Http = new Http {
    override def request(
      method: Method,
      host: String,
      path: String,
      queryParams: Map[String, String],
      bodyOption: Option[String]
    ): Result[String] = {
      import scalajs.js.Dynamic.{global => g}
      val url     = makeUrl(host, path, queryParams)
      val request = g.require("sync-request");
      trait Options extends js.Object {
        val body: js.UndefOr[String]
      }
      println(s"${method.productPrefix} ${url}")
      method match {
        case Method.Put =>
          val res = request(
            "PUT",
            url,
            new Options {
              override val body: UndefOr[String] = fromOption(bodyOption)
            }
          )
          Result.ok(res.getBody().toString)
        case Method.Get =>
          val res = request("GET", url)
          Result.ok(res.getBody().toString)

      }
    }
  }

  def implAppScript: Http = new Http {
    import googleappscript.URLFetchRequestOptions

    override def request(
      method: Method,
      host: String,
      path: String,
      queryParams: Map[String, String],
      body: Option[String]
    ): Result[String] = {
      val url = makeUrl(host, path, queryParams)
      val resp = {
        val options = method match {
          case Method.Put =>
            new URLFetchRequestOptions {
              override val method: js.UndefOr[String]           = "PUT"
              override val muteHttpExceptions: UndefOr[Boolean] = true
              override val contentType: UndefOr[String]         = "text/xml"
              override val payload: UndefOr[String]             = fromOption(body)
            }
          case Method.Get =>
            new URLFetchRequestOptions {
              override val method: js.UndefOr[String]           = "GET"
              override val muteHttpExceptions: UndefOr[Boolean] = true
            }
        }
        Utils.log(s"${options.method.toString.toUpperCase} ${url}")
        UrlFetchApp.fetch(url, options)
      }
      if (resp.getResponseCode() / 100 != 2) {
        val errorMsg = s"http code ${resp.getResponseCode()}:\n${resp.getContentText()}"
        Utils.log(errorMsg)
        Result.fail(errorMsg)
      } else {
        Result.ok(resp.getContentText())
      }
    }
  }

}
