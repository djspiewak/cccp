package com.codecommit.cccp

import org.waveprotocol.wave.model.document.operation.BufferedDocOp

case class Op(id: String, parent: Int, version: Int, delta: BufferedDocOp) {
  def reparent(parent2: Int) =
    copy(parent = parent2, version = parent2 + (version - parent))
}
