import Main.Result

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

  def implAppScript: Http = new Http {
    import scala.scalajs.js.URIUtils.encodeURIComponent
    override def request(
      method: Method,
      host: String,
      path: String,
      queryParams: Map[String, String],
      body: Option[String]
    ): Result[String] = {
      val url = host + "/" + path + "?" + queryParams
        .map(pair => encodeURIComponent(pair._1) + "=" + encodeURIComponent(pair._2))
        .mkString("&")
      val resp = {
        val options = method match {
          case HTTPMethod.Put =>
            new URLFetchRequestOptions {
              override val method: js.UndefOr[String]           = "PUT"
              override val muteHttpExceptions: UndefOr[Boolean] = true
              override val contentType: UndefOr[String]         = "text/xml"
              override val payload: UndefOr[String]             = fromOption(body)
            }
          case HTTPMethod.Get =>
            new URLFetchRequestOptions {
              override val method: js.UndefOr[String]           = "GET"
              override val muteHttpExceptions: UndefOr[Boolean] = true
            }
        }
        log(s"${options.method} ${url}")
        UrlFetchApp.fetch(url, options)
      }
      if (resp.getResponseCode() / 100 != 2) {
        log(resp.getContentText())
        fail(s"http code ${resp.getResponseCode()}")
      } else {
        ok(resp.getContentText())
      }
    }
  }

}
