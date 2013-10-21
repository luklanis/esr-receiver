package ch.luklanis.esreceiver;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import ch.luklanis.esreceiver.connectionstate.ConnectionState;
import ch.luklanis.esreceiver.connectionstate.ConnectionStateChangedEvent;
import ch.luklanis.esreceiver.connectionstate.OnConnectionStateChangeListener;
import ch.luklanis.esreceiver.datareceived.DataReceivedEvent;
import ch.luklanis.esreceiver.datareceived.OnDataReceivedListener;

import com.crs.toolkit.layout.SWTGridData;
import com.crs.toolkit.layout.SWTGridLayout;

public class AppFrame extends JFrame implements ClipboardOwner {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1668274381664960966L;
	private static final String CURRENT_VERSION = "0.6.4_http";
	private static final String propertiesFile = System.getProperty("user.dir")
			+ "/ESRReceiver.properties";

	private static final String SERVICE_TYPE = "_esr._tcp.local.";

	private final ComponentListener componentHiddenListener = new ComponentAdapter() {
		@Override
		public void componentHidden(ComponentEvent event) {
			if (!httpReceive.getCurrentState().equals(
					ConnectionState.Disconnected)) {
				httpReceive.close();
			}

			((JFrame) (event.getComponent())).dispose();
		}
	};

