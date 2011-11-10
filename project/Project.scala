import sbt._
import Keys._

object CCCPBuild extends Build {
  lazy val root = Project(id = "cccp", base = file(".")) aggregate(server, agent)
  lazy val server = Project(id = "cccp-server", base = file("server"))
  lazy val agent = Project(id = "cccp-agent", base = file("agent")) dependsOn server
  lazy val jeditClient = Project(id = "cccp-jedit-client", base = file("clients/jedit"))
  
  val stage = TaskKey[Unit]("stage", "Copy files into staging directory for a release.")
}

