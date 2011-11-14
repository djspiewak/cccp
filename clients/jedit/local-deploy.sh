#!/bin/bash

JEDIT_HOME=~/Library/jEdit
[ ! -d $JEDIT_HOME ] && JEDIT_HOME=$1

if [ ! -d $JEDIT_HOME ]; then
  echo 'Must specify a valid destination for jEdit plugin JARs!'
  exit -1
fi

cp dist/lib/cccp-jedit-client_2.9.1-0.1.jar $JEDIT_HOME/jars/CCCP.jar
cp dist/lib/scala-library.jar $JEDIT_HOME/jars/scala-library.jar
