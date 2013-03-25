package ch.luklanis.esreceiver;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ch.luklanis.esreceiver.connectionstate.ConnectionState;
import ch.luklanis.esreceiver.connectionstate.ConnectionStateChangedEvent;
import ch.luklanis.esreceiver.connectionstate.OnConnectionStateChangeListener;
import ch.luklanis.esreceiver.datareceived.DataReceivedEvent;
import ch.luklanis.esreceiver.datareceived.OnDataReceivedListener;

public class TcpReceive  implements Runnable{

	// Declaration section
	// clientClient: the client socket
	// os: the output stream
	// is: the input stream
	private static final AtomicBoolean close = new AtomicBoolean(false);
	private static final AtomicInteger currentState = new AtomicInteger(ConnectionState.Disconnected.ordinal());

	static Socket clientSocket = null;
	static DataInputStream is = null;
	static BufferedReader inputLine = null;
	static boolean closed = false;

	private static OnConnectionStateChangeListener onConnectionStateChangeListener;
	private static OnDataReceivedListener onDataReceivedListener;
	private static String host;
	private static int port;
	private Thread thread;

	public void setOnConnectionStateChangeListener(OnConnectionStateChangeListener listener) {
		onConnectionStateChangeListener = listener;
	}

	public void setOnDataReceivedListener(OnDataReceivedListener listener) {
		onDataReceivedListener = listener;
	}
	
	public void close() {
		close.set(true);
		thread.interrupt();

		changeConnectionState(ConnectionState.Disconnected);
	}

	public void connect(String host, int port) {

		close.set(false);
		TcpReceive.host = host;
		TcpReceive.port = port;
		
		changeConnectionState(ConnectionState.Connecting);

		this.thread = new Thread(new TcpReceive());
		this.thread.start();
	}

	protected void changeConnectionState(ConnectionState state) {
		currentState.set(state.ordinal());
		if (onConnectionStateChangeListener != null) {
			onConnectionStateChangeListener.connectionStateChanged(
					new ConnectionStateChangedEvent(this, state));
		}
	}     

	protected void dataReceived(String responseLine) {
		if (onDataReceivedListener != null) {
			onDataReceivedListener.dataReceived(
					new DataReceivedEvent(this, responseLine));
		}
	}  
	
	public ConnectionState getCurrentState() {
		return ConnectionState.values()[currentState.get()];
	}

	@Override
	public void run() {		
		String responseLine;

		while(!close.get()) {
			// Initialization section:
			// Try to open a socket on a given host and port
			// Try to open input and output streams
			try {
				changeConnectionState(ConnectionState.Connecting);
				clientSocket = new Socket(host, port);
				clientSocket.setSoTimeout(5000);
				is = new DataInputStream(clientSocket.getInputStream());
				changeConnectionState(ConnectionState.Connected);
			} catch (Exception e) {
				System.err.println("Don't know about host " + host);
				
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

			// Keep on reading from the socket till we receive the "Bye" from the server,
			// once we received that then we want to break.
			try{ 
				while ((responseLine = is.readUTF()) != null) {
					dataReceived(responseLine);
				}
			} catch (IOException e) {
				System.err.println("IOException:  " + e);
			} 
			finally {

				// Clean up:
				// close the input stream
				// close the socket
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				} 

				try {
					if (clientSocket.isConnected()) {
						clientSocket.close(); 
					}
				} catch (IOException e) {
					e.printStackTrace();
				}  
			}
		}
	}
}
