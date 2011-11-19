import IO._

name := "cccp-jedit-client"

unmanagedJars in Compile += {
  var jedit = new File(System.getenv("JEDIT_HOME") + "/jedit.jar")
  if (!jedit.exists) jedit = new File("/Applications/jEdit.app/Contents/Resources/Java/jedit.jar")
  if (!jedit.exists) jedit = new File("c:/Program Files/jEdit/jedit.jar")
  if (!jedit.exists) sys.error("jedit.jar was not found. please, set the JEDIT_HOME environment variable")
  Attributed.blank(jedit)
}

exportJars := true
  
stage <<= (dependencyClasspath in Runtime, exportedProducts in Runtime) map { (depCP, exportedCP) =>
  // this task "borrowed" from ENSIME (thanks, Aemon!)
  val jedit = Path("clients/jedit")
  val log = LogManager.defaultScreen
  delete(file("dist"))
  log.info("Copying runtime environment to ./dist....")
  createDirectories(List(
    file("clients/jedit/dist"), 
    file("clients/jedit/dist/lib")))
  // Copy the runtime jars
  val deps = (depCP ++ exportedCP).map(_.data)
  copy(deps x flat(jedit / "dist" / "lib"))
}
