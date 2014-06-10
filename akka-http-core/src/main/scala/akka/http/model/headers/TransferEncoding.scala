/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model
package headers

import akka.http.util.{ Rendering, SingletonValueRenderable, Renderable }

import akka.http.model.japi.JavaMapping.Implicits._

sealed abstract class TransferEncoding extends japi.headers.TransferEncoding with Renderable {
  def name: String
  def parameters: Map[String, String]

  def getParameters: java.util.Map[String, String] = parameters.asJava
}

object TransferEncodings {
  protected abstract class Predefined extends TransferEncoding with SingletonValueRenderable {
    def name: String = value
    def parameters: Map[String, String] = Map.empty
  }

  case object chunked extends Predefined
  case object compress extends Predefined
  case object deflate extends Predefined
  case object gzip extends Predefined
  final case class Extension(name: String, parameters: Map[String, String] = Map.empty) extends TransferEncoding {
    def render[R <: Rendering](r: R): r.type = {
      r ~~ name
      parameters foreach { case (k, v) ⇒ r ~~ "; " ~~ k ~~ '=' ~~# v }
      r
    }
  }
}
