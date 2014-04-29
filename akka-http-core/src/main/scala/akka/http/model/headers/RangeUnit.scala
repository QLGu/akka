/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model
package headers

import akka.http.util.{ Rendering, ValueRenderable }

sealed trait RangeUnit extends ValueRenderable with japi.headers.RangeUnit {
  def name: String
}

object RangeUnit {
  object Bytes extends RangeUnit {
    def name = "Bytes"

    def render[R <: Rendering](r: R): r.type = r ~~ "bytes"
  }

  case class Other(name: String) extends RangeUnit {
    def render[R <: Rendering](r: R): r.type = r ~~ name
  }
}
