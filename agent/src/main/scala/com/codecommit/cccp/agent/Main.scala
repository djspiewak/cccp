package com.codecommit.cccp
package agent

import akka.actor.Actor.actorOf

import java.io._
import java.net._

object Main extends App {
  if (args.length < 1) {
    System.err.println("usage: bin/server <port-file>")
    System.exit(-1)
  }
  
  val portFile = new File(args.head)
  val server = new ServerSocket(0)
  
  val writer = new FileWriter(portFile)
  try {
    writer.write(server.getLocalPort.toString)
  } finally {
    writer.close()
  }
  
  actorOf(new SwankProtocol(server.accept())).start()
}
