/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.provisioning

import net.java.sip.communicator.plugin.desktoputil.SIPCommTextField
import net.java.sip.communicator.plugin.desktoputil.TransparentPanel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JRadioButton
import javax.swing.JTextField

/**
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class ProvisioningForm
/**
 * Creates an instance of the `ProvisioningForm`.
 */
    : TransparentPanel() {
    /**
     * The enable provisioning check box.
     */
    private val enableCheckBox: JCheckBox? = null

    /**
     * The DHCP provisioning discovery button.
     */
    private val dhcpButton: JRadioButton? = null

    /**
     * The DNS provisioning discovery button.
     */
    private val dnsButton: JRadioButton? = null

    /**
     * The Bonjour provisioning discovery button.
     */
    private val bonjourButton: JRadioButton? = null

    /**
     * The manual provisioning button.
     */
    private val manualButton: JRadioButton? = null

    /**
     * The URI field to specify manually a provisioning server.
     */
    private val uriField: SIPCommTextField? = null

    /**
     * The field used to show the username.
     */
    private val usernameField: JTextField? = null
    /**
     * A field to show the password.
     */
    // private final JPasswordField passwordField;
    /**
     * The button that will delete the password.
     */
    private val forgetPasswordButton: JButton? = null

    /**
     * Initializes all contained components.
     */
    private fun initButtonStates() {
//		String provMethod = ProvisioningActivator.getProvisioningService().getProvisioningMethod();
//		boolean isProvEnabled = (provMethod != null && provMethod.length() > 0 && !provMethod.equals("NONE"));
//
//		enableCheckBox.setSelected(isProvEnabled);
//
//		if (isProvEnabled) {
//			if (provMethod.equals("DHCP"))
//				dhcpButton.setSelected(true);
//			else if (provMethod.equals("DNS"))
//				dnsButton.setSelected(true);
//			else if (provMethod.equals("Bonjour"))
//				bonjourButton.setSelected(true);
//			else if (provMethod.equals("Manual")) {
//				manualButton.setSelected(true);
//
//				String uri = ProvisioningActivator.getProvisioningService().getProvisioningUri();
//				if (uri != null)
//					uriField.setText(uri);
//			}
//		}
//
//		dhcpButton.setEnabled(isProvEnabled);
//		manualButton.setEnabled(isProvEnabled);
//		uriField.setEnabled(manualButton.isSelected());
//		bonjourButton.setEnabled(isProvEnabled);
//		dnsButton.setEnabled(false);
//
//		// creadentials
//		forgetPasswordButton.setEnabled(isProvEnabled);
//		usernameField.setText(ProvisioningActivator.getConfigurationService().getString(ProvisioningServiceImpl.PROPERTY_PROVISIONING_USERNAME));
//
//		if (ProvisioningActivator.getCredentialsStorageService().isStoredEncrypted(ProvisioningServiceImpl.PROPERTY_PROVISIONING_PASSWORD)) {
//			passwordField.setText(ProvisioningActivator.getCredentialsStorageService().loadPassword(ProvisioningServiceImpl.PROPERTY_PROVISIONING_PASSWORD));
//		}
    }

    /**
     * Initializes all listeners.
     */
    private fun initListeners() {
//		enableCheckBox.addChangeListener(new ChangeListener() {
//			public void stateChanged(ChangeEvent e)
//			{
//				boolean isSelected = enableCheckBox.isSelected();
//
//				dhcpButton.setEnabled(isSelected);
//				bonjourButton.setEnabled(isSelected);
//				manualButton.setEnabled(isSelected);
//				forgetPasswordButton.setEnabled(isSelected);
//
//				String provisioningMethod = null;
//
//				if (isSelected) {
//					if (dhcpButton.isSelected()) {
//						provisioningMethod = "DHCP";
//					}
//					else if (dnsButton.isSelected()) {
//						provisioningMethod = "DNS";
//					}
//					else if (bonjourButton.isSelected()) {
//						provisioningMethod = "Bonjour";
//					}
//					else if (manualButton.isSelected()) {
//						provisioningMethod = "Manual";
//					}
//					else {
//						dhcpButton.setSelected(true);
//						provisioningMethod = "DHCP";
//					}
//				}
//
//				ProvisioningActivator.getProvisioningService().setProvisioningMethod(provisioningMethod);
//			}
//		});
//
//		dhcpButton.addChangeListener(new ChangeListener() {
//			public void stateChanged(ChangeEvent e)
//			{
//				if (dhcpButton.isSelected())
//					ProvisioningActivator.getProvisioningService().setProvisioningMethod("DHCP");
//			}
//		});
//
//		dnsButton.addChangeListener(new ChangeListener() {
//			public void stateChanged(ChangeEvent e)
//			{
//				if (dnsButton.isSelected())
//					ProvisioningActivator.getProvisioningService().setProvisioningMethod("DNS");
//			}
//		});
//
//		bonjourButton.addChangeListener(new ChangeListener() {
//			public void stateChanged(ChangeEvent e)
//			{
//				if (bonjourButton.isSelected())
//					ProvisioningActivator.getProvisioningService().setProvisioningMethod("Bonjour");
//			}
//		});
//
//		manualButton.addChangeListener(new ChangeListener() {
//			public void stateChanged(ChangeEvent e)
//			{
//				boolean isSelected = manualButton.isSelected();
//
//				uriField.setEnabled(isSelected);
//
//				if (isSelected) {
//					ProvisioningActivator.getProvisioningService().setProvisioningMethod("Manual");
//
//					String uriText = uriField.getText();
//					if (uriText != null && uriText.length() > 0)
//						ProvisioningActivator.getProvisioningService().setProvisioningUri(uriText);
//				}
//				else {
//					ProvisioningActivator.getProvisioningService().setProvisioningUri(null);
//				}
//			}
//		});
//
//		uriField.addFocusListener(new FocusListener() {
//			public void focusLost(FocusEvent e)
//			{
//				// If the manual button isn't selected we have nothing more
//				// to do here.
//				if (!manualButton.isSelected())
//					return;
//
//				String uriText = uriField.getText();
//				if (uriText != null && uriText.length() > 0)
//					ProvisioningActivator.getProvisioningService().setProvisioningUri(uriText);
//			}
//
//			public void focusGained(FocusEvent e)
//			{
//			}
//		});
//
//		forgetPasswordButton.addActionListener(new ActionListener() {
//			public void actionPerformed(ActionEvent actionEvent)
//			{
//				if (passwordField.getPassword() == null || passwordField.getPassword().length == 0) {
//					return;
//				}
//
//				int result = JOptionPane.showConfirmDialog((Component) ProvisioningActivator.getUIService().getExportedWindow(ExportedWindow.MAIN_WINDOW)
//					.getSource(), ProvisioningActivator.getResourceService().getI18NString("plugin.provisioning.REMOVE_CREDENTIALS_MESSAGE"),
//					ProvisioningActivator.getResourceService().getI18NString("service.gui.REMOVE"), JOptionPane.YES_NO_OPTION);
//
//				if (result == JOptionPane.YES_OPTION) {
//					ProvisioningActivator.getCredentialsStorageService().removePassword(ProvisioningServiceImpl.PROPERTY_PROVISIONING_PASSWORD);
//					ProvisioningActivator.getConfigurationService().removeProperty(ProvisioningServiceImpl.PROPERTY_PROVISIONING_USERNAME);
//
//					usernameField.setText("");
//					passwordField.setText("");
//				}
//			}
//		});
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}