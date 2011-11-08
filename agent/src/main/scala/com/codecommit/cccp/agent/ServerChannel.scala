package com.codecommit.cccp
package agent

import akka.actor.{Actor, ActorRef}

import blueeyes.core.service.engines.HttpClientXLightWeb
import blueeyes.core.http.MimeTypes._

class ServerChannel(protocol: String, host: String, port: Int) extends Actor {
  import ServerChannel._
  import server.OpChunkUtil._
  
  val client = {
    val back = new HttpClientXLightWeb
    back.protocol(protocol).host(host).port(port).contentType(text/plain)
  }
  
  def receive = {
    case PerformEdit(id, op) =>
      client.post("/" + id + "/")(opToChunk(Vector(op)))
    
    case Poll(id, version, callback) => {
      for (resp <- client.get("/" + id + "/" + version); content <- resp.content) {
        if (content.data.isEmpty) {
          self ! Poll(id, version, callback)
        } else {
          val ops = chunkToOp(content)
          ops foreach { op => callback ! EditPerformed(id, op) }
        }
      }
    }
  }
}

object ServerChannel {
  case class PerformEdit(id: String, op: Op)
  case class EditPerformed(id: String, op: Op)
  
  case class Poll(id: String, version: Int, callback: ActorRef)
}
