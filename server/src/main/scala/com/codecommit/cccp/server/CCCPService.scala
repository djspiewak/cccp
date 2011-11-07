package com.codecommit.cccp
package server

import akka.actor.Actor.actorOf

import blueeyes._
import blueeyes.core.data._
import blueeyes.core.http._

import java.io.{ByteArrayOutputStream, CharArrayReader, OutputStreamWriter}

trait CCCPService extends BlueEyesServiceBuilder {
  import FilesActor._
  import MimeTypes._
  
  lazy val files = {
    val back = actorOf[FilesActor]
    back.start()
    back
  }
  
  // TODO content type for operation
  
  val cccpService = service("cccp", "0.1") { context =>
    request {
      path("/'fileId/") {
        produce(text/plain) {
          path('version) {
            get { request: HttpRequest[ByteChunk] =>
              val response = files !!! RequestHistory(request parameters 'fileId, Some(request.parameters('version).toInt))
              val back = new blueeyes.concurrent.Future[Option[ByteChunk]]
              
              response foreach {
                case data: Op => back deliver Some(opToChunk(data))
                case _ => back deliver None
              }
              
              back map { d => HttpResponse(content = d) }
            }
          } ~
          get { request: HttpRequest[ByteChunk] =>
            val response = files !!! RequestHistory(request parameters 'fileId, None)
            val back = new blueeyes.concurrent.Future[Option[ByteChunk]]
            
            response foreach {
              case data: Op => back deliver Some(opToChunk(data))
              case _ => back deliver None
            }
            
            back map { d => HttpResponse(content = d) }
          } ~
          put { request: HttpRequest[ByteChunk] =>
            for (content <- request.content) {
              val reader = new CharArrayReader(content.data map { _.toChar })
              val op = OpFormat.read(reader)
              files ! PerformEdit(request parameters 'fileId, op)
            }
            
            blueeyes.concurrent.Future.sync(HttpResponse(content = None))
          }
        }
      }
    }
  }
  
  private def opToChunk(op: Op): ByteChunk = {
    val os = new ByteArrayOutputStream
    val writer = new OutputStreamWriter(os)
    OpFormat.write(op, writer)
    new MemoryChunk(os.toByteArray, { () => None })
  }
}
