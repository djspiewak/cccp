package com.codecommit.cccp
package jedit

import java.io.File
import org.gjt.sp.jedit.{jEdit => JEdit, EditPlugin, View}

class CCCPPlugin extends EditPlugin {
  override def start() {
    val starter = new Thread {
      override def run() {
        CCCPPlugin.init()
      }
    }
    
    starter.setPriority(3)
    starter.start()
  }
  
  override def stop() {
    CCCPPlugin.shutdown()
  }
}

object CCCPPlugin {
  val Home = new File("/Users/daniel/Development/Scala/cccp/agent/dist/")
  val Backend = new Backend(Home, fatalServerError)
  
  @volatile
  private var _callId = 0
  private val callLock = new AnyRef
  
  def callId() = callLock synchronized {
    _callId += 1
    _callId
  }
  
  private def init() {
    Backend.start()(receive)
  }
  
  private def shutdown() {
    // TODO
  }
  
  def link(view: View) {
  }
  
  private def send(chunk: String) {
    Backend.send(chunk)
  }
  
  private def receive(chunk: String): Unit = chunk match {
    case _ => // TODO
  }
  
  private def fatalServerError(msg: String) {
  }
}
