package com.codecommit.cccp
package jedit

import java.awt.EventQueue
import java.io.File
import java.lang.ref.WeakReference

import org.gjt.sp.jedit
import jedit.{Buffer, jEdit => JEdit, EditPlugin, View}
import jedit.buffer.{BufferAdapter, JEditBuffer}
import jedit.textarea.Selection

import scala.util.parsing.input.CharSequenceReader

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
  import SExp._
  
  val Home = new File("/Users/daniel/Development/Scala/cccp/agent/dist/")
  val Backend = new Backend(Home, fatalServerError)
  
  @volatile
  private var _callId = 0
  private val callLock = new AnyRef
  
  @volatile
  private var ignoredFiles = Set[String]()   // will only be written from the EDT
  
  def callId() = callLock synchronized {
    _callId += 1
    _callId
  }
  
  private def init() {
    Backend.start()(receive)
  }
  
  private def shutdown() {
    send(SExp(key(":swank-rpc"), SExp(key("swank:shutdown")), callId()).toWireString)
  }
  
  def link(view: View) {
    val buffer = view.getBuffer
    val fileName = new File(buffer.getPath).getAbsolutePath
    
    buffer.addBufferListener(new BufferAdapter {
      override def contentInserted(editBuffer: JEditBuffer, startLine: Int, offset: Int, numLines: Int, length: Int) {
        editBuffer match {
          case buffer: Buffer => {
            if (fileName == new File(buffer.getPath).getAbsolutePath) {
              if (!ignoredFiles.contains(fileName)) {
                val text = buffer.getText(offset, length)
                val remainder = buffer.getLength - offset - text.length
                sendInsert(fileName, offset, text, remainder)
              }
            }
          }
          
          case _ =>    // ignore
        }
      }
      
      override def contentRemoved(editBuffer: JEditBuffer, startLine: Int, offset: Int, numLines: Int, length: Int) {
        editBuffer match {
          case buffer: Buffer => {
            if (fileName == new File(buffer.getPath).getAbsolutePath) {
              if (!ignoredFiles.contains(fileName)) {
                val text = buffer.getText(offset, length)
                val remainder = buffer.getLength - offset - text.length
                sendDelete(fileName, offset, text, remainder)
              }
            }
          }
          
          case _ =>    // ignore
        }
      }
    })
  }
  
  private def applyActions(fileName: String, actions: Set[EditorAction]) {
    val view = JEdit.getActiveView
    val buffer = JEdit.openFile(view, fileName)
    
    // val origSelection = area.getSelection
    
    actions foreach {
      case InsertAt(offset, text) => buffer.insert(offset, text)
      case DeleteAt(offset, text) => buffer.remove(offset, text.length)
    }
    
    /* val newSelection = origSelection map { sel =>
      val relevant = actions filter { _.offset <= sel.getStart() }
      
      val adjustments = relevant map {
        case InsertAt(_, text) => text.length
        case DeleteAt(_, text) => -text.length
      }
      
      val delta = adjustments.sum
      
      sel match {
        case range: Selection.Range => new Selection.Range(range.getStart() + delta, range.getEnd() + delta)
        case rect: Selection.Rect => new Selection.Rect(rect.getStart() + delta, rect.getEnd() + delta)
      }
    }
    
    area.setSelection(newSelection) */
  }
  
  private def sendInsert(fileName: String, offset: Int, text: String, after: Int) {
    val op = SExp(key(":retain"), offset, key(":insert"), text, key(":retain"), after)
    send(SExp(key(":swank-rpc"), SExp(key("swank:edit-file"), fileName, op), callId()).toWireString)
  }
  
  private def sendDelete(fileName: String, offset: Int, text: String, after: Int) {
    val op = SExp(key(":retain"), offset, key(":delete"), text, key(":retain"), after)
    send(SExp(key(":swank-rpc"), SExp(key("swank:edit-file"), fileName, op), callId()).toWireString)
  }
  
  private def send(chunk: String) {
    Backend.send(chunk)
  }
  
  private def receive(chunk: String) {
    SExp.read(new CharSequenceReader(chunk)) match {
      case SExpList(KeywordAtom(":edit-performed") :: StringAtom(fileName) :: (form: SExpList) :: Nil) => {
        val components = unmarshallOp(form.items.toList)
        
        val (_, actions) = components.foldLeft((0, Set[EditorAction]())) {
          case ((offset, acc), Retain(length)) => (offset + length, acc)
          case ((offset, acc), Insert(text)) => (offset + text.length, acc + InsertAt(offset, text))
          case ((offset, acc), Delete(text)) => (offset + text.length, acc + DeleteAt(offset, text))
        }
        
        EventQueue.invokeLater(new Runnable {
          def run() {
            ignoredFiles += fileName
            applyActions(fileName, actions)
            ignoredFiles -= fileName
          }
        })
      }
      
      case _ => // TODO
    }
  }
  
  private def fatalServerError(msg: String) {
  }
  
  private def unmarshallOp(form: List[SExp]): List[OpComponent] = {
    val components = form zip (form drop 1) collect { case (KeywordAtom(id), se) => (id, se) }
    components collect {
      case (":retain", IntAtom(length)) => Retain(length)
      case (":insert", StringAtom(text)) => Insert(text)
      case (":delete", StringAtom(text)) => Delete(text)
    }
  }
  
  sealed trait OpComponent
  case class Retain(length: Int) extends OpComponent
  case class Insert(text: String) extends OpComponent
  case class Delete(text: String) extends OpComponent
  
  sealed trait EditorAction {
    val offset: Int
  }
  
  case class InsertAt(offset: Int, text: String) extends EditorAction
  case class DeleteAt(offset: Int, text: String) extends EditorAction
}
