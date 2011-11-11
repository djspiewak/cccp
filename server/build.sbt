import IO._

name := "cccp-server"

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
  
exportJars := true
  
stage <<= (dependencyClasspath in Runtime, exportedProducts in Runtime) map { (depCP, exportedCP) =>
  // this task "borrowed" from ENSIME (thanks, Aemon!)
  val server = Path("server")
  val log = LogManager.defaultScreen
  delete(file("dist"))
  log.info("Copying runtime environment to ./dist....")
  createDirectories(List(
    file("server/dist"), 
    file("server/dist/bin"),
    file("server/dist/lib")))
  // Copy the runtime jars
  val deps = (depCP ++ exportedCP).map(_.data)
  copy(deps x flat(server / "dist" / "lib"))
  // Grab all jars..
  val cpLibs = (server / "dist" / "lib" ** "*.jar").get.flatMap(_.relativeTo(server / "dist"))
  def writeScript(classpath:String, from:String, to:String) {
    val tmplF = new File(from)
    val tmpl = read(tmplF)
    val s = tmpl.replace("<RUNTIME_CLASSPATH>", classpath)
    val f = new File(to)
    write(f, s)
    f.setExecutable(true)
  }
  // Expand the server invocation script templates.
  writeScript(cpLibs.mkString(":").replace("\\", "/"), "server/bin/cccp-server", "server/dist/bin/cccp-server")
  writeScript("\"" + cpLibs.mkString(";").replace("/", "\\") + "\"", "server/bin/cccp-server.bat", "server/dist/bin/cccp-server.bat")
  // copyFile(root / "README.md", root / "dist" / "README.md")
  // copyFile(root / "LICENSE", root / "dist" / "LICENSE")
}
