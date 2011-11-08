package com.codecommit.cccp
package server

import org.waveprotocol.wave.model.document.operation.algorithm.{Composer, Transformer}
import scala.collection.JavaConverters._
import scala.collection.immutable.SortedMap

final class OpHistory private (history: SortedMap[Int, Op]) extends PartialFunction[Op, (Op, OpHistory)] {
  import Function._
  
  def this(base: Op) = this(SortedMap(base.version -> base))
  
  def version = history.last._1
  
  def apply(op: Op) = {
    if (!(history contains op.parent)) {
      throw new IllegalArgumentException("parent version %d is not in history".format(op.version))
    } else {
      val op2 = attemptTransform(op)
      val history2 = history + (op2.version -> op2)
      (op2, new OpHistory(history2))
    }
  }
  
  def isDefinedAt(op: Op) = {
    lazy val canTransform = try {
      attemptTransform(op)
      true
    } catch {
      case _ => false
    }
    
    (history contains op.parent) && canTransform
  }
  
  def from(version: Int): Seq[Op] = (history from version values).toSeq
  
  private def attemptTransform(op: Op): Op = {
    val intervening = history from (op.parent + 1) values
    
    if (intervening.isEmpty) {
      op
    } else {
      val server = Composer.compose(intervening map { _.delta } asJava)
      val pair = Transformer.transform(op.delta, server)
      op.copy(delta = pair.clientOp).reparent(intervening.last.version)
    }
  }
}
