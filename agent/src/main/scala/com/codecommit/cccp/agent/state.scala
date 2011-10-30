package com.codecommit.cccp
package agent

import org.waveprotocol.wave.model.document.operation.algorithm.{Composer, Transformer}

sealed trait ClientState {
  def applyClient(op: Op): Action
  def applyServer(op: Op): Action
}

case class Synchronized(version: Int) extends ClientState {
  def applyClient(op: Op) = Send(op.reparent(version), AwaitingConfirm(op, version))
  def applyServer(op: Op) = Apply(op, Synchronized(op.version))
}

case class AwaitingConfirm(outstanding: Op, version: Int) extends ClientState {
  def applyClient(op: Op) =
    Shift(AwaitingWithBuffer(outstanding, op.reparent(outstanding.version), version))
  
  def applyServer(op: Op) = {
    if (op.id == outstanding.id) {
      Shift(Synchronized(op.version))
    } else {
      val pair = Transformer.transform(outstanding.delta, op.delta)
      val (client, server) = (pair.clientOp, pair.serverOp)
      val outstanding2 = outstanding.copy(delta = client)
      Apply(op.copy(delta = server), AwaitingConfirm(outstanding2.reparent(op.version), op.version))
    }
  }
}

case class AwaitingWithBuffer(outstanding: Op, buffer: Op, version: Int) extends ClientState {
  def applyClient(op: Op) = {
    val buffer2 = buffer.copy(id = op.id, version = buffer.version + 1, delta = Composer.compose(buffer.delta, op.delta))
    Shift(AwaitingWithBuffer(outstanding, buffer2, version))
  }
  
  def applyServer(op: Op) = {
    if (op.id == outstanding.id) {
      assert(op.version == outstanding.version)
      Send(buffer, AwaitingConfirm(buffer, op.version))
    } else {
      val pair = Transformer.transform(outstanding.delta, op.delta)
      val (client, server) = (pair.clientOp, pair.serverOp)
      val outstanding2 = outstanding.copy(delta = client).reparent(op.version)
      
      val pair2 = Transformer.transform(buffer.delta, server)
      val (client2, server2) = (pair2.clientOp, pair2.serverOp)
      val buffer2 = buffer.copy(delta = client2).reparent(outstanding2.version)
      
      Apply(op.copy(delta = server2), AwaitingWithBuffer(outstanding2, buffer2, op.version))
    }
  }
}


sealed trait Action

case class Send(op: Op, state: ClientState) extends Action
case class Apply(op: Op, state: ClientState) extends Action
case class Shift(state: ClientState) extends Action
