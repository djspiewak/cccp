package com.codecommit.cccp
package server

import akka.actor._

class FilesActor extends Actor {
  import Actor._
  import FilesActor._
  
  private var files = Map[String, ActorRef]()
  
  def receive = {
    case PerformEdit(id, op) => fileRef(id) ! op
    case RequestHistory(id, from) => fileRef(id) ! FileActor.RequestHistory(self.channel, from)
  }
  
  private def fileRef(id: String) = {
    files get id getOrElse {
      val back = actorOf[FileActor]
      back.start()
      files += (id -> back)
      back
    }
  }
}

object FilesActor {
  case class PerformEdit(id: String, op: Op)
  case class RequestHistory(id: String, from: Option[Int])
}
