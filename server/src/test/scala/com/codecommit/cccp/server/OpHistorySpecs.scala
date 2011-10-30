package com.codecommit.cccp
package server

import org.specs2.mutable.Specification

object OpHistorySpecs extends Specification {
  
  "operation history" should {
    "know the latest version" in {
      var hist = new OpHistory(Op(42))
      hist.version mustEqual 43
      
      hist = hist(Op(43).chars("test"))._2
      hist.version mustEqual 44
    }
    
    "transform incoming operations against intervening history" in {
      var hist = new OpHistory(Op(0))
      
      {
        val (op, hist2) = hist(Op(1).chars("test"))
        hist = hist2
        
        op.parent mustEqual 1
        op.version mustEqual 2
        
        op.delta.size mustEqual 1
        op.delta.getCharactersString(0) mustEqual "test"
      }
      
      {
        val (op, hist2) = hist(Op(2).retain(4).chars("ing"))
        hist = hist2
        
        op.parent mustEqual 2
        op.version mustEqual 3
        
        op.delta.size mustEqual 2
        op.delta.getRetainItemCount(0) mustEqual 4
        op.delta.getCharactersString(1) mustEqual "ing"
      }
      
      {
        val (op, hist2) = hist(Op(2).delete("test").chars("stomp"))
        hist = hist2
        
        op.parent mustEqual 3
        op.version mustEqual 4
        
        op.delta.size mustEqual 3
        op.delta.getDeleteCharactersString(0) mustEqual "test"
        op.delta.getCharactersString(1) mustEqual "stomp"
        op.delta.getRetainItemCount(2) mustEqual 3
      }
      
      {
        val (op, hist2) = hist(Op(1).chars("we are "))
        hist = hist2
        
        op.parent mustEqual 4
        op.version mustEqual 5
        
        op.delta.size mustEqual 2
        op.delta.getCharactersString(0) mustEqual "we are "
        op.delta.getRetainItemCount(1) mustEqual 8
      }
    }
  }
}
