package com.example

import akka.event.Logging.LogLevel
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.server.RouteResult.{ Complete, Rejected }
import akka.http.scaladsl.server.directives.{ DebuggingDirectives, LogEntry, LoggingMagnet }

// from https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/debugging-directives/logRequestResult.html#building-advanced-directives

trait HasLogDurationDirective {
  def akkaResponseTimeLoggingFunction(
    loggingAdapter: LoggingAdapter,
    requestTimestamp: Long,
    level: LogLevel = Logging.InfoLevel)(req: HttpRequest)(res: RouteResult): Unit = {
    val entry = res match {
      case Complete(resp) =>
        val responseTimestamp: Long = System.nanoTime
        val elapsedTime: Long = (responseTimestamp - requestTimestamp) / 1000000
        val loggingString = s"""Logged Request:${req.method}:${req.uri}:${resp.status}: took $elapsedTime milliseconds"""
        LogEntry(loggingString, level)
      case Rejected(reason) =>
        LogEntry(s"Rejected Reason: ${reason.mkString(",")}", level)
    }
    entry.logTo(loggingAdapter)
  }
  def printResponseTime(log: LoggingAdapter): HttpRequest => RouteResult => Unit = {
    val requestTimestamp = System.nanoTime
    akkaResponseTimeLoggingFunction(log, requestTimestamp)(_)
  }

  val logDuration = DebuggingDirectives.logRequestResult(LoggingMagnet(printResponseTime))
}
