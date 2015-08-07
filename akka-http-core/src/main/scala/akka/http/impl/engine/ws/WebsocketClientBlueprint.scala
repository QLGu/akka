/*
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.impl.engine.ws

import java.security.SecureRandom
import java.util.Random

import akka.http.impl.engine.rendering.{ HttpRequestRendererFactory, RequestRenderingContext }
import akka.http.scaladsl.model.headers.Host
import akka.stream.scaladsl.FlexiMerge.{ Read, MergeLogic }
import akka.util.ByteString
import akka.event.LoggingAdapter

import scala.concurrent.Promise

import akka.stream.BidiShape
import akka.stream.io.{ SessionBytes, SendBytes, SslTlsInbound }
import akka.stream._
import akka.stream.scaladsl._

import akka.http.ClientConnectionSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.{ TextMessage, Message }

trait OneTimeValve {
  def source[T]: Source[T, Unit]
  def open(): Unit
}
object OneTimeValve {
  def apply(): OneTimeValve = new OneTimeValve {
    val promise = Promise[Unit]()
    val _source = Source(promise.future).drop(1) // we are only interested in the completion event

    def source[T]: Source[T, Unit] = _source.asInstanceOf[Source[T, Unit]] // safe, because source won't generate any elements
    def open(): Unit = promise.success(())
  }
}

object WebsocketClientBlueprint {
  def apply(uri: Uri,
            settings: ClientConnectionSettings,
            random: Random,
            log: LoggingAdapter): Http.WebsocketClientLayer = {
    val (initialRequest, key) = Handshake.Client.buildRequest(uri, Nil, random)
    val hostHeader = Host(uri.authority)
    val renderedInitialRequest =
      HttpRequestRendererFactory.renderStrict(RequestRenderingContext(initialRequest, hostHeader), settings, log)

    BidiFlow() { implicit b ⇒
      import FlowGraph.Implicits._

      val renderMessage = b.add(Flow[Message].map(_ ⇒ ByteString.empty))
      val wrapTls = b.add(Flow[ByteString].map(SendBytes))

      val handshakeRequestSource = b.add(Source.single(renderedInitialRequest) /* FIXME: ++ valve to delay ws frames*/ )
      val httpRequestBytesAndThenWSBytes = b.add(Concat[ByteString]())

      handshakeRequestSource ~> httpRequestBytesAndThenWSBytes
      renderMessage ~> httpRequestBytesAndThenWSBytes

      httpRequestBytesAndThenWSBytes ~> wrapTls

      val unwrapTls = b.add(Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) ⇒ bytes })
      val parseMessage = b.add(Flow[ByteString].map(_ ⇒ TextMessage("test")))

      unwrapTls ~> parseMessage

      BidiShape(
        renderMessage.inlet,
        wrapTls.outlet,
        unwrapTls.inlet,
        parseMessage.outlet)
    }
  }
}
