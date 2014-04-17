/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model

import language.implicitConversions
import java.io.File
import org.reactivestreams.api.Producer
import scala.collection.immutable
import akka.util.ByteString
import waves.{ Flow, StreamProducer }
import akka.actor.ActorRefFactory

/**
 * Models the entity (aka "body" or "content) of an HTTP message.
 */
sealed trait HttpEntity extends japi.HttpEntity {
  /**
   * Determines whether this entity is known to be empty.
   */
  def isKnownEmpty: Boolean

  /**
   * The `ContentType` associated with this entity.
   */
  def contentType: ContentType

  /**
   * A stream of the data of this entity.
   */
  def dataBytes(implicit refFactory: ActorRefFactory): Producer[ByteString]

  // Java API
  def getDataBytes(refFactory: ActorRefFactory): Producer[ByteString] = dataBytes(refFactory)
}

object HttpEntity {
  implicit def apply(string: String): Regular = apply(ContentTypes.`text/plain(UTF-8)`, string)
  implicit def apply(bytes: Array[Byte]): Regular = apply(ContentTypes.`application/octet-stream`, bytes)
  implicit def apply(data: ByteString): Regular = apply(ContentTypes.`application/octet-stream`, data)
  def apply(contentType: ContentType, string: String): Regular =
    if (string.isEmpty) empty(contentType) else apply(contentType, ByteString(string.getBytes(contentType.charset.nioCharset)))
  def apply(contentType: ContentType, bytes: Array[Byte]): Regular =
    if (bytes.length == 0) empty(contentType) else apply(contentType, ByteString(bytes))
  def apply(contentType: ContentType, data: ByteString): Regular =
    if (data.isEmpty) empty(contentType) else Default(contentType, data.length, StreamProducer.of(data))

  def apply(contentType: ContentType, file: File): Regular = {
    val fileLength = file.length
    if (fileLength > 0) Default(contentType, fileLength, StreamProducer.empty) // TODO: attach from-file-Producer
    else empty(contentType)
  }

  val Empty = Default(ContentTypes.`application/octet-stream`, contentLength = 0, data = StreamProducer.empty)

  def empty(contentType: ContentType): Default =
    if (contentType == Empty.contentType) Empty
    else Default(contentType, contentLength = 0, data = StreamProducer.empty)

  /**
   * An HttpEntity that is "well-behaved" according to the HTTP/1.1 spec as that
   * it is either chunked or defines a content-length that is known a-priori.
   * Close-delimited entities are not `Regular` as they exists primarily for backwards compatibility with HTTP/1.0.
   */
  sealed trait Regular extends HttpEntity with japi.HttpEntityRegular

  // TODO: re-establish serializability
  // TODO: equal/hashcode ?

  /**
   * The model for the entity of a "regular" unchunked HTTP message with a known length.
   */
  case class Default(contentType: ContentType,
                     contentLength: Long,
                     data: Producer[ByteString]) extends Regular with japi.HttpEntityDefault {
    require(contentLength >= 0, "contentLength must be non-negative")
    def isKnownEmpty = contentLength == 0

    def dataBytes(implicit refFactory: ActorRefFactory): Producer[ByteString] = data
  }

  /**
   * The model for the entity of an HTTP response that is terminated by the server closing the connection.
   * The content-length of such responses is unknown at the time the response headers have been received.
   * Note that this type of HttpEntity cannot be used for HttpRequests!
   */
  case class CloseDelimited(contentType: ContentType, data: Producer[ByteString]) extends HttpEntity with japi.HttpEntityCloseDelimited {
    def isKnownEmpty = data eq StreamProducer.EmptyProducer

    def dataBytes(implicit refFactory: ActorRefFactory): Producer[ByteString] = data
  }

  /**
   * The model for the entity of a chunked HTTP message (with `Transfer-Encoding: chunked`).
   */
  case class Chunked(contentType: ContentType, chunks: Producer[ChunkStreamPart]) extends Regular with japi.HttpEntityChunked {
    def isKnownEmpty = chunks eq StreamProducer.EmptyProducer
    def dataBytes(implicit refFactory: ActorRefFactory): Producer[ByteString] =
      Flow(chunks).map(_.data).toProducer

    // Java API
    def getChunks: Producer[japi.ChunkStreamPart] = chunks.asInstanceOf[Producer[japi.ChunkStreamPart]]
  }

  /**
   * An element of the HttpEntity data stream.
   * Can be either a `Chunk` or a `LastChunk`.
   */
  sealed trait ChunkStreamPart extends japi.ChunkStreamPart {
    def data: ByteString
    def extension: String
    def isLastChunk: Boolean
  }

  /**
   * An intermediate entity chunk guaranteed to carry non-empty data.
   */
  case class Chunk(data: ByteString, extension: String = "") extends ChunkStreamPart {
    def isLastChunk = false
  }

  /**
   * An intermediate entity chunk guaranteed to carry non-empty data.
   */
  case class LastChunk(extension: String = "", trailer: immutable.Seq[HttpHeader] = Nil) extends ChunkStreamPart {
    def data = ByteString.empty
    def isLastChunk = true
  }
  object LastChunk extends LastChunk("", Nil)
}