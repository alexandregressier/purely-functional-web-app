package com.gitlab.kzs.zioworkshop

import com.gitlab.kzs.zioworkshop.model.{EmptyStock, Stock, StockError, StockNotFound}
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
import zio.{IO, TaskR, ZIO}

/** HTTP route definitions.
  */
object HTTPService extends Http4sDsl[STask] {

  val logger = getLogger(this.getClass)

  val stockDAO = ZIO.access[ExtServices](_.stockDAO) // Dependency injection

  val routes: HttpRoutes[STask] = HttpRoutes.of[STask] {

    case GET -> Root / "stock" / IntVar(stockId) =>
      // `for` comprehensions are successive flatMaps
      val stockDbResult: ZIO[ExtServices, StockError, Stock] = for {
        dao <- stockDAO
        stock <- dao.findStock(stockId)
        rs <- IO.fromEither(Stock.validate(stock))
      } yield rs

      stockOrErrorResponse(stockDbResult)

    case PUT -> Root / "stock" / IntVar(stockId) / IntVar(increment) =>
      stockOrErrorResponse(stockDAO.flatMap(_.updateStock(stockId, increment)))
  }

  def stockOrErrorResponse(stockResponse: ZIO[ExtServices, StockError, Stock]): TaskR[ExtServices, Response[STask]] = {
    stockResponse.foldM({ // map is to fold what flatMap is foldM
      case StockNotFound => NotFound(Json.obj("Error" -> Json.fromString("Stock not found")))
      case EmptyStock => Conflict(Json.obj("Error" -> Json.fromString("Empty stock")))
      case stockError =>
        IO(logger.error(stockError.getMessage))
          .flatMap(_ => InternalServerError(Json.obj("Error" -> Json.fromString(stockError.getMessage))))
    }, stock => Ok(stock.asJson))
  }
}

object Server extends CatsApp {

  // Runtime will execute IO unsafe calls (i.e. all the side effects) and manage threading
  val program = ZIO.runtime[ExtServices].flatMap { implicit runtime =>
    // Start the server
    BlazeServerBuilder[STask]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(HTTPService.routes.orNotFound)
      .serve
      .compile.drain
  }

  // Plug the real service
  override def run(args: List[String]) = program.provide(ExtServicesLive).fold(_ => 1, _ => 0)
}
