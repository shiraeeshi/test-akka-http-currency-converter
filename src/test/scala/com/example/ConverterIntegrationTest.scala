package com.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RequestEntity }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import org.scalatest.{ BeforeAndAfterAll, FunSuite }

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

class ConverterIntegrationTest extends FunSuite with BeforeAndAfterAll with JsonSupport {

  val system: ActorSystem = ActorSystem("ConverterIntegrationTest")
  var serverBinding: Option[Http.ServerBinding] = None

  override def beforeAll(): Unit = {
    implicit val implicitSystem: ActorSystem = system
    implicit val implicitMaterializer: ActorMaterializer = ActorMaterializer()
    implicit val implicitExecutionContext: ExecutionContext = system.dispatcher

    val converterRoutesInstance = new ConverterRoutes {
      override implicit def system: ActorSystem = implicitSystem
      override implicit def materializer: ActorMaterializer = implicitMaterializer
      override implicit def executionContext: ExecutionContext = implicitExecutionContext
    }

    val serverBindingFuture: Future[Http.ServerBinding] =
      Http().bindAndHandle(converterRoutesInstance.converterRoutes, "localhost", 8080)

    serverBindingFuture.onComplete {
      case Success(bound) =>
        println(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
        serverBinding = Some(bound)
      case Failure(e) =>
        Console.err.println(s"Server could not start!")
        e.printStackTrace()
        system.terminate()
    }

  }

  override def afterAll(): Unit = {

    implicit val executionContext: ExecutionContext = system.dispatcher

    serverBinding match {
      case Some(bound) =>
        bound.terminate(1.second)
        bound.whenTerminated onComplete { _ =>
          system.terminate()
        }
      case None =>
        system.terminate()
    }
    system.whenTerminated onComplete { _ =>
      println("system terminated")
    }
  }

  test("Main integration test") {
    implicit val systemInTest: ActorSystem = ActorSystem("ConverterIntegrationTest")
    implicit val materializerInTest: ActorMaterializer = ActorMaterializer()
    implicit val executionContextInTest: ExecutionContext = systemInTest.dispatcher

    val input = ConverterInput(data = List(ConverterRequest(currencyFrom = "RUB", currencyTo = "USD", valueFrom = 100)))

    val responseFuture = Marshal(input).to[RequestEntity] flatMap { entity =>
      Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = "http://localhost:8080/converter",
          entity = entity))
    }

    val responseEntityFuture = responseFuture flatMap { response =>
      Unmarshal(response.entity).to[ConverterOutput]
    }
    val output: ConverterOutput = Await.result(responseEntityFuture, 15.seconds)

    assert(output.data.size == 1)
    assert(output.errorCode == 0)
    assert(output.errorMessage == "")
  }

}
