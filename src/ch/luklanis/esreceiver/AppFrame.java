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

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
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
	private static final String CURRENT_VERSION = "0.6.2";
	private static final String propertiesFile = System.getProperty("user.dir")
			+ "/ESRReceiver.properties";

	private static final String SERVICE_TYPE = "_esr._tcp.local.";

	private ArrayList<JmDNS> jmdns = new ArrayList<JmDNS>();
	private final ServiceListener serviceListener = new ServiceListener() {
		public void serviceResolved(ServiceEvent event) {
			System.out.println("RESOLVED: " + event.getName());

			ServiceInfo info = event.getInfo();

			boolean autoConnect = ((ServiceDescription) devices
					.getSelectedItem()).port == 1;

			ServiceDescription newDevice = new ServiceDescription(
					info.getNiceTextString(), info.getHostAddresses()[0],
					info.getPort());

			try {
				int itemCount = devices.getItemCount();
				boolean updateSelectedIndex = false;

				try {
					if (devices.getItemAt(0).port <= 1) {
						devices.remove(0);
					} else {
						for (int i = 0; i < itemCount; i++) {
							if (!devices.getItemAt(i).ipAddress.isEmpty()
									&& devices.getItemAt(i).ipAddress
											.equals(newDevice.ipAddress)) {

								if (devices.getSelectedIndex() == i) {
									updateSelectedIndex = true;
								}

								devices.remove(i);
								break;
							}
						}
					}
				} catch (Exception ex) {
				}

				devices.addItem(newDevice);

				if (updateSelectedIndex) {
					devices.setSelectedIndex(devices.getItemCount() - 1);
				}

				if (autoConnect) {
					devices.setSelectedIndex(devices.getItemCount() - 1);
					connectIfNotConnected();
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		public void serviceRemoved(ServiceEvent event) {
			// notifyUser("Service removed: " +
			// ev.getName());
		}

		public void serviceAdded(ServiceEvent event) {
			System.out.println("ADDED: " + event.getName());

			event.getDNS().requestServiceInfo(event.getType(), event.getName(),
					1);
		}
	};

	private final ComponentListener componentHiddenListener = new ComponentAdapter() {
		@Override
		public void componentHidden(ComponentEvent event) {
			if (!tcpReceive.getCurrentState().equals(
					ConnectionState.Disconnected)) {
				tcpReceive.close();
			}

			for (int i = 0; i < jmdns.size(); i++) {
				JmDNS jd = jmdns.get(i);
				jd.removeServiceListener(SERVICE_TYPE, serviceListener);
				try {
					jd.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}

			jmdns.clear();

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
	private TcpReceive tcpReceive;
	private JComboBox<ServiceDescription> devices;
	private JCheckBox autoPasteCheckBox;
	private Properties properties;
	private JTextArea clipboardData;
	private JCheckBox removeSpaceCheckBox;
	private JTextField hostInterface;

	private static class ServiceDescription {
		public final String name;
		public final String ipAddress;
		public final int port;

		public ServiceDescription(String name, String ipAddress, int port) {
			this.name = name;
			this.ipAddress = ipAddress;
			this.port = port;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public AppFrame() {
		super("ESR Receiver");

		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(280, 240);
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		addComponentListener(componentHiddenListener);

		FileInputStream inputStream = null;
		boolean autoPaste = false;
		boolean removeSpace = false;
		properties = new Properties();
		int port = 0;
		String ipAddress = "";

		try {
			inputStream = new FileInputStream(propertiesFile);
			properties.load(inputStream);
			autoPaste = properties.getProperty("autoPaste").equalsIgnoreCase(
					"true");
			removeSpace = properties.getProperty("removeSpace")
					.equalsIgnoreCase("true");

			port = Integer.parseInt(properties.getProperty("port"));
			ipAddress = properties.getProperty("ipAddress");
		} catch (Exception e) {
			saveProperty("autoPaste", String.valueOf(autoPaste));
			saveProperty("removeSpace", String.valueOf(removeSpace));
			saveProperty("ipAddress", "");
			saveProperty("port", String.valueOf(port));

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

		body.add(new JLabel("Device:"));

		devices = new JComboBox<AppFrame.ServiceDescription>();
		devices.addItem(new ServiceDescription(
				"Please start the ESR Scanner...", "", 1));

		devices.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					ServiceDescription selectedService = (ServiceDescription) e
							.getItem();

					if (selectedService.port > 1) {
						if (tcpReceive.getCurrentState().compareTo(
								ConnectionState.Disconnected) > 0) {
							disconnect();
						}

						String connection = String
								.format("%s:%d", selectedService.ipAddress,
										selectedService.port);
						hostInterface.setText(connection);
						hostInterface.invalidate();
					}
				}
			}
		});

		SWTGridData data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.FILL;
		body.add(devices, data);

		body.add(new JLabel("Host (ip:port):"));

		hostInterface = new JTextField();

		data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.FILL;
		body.add(hostInterface, data);

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

		this.tcpReceive = new TcpReceive();
		this.tcpReceive
				.setOnConnectionStateChangeListener(new OnConnectionStateChangeListener() {

					@Override
					public void connectionStateChanged(
							ConnectionStateChangedEvent event) {
						connectionState.setText(event.getConnectionState()
								.name());

						if (event.getConnectionState().equals(
								ConnectionState.Disconnected)) {
							connectButton.setText("Connect");
							hostInterface.setEnabled(true);
						} else {
							connectButton.setText("Disconnect");
							hostInterface.setEnabled(false);
						}
					}
				});

		this.tcpReceive.setOnDataReceivedListener(dataReceivedListener);

		try {
			Enumeration<NetworkInterface> nets = NetworkInterface
					.getNetworkInterfaces();
			for (NetworkInterface netint : Collections.list(nets)) {

				if (netint.isLoopback() || !netint.isUp()) {
					continue;
				}

				Enumeration<InetAddress> inetAddresses = netint
						.getInetAddresses();

				for (InetAddress inetAddress : Collections.list(inetAddresses)) {
					if (inetAddress instanceof Inet6Address) {
						continue;
					}

					JmDNS jd = JmDNS.create(inetAddress, inetAddress
							.getHostName().toLowerCase());
					jd.addServiceListener(SERVICE_TYPE, serviceListener);
					jmdns.add(jd);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (port > 1) {
			hostInterface.setText(String.format("%s:%d", ipAddress, port));
			connectIfNotConnected();
		}
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
		String[] inetAddress = hostInterface.getText().split(":");
		String ipAddress = inetAddress[0];
		int port = Integer.parseInt(inetAddress[1]);

		if (ipAddress == null || ipAddress.isEmpty() || port <= 1) {
			return;
		}

		saveProperty("ipAddress", ipAddress);
		saveProperty("port", String.valueOf(port));

		tcpReceive.connect(ipAddress, port);
	}

	private void disconnect() {
		tcpReceive.close();
	}

	private void connectIfNotConnected() {
		if (tcpReceive.getCurrentState().equals(ConnectionState.Connecting)) {
			disconnect();
		}

		if (tcpReceive.getCurrentState()
				.compareTo(ConnectionState.Disconnected) == 0) {
			connect();
		}
	}

	private void connectOrDisconnect() {
		if (tcpReceive.getCurrentState().equals(ConnectionState.Disconnected)) {
			connect();
		} else {
			disconnect();
		}
	}
}
