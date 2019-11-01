package com.gitlab.kzs.zioworkshop

import com.gitlab.kzs.zioworkshop.dao.StockDAO
import com.gitlab.kzs.zioworkshop.model.{Stock, StockDBAccessError, StockError, StockNotFound}
import com.gitlab.kzs.zioworkshop.stream.FileStream
import org.http4s._
import org.http4s.syntax.kleisli._
import org.specs2.mutable.Specification
import zio.clock.Clock
import zio.internal.PlatformLive
import zio.interop.catz._
import zio.{IO, Runtime, Task}
import fs2.Stream

class StockSpec extends Specification {

  object ExtServicesTest extends ExtServices with Clock.Live {

    override val stockDAO: StockDAO = new StockDAO {

      override def findStock(stockId: Int): IO[StockError, Stock] =
      // A mocking framework could have been used as well
        stockId match {
          case 1 => IO.succeed(Stock(1, 10))
          case 2 => IO.succeed(Stock(2, 15))
          case 3 => IO.succeed(Stock(3, 0))
          case 99 => IO.fromEither(Left(StockDBAccessError(new Exception("Internal server error occurred"))))
          case _ => IO.fromEither(Left(StockNotFound))
        }
      override def findAllStocks: fs2.Stream[Task, Stock] = Stream(
        Stock(1, 10),
        Stock(2, 15),
        Stock(3, 0),
      )
      override def updateStock(stockId: Int, increment: Int): IO[StockError, Stock] =
        findStock(stockId).map { stock => stock.copy(value = stock.value + increment) }
    }

    override val fileStream: FileStream = new FileStream {
      override def salesFromFile: Stream[STask, Stock] = Stream(
        Stock(4, 20),
        Stock(5, 25),
      )
    }
  }

  val testRuntime: Runtime[ExtServicesTest.type] = Runtime(ExtServicesTest, PlatformLive.Default)

  "Stock HTTP Service" should {

    "respond 200 with the given stock" in {
      val request = Request[STask] (Method.GET, uri"""/stocks/1""")
      val stockResponse = testRuntime.unsafeRun(HTTPService.routes.orNotFound.run(request))
      stockResponse.status must beEqualTo(Status.Ok)
      testRuntime.unsafeRun(stockResponse.as[String]) must beEqualTo("""{"id":1,"value":10}""")
    }
  }
}
