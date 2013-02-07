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
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import ch.luklanis.esreceiver.connectionstate.ConnectionState;
import ch.luklanis.esreceiver.connectionstate.ConnectionStateChangedEvent;
import ch.luklanis.esreceiver.connectionstate.OnConnectionStateChangeListener;
import ch.luklanis.esreceiver.datareceived.DataReceivedEvent;
import ch.luklanis.esreceiver.datareceived.OnDataReceivedListener;

import com.crs.toolkit.layout.SWTGridData;
import com.crs.toolkit.layout.SWTGridLayout;
import com.sun.xml.internal.ws.util.StringUtils;

public class AppFrame extends JFrame implements ClipboardOwner {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1668274381664960966L;
	private static final String CURRENT_VERSION = "0.6.0";
	private static final String propertiesFile = System.getProperty("user.dir") + "/ESRReceiver.properties";
	
	private JButton connectButton;
	private JLabel connectionState;
	private TcpReceive tcpReceive;
	private JComboBox<ServiceDescription> devices;
	private JCheckBox autoPasteCheckBox;
	private Properties properties;
	private JTextArea clipboardData;
	private JCheckBox removeSpaceCheckBox;
	
	private JmDNS jmdns;
	private final String SERVICE_TYPE = "_esr._tcp.local";
	private ServiceListener serviceListener;
	
	private static class ServiceDescription {
		public String name;
		public String ipAddress;
		public int port;
		
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

		FileInputStream inputStream = null;
		boolean autoPaste = false;
		boolean removeSpace = false;
		properties = new Properties();

		try {
			inputStream = new FileInputStream(propertiesFile);
			properties.load(inputStream);
			autoPaste = properties.getProperty("autoPaste").equalsIgnoreCase("true");
			removeSpace = properties.getProperty("removeSpace").equalsIgnoreCase("true");
		} catch (Exception e) {
			saveProperty("autoPaste", String.valueOf(autoPaste));
			saveProperty("removeSpace", String.valueOf(removeSpace));
			e.printStackTrace();
		}
		finally {
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
		devices.addItem(new ServiceDescription("Please start the ESRScanner", "", 0));

		SWTGridData data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.FILL;
		body.add(devices, data);

		autoPasteCheckBox = new JCheckBox("Auto paste");
		autoPasteCheckBox.setSelected(autoPaste);
		autoPasteCheckBox.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				saveProperty("autoPaste", String.valueOf(autoPasteCheckBox.isSelected()));
			}
		});

		body.add(autoPasteCheckBox);

		removeSpaceCheckBox = new JCheckBox("Remove space");
		removeSpaceCheckBox.setSelected(removeSpace);
		removeSpaceCheckBox.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				saveProperty("removeSpace", String.valueOf(removeSpaceCheckBox.isSelected()));
			}
		});

		
		data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.RIGHT;
		body.add(removeSpaceCheckBox, data);

		connectButton = new JButton("Connect");
		connectButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (connectButton.getText().equalsIgnoreCase("connect")){
					ServiceDescription service = devices.getItemAt(devices.getSelectedIndex());
					
					if (service.ipAddress == null || service.ipAddress.isEmpty() || service.port == 0) {
						return;
					}
					
					connectButton.setText("Disconnect");
					connectionState.setText(ConnectionState.Connecting.name());
					tcpReceive.connect(service.ipAddress, service.port);
				} else {
					connectButton.setText("Connect");
					tcpReceive.close();
				}
			}
		});
		
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
		footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.BLACK));
		
		data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		connectionState = new JLabel(ConnectionState.Disconnected.name());
		footer.add(connectionState, data);

		JLabel version = new JLabel("Version: " + CURRENT_VERSION );
		version.setAlignmentX(JLabel.RIGHT_ALIGNMENT);
		footer.add(version);

		this.tcpReceive = new TcpReceive();
		this.tcpReceive.setOnConnectionStateChangeListener(new OnConnectionStateChangeListener() {

			@Override
			public void connectionStateChanged(ConnectionStateChangedEvent event) {
				connectionState.setText(event.getConnectionState().name());
			}
		});
		
		this.tcpReceive.setOnDataReceivedListener(new OnDataReceivedListener() {

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
		});
		
		try {
			jmdns = JmDNS.create();
		    jmdns.addServiceListener(SERVICE_TYPE, serviceListener = new ServiceListener() {
		        public void serviceResolved(ServiceEvent event) {
		        	ServiceInfo info = event.getInfo();
		        	
		        	String currentIpAddress = devices.getItemAt(devices.getSelectedIndex()).ipAddress;
		        	if (currentIpAddress == null || currentIpAddress.isEmpty()) {
		        		devices.removeAllItems();
		        	}
		        	
		        	devices.addItem(new ServiceDescription(info.getName(), info.getHostAddresses()[0], info.getPort()));
		        	
//		            notifyUser("Service resolved: "
//		                     + ev.getInfo().getQualifiedName()
//		                     + " port:" + ev.getInfo().getPort());
		        }
		        public void serviceRemoved(ServiceEvent event) {
//		            notifyUser("Service removed: " + ev.getName());
		        }
		        public void serviceAdded(ServiceEvent event) {
		            // Required to force serviceResolved to be called again
		            // (after the first search)
		            jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
		        }
		    });
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				if (jmdns != null) {
					jmdns.removeServiceListener(SERVICE_TYPE, serviceListener);
					try {
						jmdns.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
	}

	public static void main(String... args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (ClassNotFoundException e) {
				} catch (InstantiationException e) {
				} catch (IllegalAccessException e) {
				} catch (UnsupportedLookAndFeelException e) {
				}

				new AppFrame().setVisible(true);
			}
		});
	}

	@Override
	public void lostOwnership(Clipboard arg0, Transferable arg1) {
	}

	/**
	 * Place a String on the clipboard, and make this class the
	 * owner of the Clipboard's contents.
	 */
	public void setClipboardContents(String aString){
		StringSelection stringSelection = new StringSelection(aString);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents( stringSelection, this );
	}

	  /**
	  * Get the String residing on the clipboard.
	  *
	  * @return any text found on the Clipboard; if none found, return an
	  * empty String.
	  */
	  public String getClipboardContents() {
	    String result = "";
	    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
	    //odd: the Object param of getContents is not currently used
	    Transferable contents = clipboard.getContents(null);
	    boolean hasTransferableText =
	      (contents != null) &&
	      contents.isDataFlavorSupported(DataFlavor.stringFlavor)
	    ;
	    if ( hasTransferableText ) {
	      try {
	        result = (String)contents.getTransferData(DataFlavor.stringFlavor);
	      }
	      catch (UnsupportedFlavorException ex){
	        //highly unlikely since we are using a standard DataFlavor
	        System.out.println(ex);
	        ex.printStackTrace();
	      }
	      catch (IOException ex) {
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
		}
		finally {
			try {
				if (outputStream != null) {
					outputStream.close();
				}
			} catch (IOException e) {
			}
		}
	}
}
