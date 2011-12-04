package com.codecommit.cccp
package jedit

import java.awt.EventQueue
import java.io.File
import java.lang.ref.WeakReference

import javax.swing.JOptionPane

import org.gjt.sp.jedit
import jedit.{Buffer, jEdit => JEdit, EBPlugin, View}
import jedit.buffer.{BufferAdapter, JEditBuffer}
import jedit.textarea.Selection

import scala.util.parsing.input.CharSequenceReader

class CCCPPlugin extends EBPlugin {
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
  
  val HomeProperty = "cccp.home"
  val ProtocolProperty = "cccp.protocol"
  val HostProperty = "cccp.host"
  val PortProperty = "cccp.port"
  
  def Home = new File(Option(JEdit.getProperty(HomeProperty)) getOrElse "/Users/daniel/Development/Scala/cccp/agent/dist/")
  var Backend = new Backend(Home, fatalServerError)
  
  def Protocol = Option(JEdit.getProperty(ProtocolProperty)) getOrElse "http"
  def Host = Option(JEdit.getProperty(HostProperty)) getOrElse "localhost"
  def Port = Option(JEdit.getProperty(PortProperty)) flatMap { s => try { Some(s.toInt) } catch { case _ => None } } getOrElse 8585
  
  @volatile
  private var _callId = 0
  private val callLock = new AnyRef
  
  @volatile
  private var ignoredFiles = Set[String]()   // will only be written from the EDT
  
  def callId() = callLock synchronized {
    _callId += 1
    _callId
  }
  
  // TODO make this more controlled
  def reinit() {
    shutdown()
    Backend = new Backend(Home, fatalServerError)
    init()
  }
  
  private def init() {
    Backend.start()(receive)
    sendRPC(SExp(key("swank:init-connection"), SExp(key(":protocol"), Protocol, key(":host"), Host, key(":port"), Port)), callId())
  }
  
  private def shutdown() {
    sendRPC(SExp(key("swank:shutdown")), callId())
    Backend.stop()
  }
  
  def link(view: View) {
    val buffer = view.getBuffer
    val fileName = new File(buffer.getPath).getAbsolutePath
    
    EventQueue.invokeLater(new Runnable {
      def run() {
        for (id <- Option(JOptionPane.showInputDialog(view, "File ID:"))) {
          sendRPC(SExp(key("swank:link-file"), id, fileName), callId())
          
          buffer.addBufferListener(new BufferAdapter {
            override def contentInserted(editBuffer: JEditBuffer, startLine: Int, offset: Int, numLines: Int, length: Int) {
              contentChanged("insert", editBuffer, startLine, offset, numLines, length)
            }
            
            override def preContentRemoved(editBuffer: JEditBuffer, startLine: Int, offset: Int, numLines: Int, length: Int) {
              contentChanged("delete", editBuffer, startLine, offset, numLines, length)
            }
            
            private def contentChanged(change: String, editBuffer: JEditBuffer, startLine: Int, offset: Int, numLines: Int, length: Int) {
              editBuffer match {
                case buffer: Buffer => {
                  if (fileName == new File(buffer.getPath).getAbsolutePath) {
                    if (!ignoredFiles.contains(fileName)) {
                      val text = buffer.getText(offset, length)
                      val remainder = buffer.getLength - offset - text.length
                      sendChange(change, fileName, offset, text, remainder)
                    }
                  }
                }
                
                case _ =>    // ignore
              }
            }
          })
        }
      }
    })
  }
  
  def unlink(view: View) {
    val buffer = view.getBuffer
    val fileName = new File(buffer.getPath).getAbsolutePath
    
    EventQueue.invokeLater(new Runnable {
      def run() {
        val confirm = JOptionPane.showConfirmDialog(view, "Are you sure you wish to unlink the buffer?  You should not attempt to relink on the same identifier following an unlink.", "Are you sure?", JOptionPane.YES_NO_OPTION)
        
        if (confirm == JOptionPane.YES_OPTION) {
          sendRPC(SExp(key("swank:unlink-file"), fileName), callId())
        }
      }
    })
  }
  
  private def applyActions(fileName: String, actions: Seq[EditorAction]) {
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
  
  // TODO type safety
  private def sendChange(change: String, fileName: String, offset: Int, text: String, after: Int) {
    val op = SExp(key(":retain"), offset, key(":" + change), text, key(":retain"), after)
    sendRPC(SExp(key("swank:edit-file"), fileName, op), callId())
  }
  
  private def sendRPC(form: SExp, id: Int) {
    send(SExp(key(":swank-rpc"), form, id).toWireString)
  }
  
  private def send(chunk: String) {
    Backend.send(chunk)
  }
  
  private def receive(chunk: String) {
    SExp.read(new CharSequenceReader(chunk)) match {
      case SExpList(KeywordAtom(":edit-performed") :: StringAtom(fileName) :: (form: SExpList) :: Nil) => {
        val components = unmarshallOp(form.items.toList)
        
        val (_, actions) = components.foldLeft((0, Vector[EditorAction]())) {
          case ((offset, acc), Retain(length)) => (offset + length, acc)
          case ((offset, acc), Insert(text)) => (offset + text.length, acc :+ InsertAt(offset, text))
          case ((offset, acc), Delete(text)) => (offset + text.length, acc :+ DeleteAt(offset, text))
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
      case (":retain", IntAtom(length)) if length > 0 => Retain(length)
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
