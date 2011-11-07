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
  
  lazy val files = actorOf[FilesActor].start()
  
  // TODO content type for operation
  
  val cccpService = service("cccp", "0.1") {
    logging { log => context =>
      request {
        path("/'id/") {
          produce(text/plain) {
            path('version) {
              get { request: HttpRequest[ByteChunk] =>
                log.info("accessing history at a specific version")
                
                val response = files !!! RequestHistory(request parameters 'id, Some(request.parameters('version).toInt))
                val back = new blueeyes.concurrent.Future[Option[ByteChunk]]
              
                response.as[Op] foreach { op =>
                  log.info("delivering operation: " + new String(opToChunk(op).data))
                  back deliver Some(opToChunk(op))
                }
                
                back map { d => HttpResponse(content = d) }
              }
            } ~
            get { request: HttpRequest[ByteChunk] =>
              log.info("accessing composite history")
              
              val response = files !!! RequestHistory(request parameters 'id, None)
              val back = new blueeyes.concurrent.Future[Option[ByteChunk]]
              
              response.as[Op] foreach { op =>
                back deliver Some(opToChunk(op))
              }
              
              back map { d => HttpResponse(content = d) }
            } ~
            post { request: HttpRequest[ByteChunk] =>
              for (content <- request.content) {
                log.info("applying operation")
                val reader = new CharArrayReader(content.data map { _.toChar })
                val op = OpFormat.read(reader)
                files ! PerformEdit(request parameters 'id, op)
              }
              
              blueeyes.concurrent.Future.sync(HttpResponse(content = None))
            }
          }
        }
      }
    }
  }
  
  private def opToChunk(op: Op): ByteChunk = {
    val os = new ByteArrayOutputStream
    val writer = new OutputStreamWriter(os)
    OpFormat.write(op, writer)
    writer.close()
    
    new MemoryChunk(os.toByteArray, { () => None })
  }
}
