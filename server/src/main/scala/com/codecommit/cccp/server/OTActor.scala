package com.codecommit.cccp
package server

import akka.actor._
import scala.collection.SortedMap

class OTActor extends Actor {
  import OTActor._
  
  @volatile
  private var history: OpHistory = _
  
  @volatile
  private var listeners = SortedMap[Int, Set[Channel[Seq[Op]]]]()
  
  def receive = {
    case op: Op => {
      if (history == null) {
        history = new OpHistory(op)
        broadcast(op)
      } else if (history != null && history.isDefinedAt(op)) {
        val (op2, history2) = history(op)
        history = history2
        broadcast(op2)
      }
    }
    
    // TODO error handling on operations
    
    case RequestHistory(channel, version) => {
      if (history == null || version > history.version) {
        if (listeners contains version)
          listeners = listeners.updated(version, listeners(version) + channel)
        else
          listeners += (version -> Set(channel))
      } else {
        channel ! history.from(version)
      }
    }
  }
  
  private def broadcast(op: Op) {
    val channels = (listeners to op.version values).flatten
    listeners = listeners from (op.version + 1)
    channels foreach { _ ! Vector(op) }
  }
}

object OTActor {
  case class RequestHistory(channel: Channel[Seq[Op]], from: Int)
}
