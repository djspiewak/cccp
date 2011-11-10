import IO._

name := "cccp-agent"

libraryDependencies ++= Seq(
  "se.scalablesolutions.akka" % "akka-actor" % "1.2",
  "com.reportgrid" %% "blueeyes" % "0.4.24",
  "org.specs2" %% "specs2" % "1.7-SNAPSHOT" % "test")

resolvers ++= Seq(
  "Sonatype"    at "http://nexus.scala-tools.org/content/repositories/public",
  "Scala Tools" at "http://scala-tools.org/repo-snapshots/",
  "JBoss"       at "http://repository.jboss.org/nexus/content/groups/public/",
  "Akka"        at "http://akka.io/repository/",
  "GuiceyFruit" at "http://guiceyfruit.googlecode.com/svn/repo/releases/")
  
stage <<= (dependencyClasspath in Runtime, exportedProducts in Runtime) map { (depCP, exportedCP) =>
  // this task "borrowed" from ENSIME (thanks, Aemon!)
  val agent = Path("agent")
  val log = LogManager.defaultScreen
  delete(file("dist"))
  log.info("Copying runtime environment to ./dist....")
  createDirectories(List(
    file("agent/dist"), 
    file("agent/dist/bin"),
    file("agent/dist/lib")))
  // Copy the runtime jars
  val deps = (depCP ++ exportedCP).map(_.data)
  copy(deps x flat(agent / "dist" / "lib"))
  // Grab all jars..
  val cpLibs = (agent / "dist" / "lib" ** "*.jar").get.flatMap(_.relativeTo(agent / "dist"))
  def writeScript(classpath:String, from:String, to:String) {
    val tmplF = new File(from)
    val tmpl = read(tmplF)
    val s = tmpl.replace("<RUNTIME_CLASSPATH>", classpath)
    val f = new File(to)
    write(f, s)
    f.setExecutable(true)
  }
  // Expand the server invocation script templates.
  writeScript(cpLibs.mkString(":").replace("\\", "/"), "agent/bin/server", "agent/dist/bin/server")
  writeScript("\"" + cpLibs.mkString(";").replace("/", "\\") + "\"", "agent/bin/server.bat", "agent/dist/bin/server.bat")
  // copyFile(root / "README.md", root / "dist" / "README.md")
  // copyFile(root / "LICENSE", root / "dist" / "LICENSE")
}
