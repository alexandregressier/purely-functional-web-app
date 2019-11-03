package com.thisisscala.purelyfunctionalwebapp.stream

import java.nio.file.Paths

import cats.effect.Resource
import com.thisisscala.purelyfunctionalwebapp.STask
import com.thisisscala.purelyfunctionalwebapp.model.Stock
import fs2.{Stream, io, text}
import zio.Task
import zio.interop.catz._

import scala.concurrent.ExecutionContextExecutorService

/** File stream.
  */
trait FileStream {
  def salesFromFile: Stream[STask, Stock]
}

/** File stream live implementation.
  *
  * Content could have been streamed for an external web service as well.
  *
  * @param path the content root path of the file to read as a stream.
  * @param blockingExecContext the blocking execution context to read the file in.
  */
class FileStreamLive(path: String,
                     blockingExecContext: Resource[Task, ExecutionContextExecutorService]) extends FileStream {

  override def salesFromFile: Stream[STask, Stock] = {
    Stream.resource(blockingExecContext).flatMap { blockingExecContext =>
      io.file.readAll[STask] (Paths.get(path), blockingExecContext, 4096)
        .through(text.utf8Decode)
        .through(text.lines)
        .filter(s => !s.trim.isEmpty && !s.startsWith("//"))
        .map {
          line => line.split(",").map(_.trim) match {
            case Array(id, value) => Stock(id.toInt, value.toInt)
          }
        }
    }
  }
}
