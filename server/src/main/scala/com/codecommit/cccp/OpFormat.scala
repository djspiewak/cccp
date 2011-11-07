package com.codecommit.cccp

import java.io.{Reader, Writer}
import org.waveprotocol.wave.model.document.operation.DocOp
import org.waveprotocol.wave.model.document.operation.DocOpCursor
import org.waveprotocol.wave.model.document.operation.DocOpComponentType
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil

object OpFormat {
  
  def write(op: Op, w: Writer) {
    import DocOpComponentType._
    
    writeString(op.id, w)
    writeInt(op.parent, w)
    writeInt(op.version, w)
    
    for (i <- 0 until op.delta.size) {
      op.delta getType i match {
        case CHARACTERS => {
          w.append("++")
          writeString(op.delta.getCharactersString(i), w)
        }
        
        case RETAIN => {
          w.append('r')
          writeInt(op.delta getRetainItemCount i, w)
        }
        
        case DELETE_CHARACTERS => {
          w.append("--")
          writeString(op.delta getDeleteCharactersString i, w)
        }
        
        case tpe => throw new IllegalArgumentException("unknown op component: " + tpe)
      }
    }
  }
  
  private def writeInt(i: Int, w: Writer) {
    w.append(i.toString)
    w.append(';')
  }
  
  private def writeString(str: String, w: Writer) {
    w.append(str.length.toString)
    w.append(':')
    w.append(str)
  }
  
  def read(r: Reader): Op = {
    val id = readString(r)
    val parent = readInt(r)
    val version = readInt(r)
    
    val unbuffered = new DocOp {
      def apply(c: DocOpCursor) {
        var next = r.read()
        while (next >= 0) {
          next.toChar match {
            case '+' => {
              r.read()        // TODO
              c.characters(readString(r))
            }
            
            case 'r' => c.retain(readInt(r))
            
            case '-' => {
              r.read()        // TODO
              c.deleteCharacters(readString(r))
            }
          }
          next = r.read()
        }
      }
    }
    
    val delta = DocOpUtil.buffer(unbuffered)
    
    Op(id, parent, version, delta)
  }
  
  private def readInt(r: Reader): Int = {
    val str = new StringBuilder
    
    var c = r.read().toChar
    while (c - '0' <= '9' - '0') {
      str.append(c)
      c = r.read().toChar
    }
    
    str.toString.toInt
  }
  
  private def readString(r: Reader): String = {
    val length = readInt(r)
    val buffer = new Array[Char](length)
    r.read(buffer)       // TODO
    new String(buffer)
  }
}
