package com.codecommit.cccp
package jedit

import java.awt.EventQueue
import java.io.File

import org.gjt.sp.jedit
import jedit.{jEdit => JEdit, EditPlugin, View}
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
  }
  
  private def applyActions(fileName: String, actions: Set[EditorAction]) {
    val view = JEdit.getActiveView
    val buffer = JEdit.openFile(view, fileName)
    val pane = view.goToBuffer(buffer)
    val area = pane.getTextArea
    
    val origSelection = area.getSelection
    
    actions foreach {
      case InsertAt(offset, text) =>
        area.setSelectedText(new Selection.Range(offset, offset), text)
      
      case DeleteAt(offset, text) =>
        area.setSelectedText(new Selection.Range(offset, offset + text.length), "")
    }
    
    val newSelection = origSelection map { sel =>
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
    
    area.setSelection(newSelection)
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
            applyActions(fileName, actions)
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
