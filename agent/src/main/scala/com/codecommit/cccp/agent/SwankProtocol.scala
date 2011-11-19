package com.codecommit.cccp
package agent

import akka.actor.{Actor, ActorRef, PoisonPill}

import java.io.StringWriter
import java.net.Socket
import java.util.UUID

import org.waveprotocol.wave.model.document.operation.DocOp
import org.waveprotocol.wave.model.document.operation.DocOpComponentType
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil

import scala.util.parsing.input.CharSequenceReader

import util._

class SwankProtocol(socket: Socket) extends Actor {
  import Actor._
  import ClientFileActor._
  import SExp._
  import SwankProtocol._
  
  val agent = new AsyncSocketAgent(socket, receiveData, { msg =>
    println("ERROR!!!  " + msg)
    System.exit(-1)
  })
  
  @volatile
  var channel: ActorRef = _
  
  @volatile
  var files = Map[String, ActorRef]()
  
  def receive = {
    case InitConnection(protocol, host, port) => {
      println("Initializing connection: %s://%s:%d".format(protocol, host, port))
      channel = actorOf(new ServerChannel(protocol, host, port)).start()
    }
    
    case LinkFile(id, fileName) => {
      if (channel != null) {
        println("Linking file: %s -> %s".format(id, fileName))
        files = files.updated(fileName, actorOf(new ClientFileActor(id, fileName, self, channel)).start())
      }
    }
    
    case UnlinkFile(fileName) => {
      files get fileName foreach { _ ! PoisonPill }
      files -= fileName
    }
    
    case EditFile(fileName, op) => {
      val writer = new StringWriter
      OpFormat.write(Vector(op), writer)
      println(">>> Client Op: " + writer.toString)
      
      files get fileName foreach { _ ! op }
    }
    
    case EditPerformed(fileName, op) => {
      val writer = new StringWriter
      OpFormat.write(Vector(op), writer)
      println("<<< Server Op: " + writer.toString)
      
      dispatchSExp(SExp(key(":edit-performed"), fileName, marshallOp(op)))
    }
  }

  def receiveData(chunk: String) {
    println("Handling chunk: " + chunk)
    
    SExp.read(new CharSequenceReader(chunk)) match {
      case SExpList(KeywordAtom(":swank-rpc") :: (form @ SExpList(SymbolAtom(name) :: _)) :: IntAtom(callId) :: _) => {
        try {
          handleRPC(name, form, callId)
        } catch {
          case t => sendRPCError(ErrExceptionInRPC, t.getMessage, callId)
        }
      }
      
      case _ => sendProtocolError(ErrUnrecognizedForm, chunk)
    }
  }
  
  def handleRPC(name: String, form: SExp, callId: Int) = name match {
    case "swank:init-connection" => form match {
      case SExpList(_ :: (conf: SExpList) :: Nil) => {
        val map = conf.toKeywordMap
        
        if (map.contains(key(":host")) && map.contains(key(":port"))) {
          val StringAtom(protocol) = map.getOrElse(key(":protocol"), StringAtom("http"))
          val StringAtom(host) = map(key(":host"))
          val IntAtom(port) = map(key(":port"))
          
          self.start() ! InitConnection(protocol, host, port)
        } else {
          sendMalformedCall(name, form, callId)
        }
      }
      
      case _ => sendMalformedCall(name, form, callId)
    }
    
    case "swank:link-file" => form match {
      case SExpList(_ :: StringAtom(id) :: StringAtom(fileName) :: Nil) =>
        self ! LinkFile(id, fileName)
      
      case _ => sendMalformedCall(name, form, callId)
    }
    
    case "swank:unlink-file" => form match {
      case SExpList(_ :: StringAtom(fileName) :: Nil) =>
        self ! UnlinkFile(fileName)
      
      case _ => sendMalformedCall(name, form, callId)
    }
  
    case "swank:edit-file" => form match {
      case SExpList(_ :: StringAtom(fileName) :: (opForm: SExpList) :: Nil) =>
        self ! EditFile(fileName, parseOp(opForm.items.toList))
      
      case _ => sendMalformedCall(name, form, callId)
    }
    
    case "swank:shutdown" => System.exit(0)
  
    // TODO more calls
  
    case _ => sendRPCError(ErrUnrecognizedRPC, "Unknown :swank-rpc call: " + form, callId)
  }
  
  def sendMalformedCall(callType: String, form: SExp, callId: Int) {
    sendRPCError(ErrMalformedRPC, "Malformed %s call: %s".format(callType, form), callId)
  }

  def sendRPCError(code: Int, detail: String, callId: Int) {
    dispatchSExp(SExp(key(":return"), SExp(key(":abort"), code, detail), callId))
  }

  def sendProtocolError(code: Int, detail: String) {
    dispatchSExp(SExp(key(":reader-error"), code, detail))
  }

  def parseOp(form: List[SExp], op: Op = Op(0)): Op = form match {
    case KeywordAtom(":retain") :: IntAtom(length) :: tail =>
      parseOp(tail, op.retain(length))
    
    case KeywordAtom(":insert") :: StringAtom(text) :: tail =>
      parseOp(tail, op.chars(text))
    
    case KeywordAtom(":delete") :: StringAtom(text) :: tail =>
      parseOp(tail, op.delete(text))
    
    case Nil => op
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
    dispatchSExp(SExp(key(":return"), SExp(key(":ok"), form, callId)))
  }
  
  def dispatchSExp(form: SExp) {
    agent.send(form.toWireString)
  }
  
  def dispatchData(chunk: String) {
    println("Sending chunk: " + chunk)
    agent.send(chunk)
  }
}

object SwankProtocol {
  val ErrExceptionInRPC = 201
  val ErrMalformedRPC = 202
  val ErrUnrecognizedForm = 203
  val ErrUnrecognizedRPC = 204
  
  case class InitConnection(protocol: String, host: String, port: Int)
  case class LinkFile(id: String, fileName: String)
  case class UnlinkFile(fileName: String)
  case class EditFile(fileName: String, op: Op)
}
