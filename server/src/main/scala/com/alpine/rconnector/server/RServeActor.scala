/*
 * This file is part of Alpine Data Labs' R Connector (henceforth " R Connector").
 * R Connector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * R Connector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with R Connector.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alpine.rconnector.server

import org.rosuda.REngine.Rserve.RConnection
import akka.AkkaException
import akka.actor.Actor
import com.alpine.rconnector.messages.{ RException, RResponse, RRequest }
import akka.event.Logging

/**
 * This is the actor that establishes a connection to R via Rserve
 * (see <a href="http://rforge.net/Rserve/">Rserve documentation</a>).
 * <br>
 * TCP connections to R are kept for as long as the actor is alive.
 * If the actor is killed by its supervisor or throws an exception, the connection
 * gets released.
 */
class RServeActor extends Actor {

  private[this] val log = Logging(context.system, this)

  protected val conn: RConnection = new RConnection()

  log.info("\nStarting RServeActor\n")

  override def postStop(): Unit = conn.close()

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    sender ! RException(s"R server failure:\n" +
      s"Message: ${reason.getMessage}\n" +
      s"Stack trace:\n${reason.getStackTrace.toList.mkString("\n")}")
    conn.close()
    super.preRestart(reason, message)
  }

  // that's the default anyway, but you can do something different
  override def postRestart(reason: Throwable): Unit = preStart()

  // remove all temporary data from R workspace
  private[this] def clearWorkspace() = conn.eval("rm(list = ls())")

  def receive: Receive = {

    case RRequest(msg) => {

      log.info(s"In RServeActor: received request from client through the router")
      clearWorkspace() // clear now in case previous user R code execution failed
      sender ! RResponse(conn.eval(msg).asString)
      clearWorkspace() // clean up after execution
      log.info(s"In RServeActor: done with R request, sent response")

    }

    case other => throw new AkkaException(s"Unexpected message of type ${other.getClass.getName} from $sender")

  }

}