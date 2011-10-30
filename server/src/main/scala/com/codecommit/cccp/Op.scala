package com.codecommit.cccp

import java.util.UUID
import org.waveprotocol.wave.model.document.operation.BufferedDocOp
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder
import org.waveprotocol.wave.model.document.operation.DocOpComponentType

case class Op(id: String, parent: Int, version: Int, delta: BufferedDocOp) {
  
  def retain(len: Int) =
    copy(delta = builder.retain(len).build)
  
  def chars(str: String) =
    copy(delta = builder.characters(str).build)
  
  def delete(str: String) =
    copy(delta = builder.deleteCharacters(str).build)
  
  def reparent(parent2: Int) =
    copy(parent = parent2, version = parent2 + (version - parent))
  
  private def builder = {
    import DocOpComponentType._
    
    val back = new DocOpBuilder
    for (i <- 0 until delta.size) {
      delta getType i match {
        case CHARACTERS => back.characters(delta getCharactersString i)
        case RETAIN => back.retain(delta getRetainItemCount i)
        case DELETE_CHARACTERS => back.deleteCharacters(delta getDeleteCharactersString i)
        case tpe => throw new IllegalArgumentException("unknown op component: " + tpe)
      }
    }
    back
  }
}

object Op extends ((String, Int, Int, BufferedDocOp) => Op) {
  def apply(parent: Int): Op =
    Op(UUID.randomUUID().toString, parent, parent + 1, new DocOpBuilder().build)
}
