package com.thisisscala

import doobie.util.transactor.Transactor.Aux
import zio.{Task, TaskR, ZIO}

package object purelyfunctionalwebapp {
  type IOTransactor = Aux[Task, Unit]
  type SIO[E, A] = ZIO[ExtServices, E, A]
  type STask[A] = TaskR[ExtServices, A]
}
