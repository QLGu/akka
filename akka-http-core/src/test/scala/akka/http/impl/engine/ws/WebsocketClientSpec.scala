/*
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.impl.engine.ws

import java.util.Random

import akka.http.ClientConnectionSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{ ProductVersion, `User-Agent` }
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.Uri
import akka.stream.io.{ SendBytes, SslTlsOutbound, SessionBytes }
import akka.stream.scaladsl.{ Sink, Flow, Source, FlowGraph }
import akka.stream.testkit.{ TestSubscriber, TestPublisher }
import akka.util.ByteString
import org.scalatest.{ Matchers, FreeSpec }

import akka.http.impl.util._

class WebsocketClientSpec extends FreeSpec with Matchers with WithMaterializerSpec {
  "The client-side Websocket implementation should" - {
    "establish a websocket connection when the user requests it" in new TestSetup {
      expectWireData(
        """GET /ws HTTP/1.1
          |Upgrade: websocket
          |Connection: upgrade
          |Sec-WebSocket-Key: YLQguzhR2dR6y5M9vnA5mw==
          |Sec-WebSocket-Version: 13
          |Host: example.org
          |User-Agent: akka-http/test
          |
          |""".stripMarginWithNewline("\r\n"))

      sendWireData(
        """HTTP/1.1 101 Switching Protocols
          |Upgrade: websocket
          |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
          |Server: akka-http/test
          |Date: XXXX
          |Connection: upgrade
          |
          |""")
    }

    "don't send out websocket frames before handshake was finished successfully" in {}
    "parse a single chunk"
  }

  class TestSetup {
    val messagesOut = TestPublisher.manualProbe[Message]()
    val messagesIn = TestSubscriber.manualProbe[Message]()

    val random = new Random(0)
    def settings = ClientConnectionSettings(system)
      .copy(userAgentHeader = Some(`User-Agent`(List(ProductVersion("akka-http", "test")))))

    def targetUri: Uri = "ws://example.org/ws"

    def clientLayer: Http.WebsocketClientLayer =
      Http(system).websocketClientLayer(targetUri, settings, random)

    val (netOut, netIn) = {
      val netOut = TestSubscriber.manualProbe[ByteString]
      val netIn = TestPublisher.manualProbe[ByteString]()

      FlowGraph.closed(clientLayer) { implicit b ⇒
        client ⇒
          import FlowGraph.Implicits._
          Source(netIn) ~> Flow[ByteString].map(SessionBytes(null, _)) ~> client.in2
          client.out1 ~> Flow[SslTlsOutbound].collect { case SendBytes(x) ⇒ x } ~> Sink(netOut)
          Source(messagesOut) ~> client.in1
          client.out2 ~> Sink(messagesIn)
      }.run()

      netOut -> netIn
    }

    def wipeDate(string: String) =
      string.fastSplit('\n').map {
        case s if s.startsWith("Date:") ⇒ "Date: XXXX\r"
        case s                          ⇒ s
      }.mkString("\n")

    val netInSub = netIn.expectSubscription()
    val netOutSub = netOut.expectSubscription()
    val messagesOutSub = messagesOut.expectSubscription()
    val messagesInSub = messagesIn.expectSubscription()

    def sendWireData(data: String): Unit = sendWireData(ByteString(data.stripMarginWithNewline("\r\n"), "ASCII"))
    def sendWireData(data: ByteString): Unit = netInSub.sendNext(data)

    def expectWireData(s: String) = {
      netOutSub.request(1)
      netOut.expectNext().utf8String shouldEqual s.stripMarginWithNewline("\r\n")
    }

    def closeNetworkInput(): Unit = netInSub.sendComplete()
  }
}
