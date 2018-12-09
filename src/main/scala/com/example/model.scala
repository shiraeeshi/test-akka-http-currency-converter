package com.example

case class ConverterInput(data: List[ConverterRequest])
case class ConverterOutput(data: List[ConverterResponse], errorCode: Int, errorMessage: String)
case class ConverterRequest(currencyFrom: String, currencyTo: String, valueFrom: Double)
case class ConverterResponse(request: ConverterRequest, value: Double)

case class ExchangeRatesApiResponse(rates: Option[Map[String, Double]], error: Option[String])
