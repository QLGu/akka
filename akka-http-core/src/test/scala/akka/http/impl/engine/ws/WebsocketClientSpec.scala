/*
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.impl.engine.ws

import java.util.Random

import akka.actor.ActorSystem
import akka.http.ClientConnectionSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{ ProductVersion, `User-Agent` }
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.stream.io._
import akka.stream.scaladsl._
import akka.stream.testkit.{ TestSubscriber, TestPublisher }
import akka.util.ByteString
import org.scalatest.{ Matchers, FreeSpec }

import akka.http.impl.util._

class WebsocketClientSpec extends FreeSpec with Matchers with WithMaterializerSpec {
  "The client-side Websocket implementation should" - {
    "establish a websocket connection when the user requests it" in new EstablishedConnectionSetup with ClientEchoes {

    }
    "reject invalid handshakes" - {
      "missing Sec-WebSocket-Accept hash" in {}
      "wrong Sec-WebSocket-Accept hash" in {}
      "missing `Upgrade` header" in {}
      "missing `Connection: upgrade` header" in {}
    }

    "don't send out websocket frames before handshake was finished successfully" in {}
    "receive first frame in same chunk as HTTP upgrade response" in new TestSetup with ClientProbes {
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

      val firstFrame = WSTestUtils.frame(Protocol.Opcode.Text, ByteString("fast"), fin = true, mask = false)
      sendWireData(ByteString(
        """HTTP/1.1 101 Switching Protocols
          |Upgrade: websocket
          |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
          |Server: akka-http/test
          |Connection: upgrade
          |
          |""".stripMarginWithNewline("\r\n")) ++ firstFrame)

      messagesInSub.request(1)
      messagesIn.expectNext(TextMessage("fast"))
    }

    "manual scenario client sends first" in new EstablishedConnectionSetup with ClientProbes {
      netOutSub.request(10)

      messagesOutSub.sendNext(TextMessage("Message 1"))

      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)

      sendWSFrame(Protocol.Opcode.Binary, ByteString("Response"), fin = true, mask = false)

      messagesInSub.request(1)
      messagesIn.expectNext(BinaryMessage(ByteString("Response")))
    }
    "client echoes scenario" in new EstablishedConnectionSetup with ClientEchoes {
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 2"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 2"), fin = true)
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 3"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 3"), fin = true)
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 4"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 4"), fin = true)
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 5"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 5"), fin = true)

      sendWSCloseFrame(Protocol.CloseCodes.Regular)
      expectMaskedCloseFrame(Protocol.CloseCodes.Regular)

      closeNetworkInput()
      expectNetworkClose()
    }
  }

  abstract class EstablishedConnectionSetup extends TestSetup {
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
        |Connection: upgrade
        |
        |""")
  }

  abstract class TestSetup extends WSTestSetupBase {
    def clientImplementation: Flow[Message, Message, Unit]

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
          client.out2 ~> clientImplementation ~> client.in1
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

    def expectNextChunk(): ByteString = {
      netOutSub.request(1)
      netOut.expectNext()
    }

    def sendWireData(data: String): Unit = sendWireData(ByteString(data.stripMarginWithNewline("\r\n"), "ASCII"))
    def sendWireData(data: ByteString): Unit = netInSub.sendNext(data)

    def send(bytes: ByteString): Unit = sendWireData(bytes)

    def expectWireData(s: String) = {
      netOutSub.request(1)
      netOut.expectNext().utf8String shouldEqual s.stripMarginWithNewline("\r\n")
    }

    def expectNetworkClose(): Unit = netOut.expectComplete()
    def closeNetworkInput(): Unit = netInSub.sendComplete()
  }

  trait ClientEchoes extends TestSetup {
    override def clientImplementation: Flow[Message, Message, Unit] = echoServer
    def echoServer: Flow[Message, Message, Unit] = Flow[Message]
  }
  trait ClientProbes extends TestSetup {
    lazy val messagesOut = TestPublisher.manualProbe[Message]()
    lazy val messagesIn = TestSubscriber.manualProbe[Message]()

    lazy val messagesOutSub = messagesOut.expectSubscription()
    lazy val messagesInSub = messagesIn.expectSubscription()

    override def clientImplementation: Flow[Message, Message, Unit] =
      Flow.wrap(Sink(messagesIn), Source(messagesOut))(Keep.none)
  }
}

object WebsocketClientTest extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  def runCase(caseId: Int): Unit = {
    val caseUri = Uri(s"ws://localhost:9001/runCase?case=$caseId&agent=akka-http")

    val tcp = Tcp().outgoingConnection("localhost", 9001)
    val ws = Http(system).websocketClientLayer(caseUri, ClientConnectionSettings(system))

    val wsClient = ws atop SslTlsPlacebo.forScala join tcp
    wsClient.join(Flow[Message].via(PPrintDebug.flow("user"))).run()
  }

  (1 to 1) foreach runCase
  import scala.concurrent.duration._
  import system.dispatcher
  system.scheduler.scheduleOnce(10.seconds)(system.shutdown())
}