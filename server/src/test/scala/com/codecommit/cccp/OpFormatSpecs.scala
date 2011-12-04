package com.codecommit.cccp

import org.specs2.mutable._
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.CharArrayReader

object OpFormatSpecs extends Specification {
  
  "operation serialization" should {
    "serialize/deserialize a single operation" in {
      val op = Op(0).retain(12).chars("test")
      val str = writeToString(Vector(op))
      val ops = readFromString(str)
      
      ops must haveSize(1)
      ops.head must beLike {
        case Op(id, 0, 1, delta) if id == op.id => {
          delta.size mustEqual 2
          delta.getRetainItemCount(0) mustEqual 12
          delta.getCharactersString(1) mustEqual "test"
        }
      }
    }
    
    "serialize/deserialize multiple operations" in {
      val op1 = Op(0).retain(12).chars("test")
      val op2 = Op(1).chars("boo!").retain(37).delete("test?")
      
      val str = writeToString(Vector(op1, op2))
      val ops = readFromString(str)
      
      ops must haveSize(2)
      
      ops(0) must beLike {
        case Op(id, 0, 1, delta) if id == op1.id => {
          delta.size mustEqual 2
          delta.getRetainItemCount(0) mustEqual 12
          delta.getCharactersString(1) mustEqual "test"
        }
      }
      
      ops(1) must beLike {
        case Op(id, 1, 2, delta) if id == op2.id => {
          delta.size mustEqual 3
          delta.getCharactersString(0) mustEqual "boo!"
          delta.getRetainItemCount(1) mustEqual 37
          delta.getDeleteCharactersString(2) mustEqual "test?"
        }
      }
    }
  }
  
  def writeToString(ops: Seq[Op]): String = {
    val os = new ByteArrayOutputStream
    val writer = new OutputStreamWriter(os)
    OpFormat.write(ops, writer)
    writer.close()
    
    os.toByteArray map { _.toChar } mkString
  }
  
  def readFromString(str: String): Seq[Op] = {
    val reader = new CharArrayReader(str.toArray)
    OpFormat.read(reader)
  }
}
