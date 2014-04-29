/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model
package headers

import akka.http.util.{ Rendering, ValueRenderable }

import akka.http.model.japi.JavaMapping.Implicits._

sealed trait ContentRange extends ValueRenderable with japi.headers.ContentRange {
  // default implementations to override
  def isSatisfiable: Boolean = false
  def isOther: Boolean = false
  def getSatisfiableFirst: akka.japi.Option[java.lang.Long] = akka.japi.Option.none
  def getSatisfiableLast: akka.japi.Option[java.lang.Long] = akka.japi.Option.none
  def getOtherValue: akka.japi.Option[String] = akka.japi.Option.none
}

sealed trait ByteContentRange extends ContentRange {
  def instanceLength: Option[Long]

  // Java API
  def isByteContentRange: Boolean = true
  def getInstanceLength: akka.japi.Option[java.lang.Long] = instanceLength.asJava
}

// http://tools.ietf.org/html/draft-ietf-httpbis-p5-range-26#section-4.2
object ContentRange {
  def apply(first: Long, last: Long): Default = apply(first, last, None)
  def apply(first: Long, last: Long, instanceLength: Long): Default = apply(first, last, Some(instanceLength))
  def apply(first: Long, last: Long, instanceLength: Option[Long]): Default = Default(first, last, instanceLength)

  /**
   * Models a satisfiable HTTP content-range.
   */
  case class Default(first: Long, last: Long, instanceLength: Option[Long]) extends ByteContentRange {
    require(0 <= first && first <= last, "first must be >= 0 and <= last")
    require(instanceLength.isEmpty || instanceLength.get > last, "instanceLength must be empty or > last")

    def render[R <: Rendering](r: R): r.type = {
      r ~~ first ~~ '-' ~~ last ~~ '/'
      if (instanceLength.isDefined) r ~~ instanceLength.get else r ~~ '*'
    }

    // Java API
    override def isSatisfiable: Boolean = true
    override def getSatisfiableFirst: akka.japi.Option[java.lang.Long] = akka.japi.Option.some(first)
    override def getSatisfiableLast: akka.japi.Option[java.lang.Long] = akka.japi.Option.some(last)
  }

  /**
   * An unsatisfiable content-range.
   */
  case class Unsatisfiable(length: Long) extends ByteContentRange {
    val instanceLength = Some(length)
    def render[R <: Rendering](r: R): r.type = r ~~ "*/" ~~ length
  }

  /**
   * An `other-range-resp`.
   */
  case class Other(override val value: String) extends ContentRange {
    def render[R <: Rendering](r: R): r.type = r ~~ value

    def isByteContentRange = false
    def getInstanceLength: akka.japi.Option[java.lang.Long] = akka.japi.Option.none
    override def getOtherValue: akka.japi.Option[String] = akka.japi.Option.some(value)
  }
}