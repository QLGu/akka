/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model
package headers

import scala.collection.immutable
import org.parboiled2.CharPredicate
import akka.http.util._
import UriRendering.UriRenderer

import akka.http.model.japi.JavaMapping.Implicits._

case class LinkValue(uri: Uri, parameters: immutable.Seq[LinkParam]) extends japi.headers.LinkValue with ValueRenderable {
  import LinkParams.paramsRenderer
  def render[R <: Rendering](r: R): r.type = {
    r ~~ '<' ~~ uri ~~ '>'
    if (parameters.nonEmpty) r ~~ "; " ~~ parameters
    r
  }

  def getUri: japi.Uri = uri.asJava
  def getParameters: java.lang.Iterable[japi.headers.LinkParam] = parameters.asJava
}

object LinkValue {
  def apply(uri: Uri, params: LinkParam*): LinkValue = apply(uri, immutable.Seq(params: _*))
}

sealed abstract class LinkParam extends japi.headers.LinkParam with ToStringRenderable {
  val key: String = getClass.getSimpleName
  def value: AnyRef
}

object LinkParams {
  implicit val paramsRenderer: Renderer[Seq[LinkParam]] = Renderer.seqRenderer(separator = "; ")

  private val reserved = CharPredicate(" ,;")

  // A few convenience rels
  val next = rel("next")
  val prev = rel("prev")
  val first = rel("first")
  val last = rel("last")

  // http://tools.ietf.org/html/rfc5988#section-5.3
  // can be either a bare word, an absolute URI, or a quoted, space-separated string of zero-or-more of either.
  case class rel(value: String) extends LinkParam {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "rel="
      if (reserved matchesAny value) r ~~ '"' ~~ value ~~ '"' else r ~~ value
    }
  }

  // http://tools.ietf.org/html/rfc5988#section-5.2
  case class anchor(uri: Uri) extends LinkParam {
    def value: AnyRef = uri

    def render[R <: Rendering](r: R): r.type = r ~~ "anchor=\"" ~~ uri ~~ '"'
  }

  // http://tools.ietf.org/html/rfc5988#section-5.3
  // can be either a bare word, an absolute URI, or a quoted, space-separated string of zero-or-more of either.
  case class rev(value: String) extends LinkParam {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ "rev="
      if (reserved matchesAny value) r ~~ '"' ~~ value ~~ '"' else r ~~ value
    }
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class hreflang(lang: Language) extends LinkParam {
    def value: AnyRef = lang

    def render[R <: Rendering](r: R): r.type = r ~~ "hreflang=" ~~ lang
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class media(desc: String) extends LinkParam {
    def value: AnyRef = desc

    def render[R <: Rendering](r: R): r.type = {
      r ~~ "media="
      if (reserved matchesAny desc) r ~~ '"' ~~ desc ~~ '"' else r ~~ desc
    }
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class title(title: String) extends LinkParam {
    def value: AnyRef = title

    def render[R <: Rendering](r: R): r.type = r ~~ "title=\"" ~~ title ~~ '"'
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class `title*`(title: String) extends LinkParam {
    def value: AnyRef = title

    def render[R <: Rendering](r: R): r.type = {
      r ~~ "title*="
      if (reserved matchesAny title) r ~~ '"' ~~ title ~~ '"' else r ~~ title
    }
  }

  // http://tools.ietf.org/html/rfc5988#section-5.4
  case class `type`(mediaType: MediaType) extends LinkParam {
    def value: AnyRef = mediaType

    def render[R <: Rendering](r: R): r.type = {
      r ~~ "type="
      if (reserved matchesAny mediaType.value) r ~~ '"' ~~ mediaType.value ~~ '"' else r ~~ mediaType.value
    }
  }
}
