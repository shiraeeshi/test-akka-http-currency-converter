package com.example

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val requestJsonFormat = jsonFormat3(ConverterRequest)
  implicit object ConverterResponseFormat extends JsonFormat[ConverterResponse] {
    override def read(json: JsValue): ConverterResponse = {
      val fields = json.asJsObject("ConverterResponse object expected").fields
      ConverterResponse(
        ConverterRequest(
          fields("currencyFrom").convertTo[String],
          fields("currencyTo").convertTo[String],
          fields("valueFrom").convertTo[Double]),
        fields("valueTo").convertTo[Double])
    }

    override def write(res: ConverterResponse): JsValue = JsObject(
      "currencyFrom" -> res.request.currencyFrom.toJson,
      "currencyTo" -> res.request.currencyTo.toJson,
      "valueFrom" -> res.request.valueFrom.toJson,
      "valueTo" -> res.value.toJson)
  }
  implicit val inputJsonFormat = jsonFormat1(ConverterInput)
  implicit val outputJsonFormat = jsonFormat3(ConverterOutput)
  implicit val exchangeRatesApiResponseFormat = jsonFormat2(ExchangeRatesApiResponse)

}
