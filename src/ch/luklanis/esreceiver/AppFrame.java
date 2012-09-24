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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
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

public class AppFrame extends JFrame implements ClipboardOwner {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1668274381664960966L;
	private JButton saveButton;
	private JLabel connectionState;
	private TcpReceive tcpReceive;
	private JTextField ipAddress;
	private JCheckBox autoPasteCheckBox;
	private Properties properties;
	private JTextArea clipboardData;
	private static final String propertiesFile = System.getProperty("user.dir") + "/ESRReceiver.properties";

	public AppFrame() {
		super("ESR Receiver");

		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(280, 200);

		FileInputStream inputStream = null;
		String host = "";
		boolean autoPaste = false;
		properties = new Properties();

		try {
			inputStream = new FileInputStream(propertiesFile);
			properties.load(inputStream);        
			host = properties.getProperty("host");
			autoPaste = properties.getProperty("autoPaste").equalsIgnoreCase("true");
		} catch (Exception e) {
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

		body.add(new JLabel("IP Address:"));

		ipAddress = new JTextField();
		ipAddress.setText(host);

		SWTGridData data = new SWTGridData();
		data.grabExcessHorizontalSpace = true;
		data.horizontalAlignment = SWTGridData.FILL;
		body.add(ipAddress, data);

		autoPasteCheckBox = new JCheckBox("Auto paste");
		autoPasteCheckBox.setSelected(autoPaste);
		autoPasteCheckBox.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				saveProperty("autoPaste", String.valueOf(autoPasteCheckBox.isSelected()));
			}
		});

		body.add(autoPasteCheckBox);

		saveButton = new JButton("Connect");
		saveButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (saveButton.getText().equalsIgnoreCase("connect")){
					saveProperty("host", ipAddress.getText());
					saveButton.setText("Disconnect");
					connectionState.setText(ConnectionState.Connecting.name());
					tcpReceive.connect(ipAddress.getText());
				} else {
					saveButton.setText("Connect");
					tcpReceive.close();
				}
			}
		});
		getRootPane().setDefaultButton(saveButton);
		body.add(saveButton);

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

		JLabel version = new JLabel("Version: 0.5.5");
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

				setClipboardContents(codeRow);

				if (autoPasteCheckBox.isSelected()) {
					Robot robot;
					try {
						robot = new Robot();
					} catch (AWTException e1) {
						e1.printStackTrace();
						return;
					}
					robot.setAutoDelay(5);
					
					robot.keyPress(KeyEvent.VK_CONTROL);
					robot.keyPress(KeyEvent.VK_V);
					robot.keyRelease(KeyEvent.VK_CONTROL);
					robot.keyPress(KeyEvent.VK_ENTER);
					robot.keyRelease(KeyEvent.VK_ENTER);
				}
				
				clipboardData.selectAll();
				clipboardData.paste();
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
