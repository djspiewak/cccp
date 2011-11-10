name := "cccp-jedit-client"

unmanagedJars in Compile += {
  Attributed.blank(new File("/Applications/jEdit.app/Contents/Resources/Java/jedit.jar"))
}
