package com.gitlab.kzs.zioworkshop

import com.gitlab.kzs.zioworkshop.dao.{StockDAO, StockDAOLive}
import com.gitlab.kzs.zioworkshop.model.{EmptyStock, Stock, StockNotFound}
import doobie.util.transactor.Transactor
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.slf4j.LoggerFactory._
import zio.interop.catz._
import zio.{IO, Task, ZIO}

/**
  * HTTP route definitions.
  */
class HTTPService(dao: StockDAO) extends Http4sDsl[Task] {

  val logger = getLogger(this.getClass)

  val routes: HttpRoutes[Task] = HttpRoutes.of[Task] {

    case GET -> Root / "stock" / IntVar(stockId) =>
      stockOrErrorResponse(for {
        stock <- dao.currentStock(stockId)
        stockDbResult <- IO.fromEither(Stock.validate(stock))
      } yield stockDbResult)

      stockOrErrorResponse(stockDbResult)
  }

  def stockOrErrorResponse(stockResponse: Task[Stock]): Task[Response[Task]] = {
    stockResponse.foldM({ // map is to fold what flatMap is foldM
      case StockNotFound => NotFound(Json.obj("Error" -> Json.fromString("Stock not found")))
      case EmptyStock => Conflict(Json.obj("Error" -> Json.fromString("Empty stock")))
    }, stock => Ok(stock.asJson))
  }

}

object Server extends CatsApp {

  import zio.interop.catz.implicits._

  val xa = Transactor.fromDriverManager[Task] (
    "org.h2.Driver",
    "jdbc:h2:mem:poc;INIT=RUNSCRIPT FROM 'src/main/resources/sql/create.sql'"
    , "sa", ""
  )

  // Runtime will execute IO unsafe calls (i.e. all the side effects) and manage threading
  val program = ZIO.runtime[Environment].flatMap { implicit runtime =>
    // Start the server
    BlazeServerBuilder[Task]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(new HTTPService(new StockDAOLive(xa)).routes.orNotFound)
      .serve
      .compile.drain
  }

  // Plug the real service
  override def run(args: List[String]) = program.fold(_ => 1, _ => 0)

}
