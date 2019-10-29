package com.gitlab.kzs.zioworkshop

import com.gitlab.kzs.zioworkshop.dao.{StockDAO, StockDAOLive}
import doobie.util.transactor.Transactor
import zio.Task
import zio.clock.Clock
import zio.interop.catz._

trait ExtServices extends Clock {
  val stockDAO: StockDAO
}

object ExtServicesLive extends ExtServices with Clock.Live {

  val xa = Transactor.fromDriverManager[Task] (
    "org.h2.Driver",
    "jdbc:h2:mem:poc;INIT=RUNSCRIPT FROM 'src/main/resources/sql/create.sql'"
    , "sa", ""
  )

  override val stockDAO: StockDAO = new StockDAOLive(xa)
}
