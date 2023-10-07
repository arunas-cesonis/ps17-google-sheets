package mypkg

import mypkg.Http.Method
import mypkg.Result.Result
import mypkg.Utils.log
import mypkg.Xml.Document

trait Api {
  def fetchResourceSchema(resource: String): Result[Schema]
  def fetchResource(resource: String): Result[Document]
  def sendResource(resource: String, doc: Document): Result[Document]
  def getAvailableResources: Result[List[String]]
}

object Api {

  def create(config: Config, http: Http): Api =
    new Api {

      def getAvailableResources: Result[List[String]] = {
        val params = Params.empty
          .add(Params.WsKey(config.key))
          .add(Params.Schema.Synopsis)
        val resp = http.request(Method.Get, config.host, "/api", params.map, None).toTry.get
        val doc  = Xml.parse(resp)
        Result.ok(doc.root.getElement("api").get.elements.map(_.name).toList)
      }

      override def fetchResourceSchema(resource: String): Result[Schema] = {
        log(s"fetchResourceSchema($resource)")
        val params = Params.empty
          .add(Params.WsKey(config.key))
          .add(Params.Schema.Synopsis)
        log(s"fetchResourceSchema($resource): http.request")
        val resp = http.request(Method.Get, config.host, s"/api/${resource}", params.map, None).toTry.get
        log(s"fetchResourceSchema($resource): Xml.parse")
        val data = Xml.parse(resp)
        log(s"fetchResourceSchema($resource): Schema.from")
        Schema.from(resource, data)
      }

      override def fetchResource(resource: String): Result[Document] = {
        val params = Params.empty
          .add(Params.WsKey(config.key))
          .add(Params.Display.Full)
          .add(Params.Limit(5))
        val resp = http.request(Method.Get, config.host, s"/api/${resource}", params.map, None).toTry.get
        val data = Xml.parse(resp)
        Result.ok(data)
      }

      override def sendResource(resource: String, doc: Document): Result[Document] = {
        val params = Params.empty
          .add(Params.WsKey(config.key))
          .add(Params.Display.Full)
        val resp = http.request(Method.Put, config.host, s"/api/${resource}", params.map, Some(doc.print)).toTry.get
        val data = Xml.parse(resp)
        Result.ok(data)
      }
    }

}
