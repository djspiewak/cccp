package com.codecommit.cccp
package agent

import akka.actor.{Actor, ActorRef, Scheduler}

import blueeyes.core.service.engines.HttpClientXLightWeb
import blueeyes.core.http.MimeTypes._

import java.util.concurrent.TimeUnit

class ServerChannel(protocol: String, host: String, port: Int) extends Actor {
  import ServerChannel._
  import server.OpChunkUtil._
  
  val MaxDelay = 30 * 1000       // 30 seconds
  
  val client = {
    val back = new HttpClientXLightWeb
    back.protocol(protocol).host(host).port(port).contentType(text/plain)
  }
  
  def receive = {
    case PerformEdit(id, op, delay) => {
      val chunk = opToChunk(Vector(op))
      println("Sending chunk to server: " + (chunk.data map { _.toChar } mkString))
      
      client.post("/" + id + "/")(chunk) ifCanceled { _ =>
        val delay2 = math.max(delay * 2, MaxDelay)
        val randomDelay = (math.random * delay).toInt
        Scheduler.scheduleOnce(self, PerformEdit(id, op, delay2), randomDelay, TimeUnit.MILLISECONDS)
      }
    }
    
    case Poll(id, version, callback, delay) => {
      val pollResults = client.get("/" + id + "/" + version)
      
      pollResults ifCanceled { _ =>
        val delay2 = math.max(delay * 2, MaxDelay)
        val randomDelay = (math.random * delay).toInt
        Scheduler.scheduleOnce(self, Poll(id, version, callback, delay2), randomDelay, TimeUnit.MILLISECONDS)
      }
      
      for (resp <- pollResults; content <- resp.content) {
        if (content.data.isEmpty) {
          self ! Poll(id, version, callback)
        } else {
          println("Received chunk from server:\n  " + (content.data map { _.toChar } mkString).replace("\n", "\n  "))
          
          val ops = chunkToOp(content)
          callback ! EditsPerformed(id, ops)
        }
      }
    }
  }
}

object ServerChannel {
  case class PerformEdit(id: String, op: Op, delay: Int = 1)
  case class EditsPerformed(id: String, op: Seq[Op])
  
  case class Poll(id: String, version: Int, callback: ActorRef, delay: Int = 1)
}
