package ch.luklanis.esreceiver;

import ch.luklanis.esreceiver.connectionstate.ConnectionState;
import ch.luklanis.esreceiver.connectionstate.ConnectionStateChangedEvent;
import ch.luklanis.esreceiver.connectionstate.OnConnectionStateChangeListener;
import ch.luklanis.esreceiver.datareceived.DataReceivedEvent;
import ch.luklanis.esreceiver.datareceived.OnDataReceivedListener;
import com.crs.toolkit.layout.SWTGridData;
import com.crs.toolkit.layout.SWTGridLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class AppFrame extends JFrame implements ClipboardOwner {

    /**
     *
     */
    private static final long serialVersionUID = -1668274381664960966L;
    private static final String CURRENT_VERSION = "0.6.4_http";
    private static final String propertiesFile = System.getProperty("user.dir")
            + "/ESRReceiver.properties";

//    private final ComponentListener componentHiddenListener = new ComponentAdapter() {
//        @Override
//        public void componentHidden(ComponentEvent event) {
//            if (!httpReceive.getCurrentState().equals(
//                    ConnectionState.Disconnected)) {
//                httpReceive.close();
//            }
//
//            ((JFrame) (event.getComponent())).dispose();
//        }
//    };

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

//            if (removeSpaceCheckBox.isSelected()) {
//                codeRow = codeRow.replace(" ", "");
//            }

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
                } else if (OSValidator.isMac()) {
                    // ⌘-V on Mac
                    robot.keyPress(KeyEvent.VK_META);
                    robot.keyPress(KeyEvent.VK_V);
                    robot.keyRelease(KeyEvent.VK_V);
                    robot.keyRelease(KeyEvent.VK_META);
                } else {
                    throw new AssertionError("Not tested on " + OSValidator.OS);
                }

                if (addKeysComboBox.getSelectedItem().toString().contains("Tab")) {
                    robot.keyPress(KeyEvent.VK_TAB);
                    robot.keyRelease(KeyEvent.VK_TAB);
                }

                if (addKeysComboBox.getSelectedItem().toString().contains("Enter")) {
                    robot.keyPress(KeyEvent.VK_ENTER);
                    robot.keyRelease(KeyEvent.VK_ENTER);
                }
            }

            clipboardData.selectAll();
            clipboardData.paste();
        }
    };

    private JButton connectButton;
    private JLabel connectionState;
    private HttpReceive httpReceive;
    private JTextField emailTextField;
    private JCheckBox autoPasteCheckBox;
    private Properties properties;
    private JTextArea clipboardData;
    private JPasswordField passwordField;
    private JComboBox<String> addKeysComboBox;

    public AppFrame() {
        super("ESR Receiver");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(380, 240);
        //setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        //addComponentListener(componentHiddenListener);

        FileInputStream inputStream = null;
        boolean autoPaste = false;
        boolean removeSpace = false;
        properties = new Properties();
        String password = "";
        String emailAddress = "";
        String addKeys = "";

        try {
            inputStream = new FileInputStream(propertiesFile);
            properties.load(inputStream);
            autoPaste = properties.getProperty("autoPaste").equalsIgnoreCase(
                    "true");
            addKeys = properties.getProperty("addKeys");
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
                e.printStackTrace();
            }
        }

        JPanel body = new JPanel(new SWTGridLayout(2, false));
        getContentPane().add(body);

        body.add(new JLabel("Email address:"));

        emailTextField = new JTextField();
        emailTextField.setText(emailAddress);

        SWTGridData data = new SWTGridData();
        data.grabExcessHorizontalSpace = true;
        data.horizontalAlignment = SWTGridData.FILL;
        body.add(emailTextField, data);

        body.add(new JLabel("Password:"));

        passwordField = new JPasswordField();
        passwordField.setText(password);

        data = new SWTGridData();
        data.grabExcessHorizontalSpace = true;
        data.horizontalAlignment = SWTGridData.FILL;
        body.add(passwordField, data);

        autoPasteCheckBox = new JCheckBox("Auto paste");
        autoPasteCheckBox.setSelected(autoPaste);
        autoPasteCheckBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                boolean isSelected = autoPasteCheckBox.isSelected();
                saveProperty("autoPaste",
                        String.valueOf(isSelected));
                addKeysComboBox.setEnabled(isSelected);
            }
        });

        body.add(autoPasteCheckBox);

        JPanel keys = new JPanel(new SWTGridLayout(3, false));
        body.add(keys);

        keys.add(new JLabel("Add"));

        addKeysComboBox = new JComboBox<String>();
        addKeysComboBox.setEnabled(autoPaste);
        addKeysComboBox.addItem("Nothing");
        addKeysComboBox.addItem("<Enter>");
        addKeysComboBox.addItem("<Tab><Enter>");

        if (!addKeys.isEmpty()) {
            addKeysComboBox.setSelectedItem(addKeys);
        }

        addKeysComboBox.addItemListener(new ItemListener() {

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    saveProperty("addKeys", e.getItem().toString());
                }
            }
        });

        data = new SWTGridData();
        data.grabExcessHorizontalSpace = true;
//        keys.add(addKeysComboBox, data);
        keys.add(addKeysComboBox);

        keys.add(new JLabel("after paste"));

//        removeSpaceCheckBox = new JCheckBox("Remove space");
//        removeSpaceCheckBox.setSelected(removeSpace);
//        removeSpaceCheckBox.addActionListener(new ActionListener() {
//
//            @Override
//            public void actionPerformed(ActionEvent arg0) {
//                saveProperty("removeSpace",
//                        String.valueOf(removeSpaceCheckBox.isSelected()));
//            }
//        });

//        data = new SWTGridData();
//        data.grabExcessHorizontalSpace = true;
//        data.horizontalAlignment = SWTGridData.RIGHT;
//        body.add(removeSpaceCheckBox, data);

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
                            passwordField.setEnabled(true);
                            emailTextField.setEnabled(true);
                        } else {
                            connectButton.setText("Disconnect");
                            passwordField.setEnabled(false);
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
                } catch (Exception e) {
                    e.printStackTrace();
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

    //    /**
//     * Get the String residing on the clipboard.
//     *
//     * @return any text found on the Clipboard; if none found, return an empty
//     *         String.
//     */
//    public String getClipboardContents() {
//        String result = "";
//        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
//        // odd: the Object param of getContents is not currently used
//        Transferable contents = clipboard.getContents(null);
//        boolean hasTransferableText = (contents != null)
//                && contents.isDataFlavorSupported(DataFlavor.stringFlavor);
//        if (hasTransferableText) {
//            try {
//                result = (String) contents
//                        .getTransferData(DataFlavor.stringFlavor);
//            } catch (UnsupportedFlavorException ex) {
//                // highly unlikely since we are using a standard DataFlavor
//                System.out.println(ex);
//                ex.printStackTrace();
//            } catch (IOException ex) {
//                System.out.println(ex);
//                ex.printStackTrace();
//            }
//        }
//        return result;
//    }
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
                e.printStackTrace();
            }
        }
    }

    private void connect() {
        String emailAddress = emailTextField.getText();
        String password = new String(passwordField.getPassword());

        if (emailAddress == null || emailAddress.isEmpty() ||
                password.isEmpty()) {
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
