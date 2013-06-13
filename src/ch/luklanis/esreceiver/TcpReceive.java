package ch.luklanis.esreceiver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ch.luklanis.esreceiver.connectionstate.ConnectionState;
import ch.luklanis.esreceiver.connectionstate.ConnectionStateChangedEvent;
import ch.luklanis.esreceiver.connectionstate.OnConnectionStateChangeListener;
import ch.luklanis.esreceiver.datareceived.DataReceivedEvent;
import ch.luklanis.esreceiver.datareceived.OnDataReceivedListener;

public class TcpReceive implements Runnable {

	// Declaration section
	// clientClient: the client socket
	// os: the output stream
	// is: the input stream
	private static final AtomicBoolean close = new AtomicBoolean(false);
	private static final AtomicInteger currentState = new AtomicInteger(
			ConnectionState.Disconnected.ordinal());
	
	private static final String KEEP_ALIVE = "KA";
	private static final String STOP_CONNECTION = "STOP";
	private static final String START_SERVER = "START";

	private static final String ACK = "ACK";

	static Socket clientSocket = null;

	private OnConnectionStateChangeListener onConnectionStateChangeListener;
	private OnDataReceivedListener onDataReceivedListener;
	private String host;
	private int port;
	private static Thread mReceiveDataThread;

	public void setOnConnectionStateChangeListener(
			OnConnectionStateChangeListener listener) {
		onConnectionStateChangeListener = listener;
	}

	public void setOnDataReceivedListener(OnDataReceivedListener listener) {
		onDataReceivedListener = listener;
	}

	public void close() {
		close.set(true);

		if (mReceiveDataThread != null) {
			if (clientSocket != null) {
				try {
					clientSocket.close();
				} catch (IOException e) {
				}
				clientSocket = null;
			}
			
			try {
				mReceiveDataThread.join(500);
			} catch (InterruptedException e) {
			}
			
			if (mReceiveDataThread.isAlive()) {
				mReceiveDataThread.interrupt();
			}

			mReceiveDataThread = null;
		}

		changeConnectionState(ConnectionState.Disconnected);
	}

	public void connect(String host, int port) {

		close.set(false);
		this.host = host;
		this.port = port;

		changeConnectionState(ConnectionState.Connecting);

		if (mReceiveDataThread == null) {
			mReceiveDataThread = new Thread(this);
			mReceiveDataThread.setName("receiveDataThread");
			mReceiveDataThread.start();
		}
	}

	protected void changeConnectionState(ConnectionState state) {
		currentState.set(state.ordinal());
		if (onConnectionStateChangeListener != null) {
			onConnectionStateChangeListener
					.connectionStateChanged(new ConnectionStateChangedEvent(
							this, state));
		}
	}

	protected void dataReceived(String responseLine) {
		if (onDataReceivedListener != null) {
			onDataReceivedListener.dataReceived(new DataReceivedEvent(this,
					responseLine));
		}
	}

	public ConnectionState getCurrentState() {
		return ConnectionState.values()[currentState.get()];
	}

	@Override
	public void run() {
		String responseLine = "";
		DataInputStream is = null;
		DataOutputStream os = null;
		

		while (!close.get()) {
			// Initialization section:
			// Try to open a socket on a given host and port
			// Try to open input and output streams
			try {
				changeConnectionState(ConnectionState.Connecting);
				clientSocket = new Socket(host, port);
				is = new DataInputStream(clientSocket.getInputStream());
				//clientSocket.setSoTimeout(5000);
				changeConnectionState(ConnectionState.Connected);
			} catch (Exception e) {				
				try {
					if (clientSocket.isConnected()) {
						clientSocket.close();
					}
				} catch (Exception e1) {
				}

				try {
					Thread.sleep(1000);
				} catch (Exception e1) {
				}
				continue;
			}

			// Keep on reading from the socket till we receive the "Bye" from
			// the server,
			// once we received that then we want to break.
			try {
				if ((responseLine = is.readUTF()) != null) {
					responseLine = responseLine
							.replaceAll(KEEP_ALIVE, "")
							.replaceAll(START_SERVER, "");

					if (!responseLine.isEmpty()) {

						os = new DataOutputStream(
								clientSocket.getOutputStream());
						os.writeUTF(ACK);
						os.flush();
					}
					if (!responseLine.equals(STOP_CONNECTION)
							&& !responseLine.isEmpty()) {
						dataReceived(responseLine);
					}
				}
			} catch (SocketTimeoutException e) {
			} catch (SocketException e) {
			} catch (Exception e) {
				e.printStackTrace();
			} finally {

				// Clean up:
				// close the input stream
				// close the socket
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					is = null;
				}

				if (os != null) {
					try {
						os.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					os = null;
				}

				if (clientSocket != null) {
					try {
						clientSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					clientSocket = null;
				}

				if (responseLine.equals(STOP_CONNECTION)) {
					changeConnectionState(ConnectionState.Connecting);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
				}
			}
		}
	}
}
