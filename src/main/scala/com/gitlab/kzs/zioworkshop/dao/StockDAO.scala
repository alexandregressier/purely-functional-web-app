package com.gitlab.kzs.zioworkshop.dao

import com.gitlab.kzs.zioworkshop.IOTransactor
import com.gitlab.kzs.zioworkshop.model.{Stock, StockDBAccessError, StockError, StockNotFound}
import doobie.implicits._
import zio.Task
import zio.interop.catz._
import zio.IO

trait StockDAO {
  def currentStock(stockId: Int): IO[StockError, Stock]
  def updateStock(stockId: Int, increment: Int): IO[StockError, Stock]
}

/** The methods in this class are pure functions
  * They can describe how to interact with the database (select, insert, ...)
  * But as IO is lazy, no side effect will be executed here
  *
  * @param xa
  */
class StockDAOLive(val xa: IOTransactor) extends StockDAO {

  override def currentStock(stockId: Int): IO[StockError, Stock] = {
    val stockDatabaseResult = sql"""
      SELECT * FROM stock where id=$stockId
     """.query[Stock].option

    stockDatabaseResult.transact(xa).mapError(StockDBAccessError)
      .flatMap { // Task[Option[Stock]] -> Task[Stock]
        case Some(stock) => IO.succeed(stock)
        case None => IO.fail(StockNotFound)
      }
  }

  override def updateStock(stockId: Int, increment: Int): IO[StockError, Stock] = {
    val newStockDatabaseResult = for {
      _ <- sql"""UPDATE stock SET value = value + $increment WHERE id=$stockId""".update.run
      newStock <- sql"""SELECT * FROM stock where id=$stockId""".query[Stock].unique
    } yield newStock

    newStockDatabaseResult.transact(xa).mapError(StockDBAccessError)
  }
}
