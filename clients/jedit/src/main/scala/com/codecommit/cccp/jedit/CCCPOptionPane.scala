package com.codecommit.cccp
package jedit

import org.gjt.sp.jedit
import jedit.{jEdit => JEdit, AbstractOptionPane}
import jedit.browser.VFSFileChooserDialog

import java.awt.BorderLayout
import java.awt.event.{ActionEvent, ActionListener}

import javax.swing._

class CCCPOptionPane extends AbstractOptionPane("ensime") {
  private val homeField = new JTextField(CCCPPlugin.Home.getCanonicalPath)
  private val protocolField = new JTextField(CCCPPlugin.Protocol)
  private val hostField = new JTextField(CCCPPlugin.Host)
  private val portField = new JTextField(CCCPPlugin.Port.toString)
  
  override def _init() {
    val homePanel = new JPanel(new BorderLayout)
    addComponent("CCCP Agent Home", homePanel)
    
    homePanel.add(homeField)
    
    val button = new JButton("...")
    button.addActionListener(new ActionListener {
      def actionPerformed(e: ActionEvent) {
        val view = JEdit.getActiveView      // can we do without this?
        val dialog = new VFSFileChooserDialog(view, homeField.getText, 0, false, true)
        dialog.getSelectedFiles.headOption foreach homeField.setText
      }
    })
    homePanel.add(button, BorderLayout.EAST)
    
    addComponent("Protocol", protocolField)
    addComponent("Host", hostField)
    addComponent("Port", portField)
  }
  
  override def _save() {
    JEdit.setProperty(CCCPPlugin.HomeProperty, homeField.getText)
    JEdit.setProperty(CCCPPlugin.ProtocolProperty, protocolField.getText)
    JEdit.setProperty(CCCPPlugin.HostProperty, hostField.getText)
    JEdit.setProperty(CCCPPlugin.PortProperty, portField.getText)
    
    CCCPPlugin.reinit()
  }
}
