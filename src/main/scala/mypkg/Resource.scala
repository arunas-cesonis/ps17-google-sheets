package mypkg


case class Resource(name: String, itemName: String)

object Resource {
  val products = Resource("products", "product")
  val stockAvailables = Resource("stock_availables", "stock_available")
  val orders = Resource("orders", "order")
  val byName: Map[String, Resource] = List(
    products,
    stockAvailables,
    orders
  ).map(r => r.name -> r).toMap
}

