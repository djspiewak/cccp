package com.codecommit.cccp
package server

import blueeyes._
import blueeyes.core.data._
import blueeyes.core.http._

import java.io.{ByteArrayOutputStream, CharArrayReader, OutputStreamWriter}

object OpChunkUtil {
  def opToChunk(ops: Seq[Op]): ByteChunk = {
    val os = new ByteArrayOutputStream
    val writer = new OutputStreamWriter(os)
    OpFormat.write(ops, writer)
    writer.close()
    
    new MemoryChunk(os.toByteArray, { () => None })
  }
  
  def chunkToOp(chunk: ByteChunk): Seq[Op] = {
    val reader = new CharArrayReader(chunk.data map { _.toChar })
    OpFormat.read(reader)
  }
}
