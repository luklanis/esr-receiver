package ch.luklanis.esreceiver;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicInteger;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import ch.luklanis.esreceiver.connectionstate.ConnectionState;
import ch.luklanis.esreceiver.connectionstate.ConnectionStateChangedEvent;
import ch.luklanis.esreceiver.connectionstate.OnConnectionStateChangeListener;
import ch.luklanis.esreceiver.datareceived.DataReceivedEvent;
import ch.luklanis.esreceiver.datareceived.OnDataReceivedListener;

public class TcpReceive {

	// Declaration section
	// clientClient: the client socket
	// os: the output stream
	// is: the input stream
	private static final AtomicInteger currentState = new AtomicInteger(
			ConnectionState.Disconnected.ordinal());

	private WebSocketClient webSocketClient;

	private OnConnectionStateChangeListener onConnectionStateChangeListener;
	private OnDataReceivedListener onDataReceivedListener;
	private String host;
	private int port;

	public void setOnConnectionStateChangeListener(
			OnConnectionStateChangeListener listener) {
		onConnectionStateChangeListener = listener;
	}

	public void setOnDataReceivedListener(OnDataReceivedListener listener) {
		onDataReceivedListener = listener;
	}

	public void close() {
		webSocketClient.close();
	}

	public void connect(String host, int port) {
		this.host = host;
		this.port = port;

		changeConnectionState(ConnectionState.Connecting);
		
		String uriString = String.format("ws://%s:%s", this.host, this.port);

		try {
			webSocketClient = new WebSocketClient(new URI(uriString)) {
				
				@Override
				public void onOpen(ServerHandshake arg0) {
					changeConnectionState(ConnectionState.Connected);
				}
				
				@Override
				public void onMessage(String arg0) {
					String filtered = arg0.substring(0, arg0.indexOf("."));
					dataReceived(filtered);
				}
				
				@Override
				public void onError(Exception arg0) {
					System.out.println(arg0.getMessage());
					
					changeConnectionState(ConnectionState.Connecting);
					
					TcpReceive self = TcpReceive.this;
					self.connect(self.host, self.port);
				}
				
				@Override
				public void onClose(int arg0, String arg1, boolean arg2) {
					changeConnectionState(ConnectionState.Disconnected);
				}
			};
			
			webSocketClient.connect();
		} catch (URISyntaxException e) {
			System.out.println(uriString + " is not a valid WebSocket URI\n");
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
}
