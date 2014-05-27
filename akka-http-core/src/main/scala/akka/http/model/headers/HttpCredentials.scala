/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.model
package headers

import org.parboiled2.util.Base64
import akka.http.model.HttpCharsets._
import akka.http.util.{ Rendering, ValueRenderable }

import akka.http.model.japi.JavaMapping.Implicits._

sealed abstract class HttpCredentials extends japi.headers.HttpCredentials with ValueRenderable {
  def scheme: String
  def token: String
  def parameters: Map[String, String]

  /** Java API */
  def getParameters: java.util.Map[String, String] = parameters.asJava
}

case class BasicHttpCredentials(username: String, password: String) extends japi.headers.BasicHttpCredentials {
  val cookie = {
    val userPass = username + ':' + password
    val bytes = userPass.getBytes(`ISO-8859-1`.nioCharset)
    Base64.rfc2045.encodeToChar(bytes, false)
  }

  def render[R <: Rendering](r: R): r.type = r ~~ "Basic " ~~ cookie

  def scheme: String = "Basic"
  def token = cookie.toString
  def parameters = Map.empty
}

object BasicHttpCredentials {
  def apply(credentials: String): BasicHttpCredentials = {
    val bytes = Base64.rfc2045.decodeFast(credentials)
    val userPass = new String(bytes, `ISO-8859-1`.nioCharset)
    userPass.indexOf(':') match {
      case -1 ⇒ apply(userPass, "")
      case ix ⇒ apply(userPass.substring(0, ix), userPass.substring(ix + 1))
    }
  }
}

case class OAuth2BearerToken(token: String) extends japi.headers.OAuth2BearerToken {
  def render[R <: Rendering](r: R): r.type = r ~~ "Bearer " ~~ token

  def scheme: String = "Bearer"
  def parameters: Map[String, String] = Map.empty
}

case class GenericHttpCredentials(scheme: String, token: String,
                                  parameters: Map[String, String] = Map.empty) extends HttpCredentials {
  def render[R <: Rendering](r: R): r.type = {
    r ~~ scheme
    if (!token.isEmpty) r ~~ ' ' ~~ token
    if (parameters.nonEmpty)
      parameters foreach new (((String, String)) ⇒ Unit) {
        var first = true
        def apply(kvp: (String, String)): Unit = {
          val (k, v) = kvp
          if (first) { r ~~ ' '; first = false } else r ~~ ','
          if (!k.isEmpty) r ~~ k ~~ '='
          r ~~# v
        }
      }
    r
  }
}

object GenericHttpCredentials {
  def apply(scheme: String, parameters: Map[String, String]): GenericHttpCredentials = apply(scheme, "", parameters)
}
