package com.codecommit.cccp
package agent

import akka.actor.{Actor, ActorRef}

class ClientFileActor(id: String, callback: ActorRef, channel: ActorRef) extends Actor {
  import ServerChannel._
  
  @volatile
  var state: ClientState = Synchronized(0)
  
  channel ! Poll(id, state.version, self)
  
  def receive = {
    case op: Op => handleAction(state applyClient op)
    
    case EditsPerformed(_, ops) => {
      for (op <- ops) {
        handleAction(state applyServer op)            // TODO could be a bit smarter here with Composer
      }
      
      channel ! Poll(id, state.version, self)
    }
  }
  
  private def handleAction(act: Action) = act match {
    case Send(op, state2) => {
      channel ! PerformEdit(id, op)
      state = state2
    }
    
    case Apply(op, state2) => {
      callback ! op
      state = state2
    }
    
    case Shift(state2) => {
      state = state2
    }
  }
}

