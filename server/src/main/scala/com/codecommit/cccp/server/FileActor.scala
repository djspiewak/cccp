package com.codecommit.cccp
package server

import akka.actor._
import scala.collection.SortedMap

class FileActor extends Actor {
  import FileActor._
  
  private var history: OpHistory = _
  
  private var listeners = SortedMap[Int, Set[Channel[Op]]]()
  
  def receive = {
    case op: Op if history == null => {
      history = new OpHistory(op)
      broadcast(op)
    }
    
    case op: Op if history != null && history.isDefinedAt(op) => {
      val (op2, history2) = history(op)
      broadcast(op2)
    }
    
    // TODO error handling on operations
    
    case RequestHistory(channel, Some(version)) if history == null || version >= history.version => {
      if (listeners contains version)
        listeners = listeners.updated(version, listeners(version) + channel)
      else
        listeners += (version -> Set(channel))
    }
    
    case RequestHistory(channel, version) if history != null =>
      channel ! history.from(version)
  }
  
  private def broadcast(op: Op) {
    val channels = (listeners to op.version values).flatten
    listeners = listeners from (op.version + 1)
    channels foreach { _ ! op }
  }
}

object FileActor {
  case class RequestHistory(channel: Channel[Op], from: Option[Int])
}
