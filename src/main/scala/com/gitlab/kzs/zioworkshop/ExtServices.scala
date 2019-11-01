package com.gitlab.kzs.zioworkshop

import java.util.concurrent.Executors

import cats.effect.Resource
import com.gitlab.kzs.zioworkshop.dao.{StockDAO, StockDAOLive}
import com.gitlab.kzs.zioworkshop.stream.{FileStream, FileStreamLive}
import doobie.util.transactor.Transactor
import zio.Task
import zio.clock.Clock
import zio.interop.catz._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

/** External services.
  */
trait ExtServices extends Clock {
  val stockDAO: StockDAO
  val fileStream: FileStream
}

/** The external services live implementation.
  */
object ExtServicesLive extends ExtServices with Clock.Live {

  val xa = Transactor.fromDriverManager[Task] (
    "org.h2.Driver",
    "jdbc:h2:mem:poc;INIT=RUNSCRIPT FROM 'src/main/resources/sql/create.sql'"
    , "sa", ""
  )
  val blockingExecContext: Resource[Task, ExecutionContextExecutorService] =
    Resource.make(Task(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2)))) {
      ec => Task(ec.shutdown())
    }
  override val stockDAO: StockDAO = new StockDAOLive(xa)
  override val fileStream: FileStream = new FileStreamLive(
    getClass.getResource("/specialCollection.txt").toURI,
    blockingExecContext
  )
}