	private final ActionListener connectButtonClicked = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent arg0) {
			connectOrDisconnect();
		}
	};

	private final OnDataReceivedListener dataReceivedListener = new OnDataReceivedListener() {

		@Override
		public void dataReceived(DataReceivedEvent event) {
			String codeRow = event.getData();

			if (removeSpaceCheckBox.isSelected()) {
				codeRow = codeRow.replace(" ", "");
			}

			setClipboardContents(codeRow);

			if (autoPasteCheckBox.isSelected()) {
				Robot robot;
				try {
					robot = new Robot();
				} catch (AWTException e1) {
					e1.printStackTrace();
					return;
				}

				if (OSValidator.isWindows() || OSValidator.isUnix()) {
					// Ctrl-V on Win and Linux
					robot.keyPress(KeyEvent.VK_CONTROL);
					robot.keyPress(KeyEvent.VK_V);
					robot.keyRelease(KeyEvent.VK_V);
					robot.keyRelease(KeyEvent.VK_CONTROL);
					robot.keyPress(KeyEvent.VK_ENTER);
					robot.keyRelease(KeyEvent.VK_ENTER);
				} else if (OSValidator.isMac()) {
					// ⌘-V on Mac
					robot.keyPress(KeyEvent.VK_META);
					robot.keyPress(KeyEvent.VK_V);
					robot.keyRelease(KeyEvent.VK_V);
					robot.keyRelease(KeyEvent.VK_META);
				} else {
					throw new AssertionError("Not tested on " + OSValidator.OS);
				}
			}

			clipboardData.selectAll();
			clipboardData.paste();
		}
	};

	private JButton connectButton;
	private JLabel connectionState;
	private HttpReceive httpReceive;
	private JTextArea emailTextField;
	private JCheckBox autoPasteCheckBox;
	private Properties properties;
	private JTextArea clipboardData;
	private JCheckBox removeSpaceCheckBox;
	private JTextField keyTextField;

	public AppFrame() {
		super("ESR Receiver");

		//setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(280, 240);
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		addComponentListener(componentHiddenListener);

		FileInputStream inputStream = null;
		boolean autoPaste = false;
		boolean removeSpace = false;
		properties = new Properties();
		String password = "";
		String emailAddress = "";

		try {
			inputStream = new FileInputStream(propertiesFile);
			properties.load(inputStream);
			autoPaste = properties.getProperty("autoPaste").equalsIgnoreCase(
					"true");
			removeSpace = properties.getProperty("removeSpace")
					.equalsIgnoreCase("true");

			password = properties.getProperty("password");
			emailAddress = properties.getProperty("emailAddress");
		} catch (Exception e) {
			saveProperty("autoPaste", String.valueOf(autoPaste));
			saveProperty("removeSpace", String.valueOf(removeSpace));
			saveProperty("emailAddress", "");
			saveProperty("password", "");

			e.printStackTrace();
		} finally {
			try {
				if (inputStream != null) {
					inputStream.close();
				}
			} catch (IOException e) {
			}
		}

		JPanel body = new JPanel(new SWTGridLayout(2, false));
		getContentPane().add(body);

		body.add(new JLabel("Email address:"));

		emailTextField = new JTextArea();
        emailTextField.setText(emailAddress);

		SWTGridData data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.FILL;
		body.add(emailTextField, data);

		body.add(new JLabel("Password:"));

		keyTextField = new JTextField();
        keyTextField.setText(password);

		data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.FILL;
		body.add(keyTextField, data);

		autoPasteCheckBox = new JCheckBox("Auto paste");
		autoPasteCheckBox.setSelected(autoPaste);
		autoPasteCheckBox.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				saveProperty("autoPaste",
						String.valueOf(autoPasteCheckBox.isSelected()));
			}
		});

		body.add(autoPasteCheckBox);

		removeSpaceCheckBox = new JCheckBox("Remove space");
		removeSpaceCheckBox.setSelected(removeSpace);
		removeSpaceCheckBox.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				saveProperty("removeSpace",
						String.valueOf(removeSpaceCheckBox.isSelected()));
			}
		});

		data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.RIGHT;
		body.add(removeSpaceCheckBox, data);

		connectButton = new JButton("Connect");
		connectButton.addActionListener(connectButtonClicked);

		getRootPane().setDefaultButton(connectButton);

		data = new SWTGridData();
		data.horizontalSpan = 2;
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.RIGHT;

		body.add(connectButton, data);

		body.add(Box.createVerticalStrut(5));

		JLabel clipboard = new JLabel("Current coderow on the clipboard:");
		data = new SWTGridData();
		data.horizontalSpan = 2;
		body.add(clipboard, data);

		clipboardData = new JTextArea("");
		clipboardData.setLineWrap(true);
		data = new SWTGridData();
		data.horizontalSpan = 2;
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.FILL;
		body.add(clipboardData, data);

		JPanel footer = new JPanel(new SWTGridLayout(2, false));
		getContentPane().add(footer, BorderLayout.SOUTH);
		footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
				Color.BLACK));

		data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		connectionState = new JLabel(ConnectionState.Disconnected.name());
		footer.add(connectionState, data);

		JLabel version = new JLabel("Version: " + CURRENT_VERSION);
		version.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
		footer.add(version);

		this.httpReceive = new HttpReceive();
		this.httpReceive
				.setOnConnectionStateChangeListener(new OnConnectionStateChangeListener() {

					@Override
					public void connectionStateChanged(
							ConnectionStateChangedEvent event) {
						connectionState.setText(event.getConnectionState()
								.name());

						if (event.getConnectionState()
								.equals(ConnectionState.Disconnected) || 
								event.getConnectionState()
								.equals(ConnectionState.AuthenticationError)) {
							connectButton.setText("Connect");
							keyTextField.setEnabled(true);
							emailTextField.setEnabled(true);
						} else {
							connectButton.setText("Disconnect");
							keyTextField.setEnabled(false);
							emailTextField.setEnabled(false);
						}
					}
				});

		this.httpReceive.setOnDataReceivedListener(dataReceivedListener);
	}

	public static void main(String... args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager
							.getSystemLookAndFeelClassName());
				} catch (ClassNotFoundException e) {
				} catch (InstantiationException e) {
				} catch (IllegalAccessException e) {
				} catch (UnsupportedLookAndFeelException e) {
				}

				AppFrame appFrame = new AppFrame();
				appFrame.setVisible(true);
			}
		});
	}

	@Override
	public void lostOwnership(Clipboard arg0, Transferable arg1) {
	}

	/**
	 * Place a String on the clipboard, and make this class the owner of the
	 * Clipboard's contents.
	 */
	public void setClipboardContents(String aString) {
		StringSelection stringSelection = new StringSelection(aString);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(stringSelection, this);
	}

	/**
	 * Get the String residing on the clipboard.
	 * 
	 * @return any text found on the Clipboard; if none found, return an empty
	 *         String.
	 */
	public String getClipboardContents() {
		String result = "";
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		// odd: the Object param of getContents is not currently used
		Transferable contents = clipboard.getContents(null);
		boolean hasTransferableText = (contents != null)
				&& contents.isDataFlavorSupported(DataFlavor.stringFlavor);
		if (hasTransferableText) {
			try {
				result = (String) contents
						.getTransferData(DataFlavor.stringFlavor);
			} catch (UnsupportedFlavorException ex) {
				// highly unlikely since we are using a standard DataFlavor
				System.out.println(ex);
				ex.printStackTrace();
			} catch (IOException ex) {
				System.out.println(ex);
				ex.printStackTrace();
			}
		}
		return result;
	}

	protected void saveProperty(String property, String value) {
		FileOutputStream outputStream = null;
		try {
			properties.setProperty(property, value);
			outputStream = new FileOutputStream(propertiesFile);
			properties.store(outputStream, "update " + property);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (outputStream != null) {
					outputStream.close();
				}
			} catch (IOException e) {
			}
		}
	}

	private void connect() {
		String emailAddress = emailTextField.getText();
		String password = keyTextField.getText();

		if (emailAddress == null || emailAddress.isEmpty() ||
				password == null || password.isEmpty()) {
			return;
		}

		saveProperty("emailAddress", emailAddress);
		saveProperty("password", password);

		httpReceive.connect(emailAddress, password);
	}

	private void disconnect() {
		httpReceive.close();
	}

	private void connectOrDisconnect() {
		if (httpReceive.getCurrentState().equals(ConnectionState.Disconnected)) {
			connect();
		} else {
			disconnect();
		}
	}
}
