package com.codecommit.cccp
package agent

import akka.actor.{Actor, ActorRef}

import java.net.Socket
import java.util.UUID

import org.waveprotocol.wave.model.document.operation.DocOp
import org.waveprotocol.wave.model.document.operation.DocOpCursor
import org.waveprotocol.wave.model.document.operation.DocOpComponentType
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil

import scala.util.parsing.input.CharSequenceReader

import util._

class SwankProtocol(socket: Socket) extends Actor {
  import Actor._
  import ClientFileActor._
  import SExp._
  import SwankProtocol._
  
  val agent = new AsyncSocketAgent(socket, receiveData, { _ => })        // TODO error handling
  
  @volatile
  var channel: ActorRef = _
  
  @volatile
  var files = Map[String, ActorRef]()
  
  def receive = {
    case InitConnection(protocol, host, port) =>
      channel = actorOf(new ServerChannel(protocol, host, port))
    
    case LinkFile(id, fileName) => {
      if (channel != null) {
        files = files.updated(fileName, actorOf(new ClientFileActor(id, fileName, self, channel)))
      }
    }
    
    case EditFile(fileName, op) => files get fileName foreach { _ ! op }
    
    case EditPerformed(fileName, op) =>
  }

  def receiveData(chunk: String) {
    SExp.read(new CharSequenceReader(chunk)) match {
      case SExpList(KeywordAtom(":swank-rpc") :: (form @ SExpList(SymbolAtom(name) :: _)) :: IntAtom(callId) :: _) => {
        handleRPC(name, form, callId)
      }
      
      case _ =>     // TODO
    }
  }
  
  def handleRPC(name: String, form: SExp, callId: Int) = name match {
    case "swank:init-connection" => form match {
      case SExpList(_ :: (conf: SExpList) :: Nil) => {
        val map = conf.toKeywordMap
        
        if (map.contains(key(":host")) && map.contains(key(":port"))) {
          val StringAtom(protocol) = map.getOrElse(key(":protocol"), "http")
          val StringAtom(host) = map(key(":host"))
          val IntAtom(port) = map(key(":port"))
          
          actorOf(this) ! InitConnection(protocol, host, port)
        } else {
          // TODO
        }
      }
      
      case _ => // TODO
    }
    
    case "swank:link-file" => form match {
      case SExpList(_ :: StringAtom(id) :: StringAtom(fileName) :: Nil) =>
        actorOf(this) ! LinkFile(id, fileName)
      
      case _ => // TODO
    }
  
    case "swank:edit-file" => form match {
      case SExpList(_ :: StringAtom(fileName) :: (opForm: SExpList) :: Nil) => {
        val rawOp = new DocOp {
          def apply(c: DocOpCursor) {
            parseOp(c, opForm.items.toList)
          }
        }
        
        val op = Op(UUID.randomUUID().toString, 0, 1, DocOpUtil.buffer(rawOp))
        actorOf(this) ! EditFile(fileName, op)
      }
      
      case _ => // TODO
    }
  
    // TODO more calls
  
    case _ => // TODO
  }

  def parseOp(c: DocOpCursor, form: List[SExp]): Op = form match {
    case KeywordAtom(":retain") :: IntAtom(length) :: tail => {
      c.retain(length)
      parseOp(c, tail)
    }
    
    case KeywordAtom(":insert") :: StringAtom(text) :: tail => {
      c.characters(text)
      parseOp(c, tail)
    }
    
    case KeywordAtom(":delete") :: StringAtom(text) :: tail => {
      c.deleteCharacters(text)
      parseOp(c, tail)
    }
    
    case _ => sys.error("TODO")
  }
  
  def marshallOp(op: Op): SExp = {
    import DocOpComponentType._
    
    val items = (0 until op.delta.size map op.delta.getType zipWithIndex) flatMap {
      case (RETAIN, i) => key(":retain") :: IntAtom(op.delta getRetainItemCount i) :: Nil
      case (CHARACTERS, i) => key(":insert") :: StringAtom(op.delta getCharactersString i) :: Nil
      case (DELETE_CHARACTERS, i) => key(":delete") :: StringAtom(op.delta getDeleteCharactersString i) :: Nil
      case (tpe, _) => throw new IllegalArgumentException("unknown op component: " + tpe)
    }
    
    SExpList(items)
  }
  
  def dispatchReturn(callId: Int, form: SExp) {
    dispatchData(SExp(key(":return"), SExp(key(":ok"), form, callId)).toWireString)
  }
  
  def dispatchData(chunk: String) {
    agent.send(chunk)
  }
}

object SwankProtocol {
  case class InitConnection(protocol: String, host: String, port: Int)
  case class LinkFile(id: String, fileName: String)
  case class EditFile(fileName: String, op: Op)
}
