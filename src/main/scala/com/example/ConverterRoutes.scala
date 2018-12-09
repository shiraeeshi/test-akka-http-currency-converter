package com.example

import akka.actor.ActorSystem
import akka.event.Logging

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal

import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.ActorMaterializer
import cats.implicits._
import spray.json._

trait ConverterRoutes extends JsonSupport with HasLogDurationDirective {

  implicit def system: ActorSystem
  implicit def executionContext: ExecutionContext
  implicit def materializer: ActorMaterializer

  lazy val log = Logging(system, classOf[ConverterRoutes])

  lazy val converterRoutes: Route =
    logDuration {
      pathPrefix("converter") {
        post {
          entity(as[ConverterInput]) { input =>
            val outputFuture: Future[ConverterOutput] = handleConverterInput(input)
            complete(outputFuture)
          }
        }
      }
    }

  def handleConverterInput(input: ConverterInput): Future[ConverterOutput] = {
    log.info(s"input: ${input.toJson}")

    val requestsByCurrencyFrom: Map[String, List[ConverterRequest]] =
      input.data.groupBy(_.currencyFrom)

    val converterResponsesFuture: Future[List[Either[String, ConverterResponse]]] =
      convert(requestsByCurrencyFrom)

    val outputFuture: Future[ConverterOutput] =
      createConverterOutput(converterResponsesFuture)

    outputFuture
  }

  private def convert(requestsByCurrencyFrom: Map[String, List[ConverterRequest]]): Future[List[Either[String, ConverterResponse]]] = {

    val futures: List[Future[Either[String, ConverterResponse]]] = requestsByCurrencyFrom.toList flatMap {
      case (currencyFrom, converterRequests) =>

        val requestToApi = HttpRequest(uri = s"https://api.exchangeratesapi.io/latest?base=$currencyFrom")

        val futureRates: Future[Either[String, Map[String, Double]]] = for {
          res <- Http().singleRequest(requestToApi)
          apiResponse <- Unmarshal(res.entity).to[ExchangeRatesApiResponse]
        } yield apiResponse.rates match {
          case Some(rates) =>
            Right(rates)
          case None =>
            Left(apiResponse.error.getOrElse(""))
        }

        val futureConverterResponses: List[Future[Either[String, ConverterResponse]]] =
          converterRequests map { converterRequest =>
            futureRates map { eitherRates =>
              eitherRates map { rates =>
                val rate = rates(converterRequest.currencyTo)
                val value = rate * converterRequest.valueFrom
                ConverterResponse(converterRequest, truncate2digits(value))
              }
            }
          }

        futureConverterResponses
    }

    futures.sequence
  }

  private def createConverterOutput(converterResponsesFuture: Future[List[Either[String, ConverterResponse]]]): Future[ConverterOutput] =
    converterResponsesFuture map { converterResponses =>

      val zero = ConverterOutput(data = Nil, errorCode = 0, errorMessage = "")

      val output = converterResponses.foldLeft(zero) {
        case (_, Left(msg)) =>
          ConverterOutput(Nil, 1, msg)
        case (ConverterOutput(responses, errorCode, msg), Right(converterResponse)) if errorCode == 0 && msg == "" =>
          ConverterOutput(converterResponse :: responses, 0, "")
        case (ConverterOutput(_, errorCode, msg), _) =>
          ConverterOutput(Nil, errorCode, msg)
      }

      log.info(s"output: ${output.toJson}")
      output
    }

  private def truncate2digits(value: Double) = {
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

}
