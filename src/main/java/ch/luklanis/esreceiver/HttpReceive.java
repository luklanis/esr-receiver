package ch.luklanis.esreceiver;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;

import ch.luklanis.esreceiver.connectionstate.ConnectionState;
import ch.luklanis.esreceiver.connectionstate.ConnectionStateChangedEvent;
import ch.luklanis.esreceiver.connectionstate.OnConnectionStateChangeListener;
import ch.luklanis.esreceiver.datareceived.DataReceivedEvent;
import ch.luklanis.esreceiver.datareceived.OnDataReceivedListener;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;

public class HttpReceive {

	// Declaration section
	// clientClient: the client socket
	// os: the output stream
	// is: the input stream
	private static final AtomicInteger currentState = new AtomicInteger(
			ConnectionState.Disconnected.ordinal());

	private OnConnectionStateChangeListener onConnectionStateChangeListener;
	private OnDataReceivedListener onDataReceivedListener;
	private String url;
    private String emailAddress;
	private String password;
	Future<HttpResponse<JsonNode>> future;
	
	private Callback<JsonNode> unirestCallback = new Callback<JsonNode>() {
		
		@Override
		public void failed(Exception arg0) {
			arg0.printStackTrace();
			future = Unirest.get(url)
					.asJsonAsync(unirestCallback);
		}
		
		@Override
		public void completed(HttpResponse<JsonNode> arg0) {
            JSONObject response = arg0.getBody().getObject();

            if (!response.has("error") || response.isNull("error")) {
                try {
                    String message = response.getString("message");
                    String iv = response.getString("iv");

                    byte[] decodedMsg = DatatypeConverter.parseBase64Binary(message);
                    byte[] decodedIv = DatatypeConverter.parseBase64Binary(iv);

                    dataReceived(decrypt(decodedMsg, decodedIv));
                } catch (JSONException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            } else {
                try {
                    if (response.getString("error").equals("timeout")) {
                        System.out.println("Timeout. Start long polling again...");
                        future = Unirest.get(url)
                                .asJsonAsync(unirestCallback);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
		}
		
		@Override
		public void cancelled() {
			System.out.println("Http request cancelled");
		}
	};

    public void setOnConnectionStateChangeListener(
			OnConnectionStateChangeListener listener) {
		onConnectionStateChangeListener = listener;
	}

	public void setOnDataReceivedListener(OnDataReceivedListener listener) {
		onDataReceivedListener = listener;
	}

	public void close() {
		future.cancel(true);
        changeConnectionState(ConnectionState.Disconnected);
	}

	public void connect(String emailAddress, String password) {
        this.password = password;
        this.emailAddress = emailAddress;

        this.url = String.format("http://esr-relay.herokuapp.com/%s/%s", emailAddress, sha256(password));
		
		changeConnectionState(ConnectionState.Connecting);
		
		future = Unirest.get(url)
				.asJsonAsync(unirestCallback);
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
	
	public static String sha256(String base) {
	    try{
	        MessageDigest digest = MessageDigest.getInstance("SHA-256");
	        byte[] hash = digest.digest(base.getBytes("UTF-8"));
	        StringBuffer hexString = new StringBuffer();

	        for (int i = 0; i < hash.length; i++) {
	            String hex = Integer.toHexString(0xff & hash[i]);
	            if(hex.length() == 1) hexString.append('0');
	            hexString.append(hex);
	        }

	        return hexString.toString();
	    } catch(Exception ex){
	       throw new RuntimeException(ex);
	    }
	}

    public String decrypt(byte[] message, byte[] iv)
    {
        try
        {
            // todo: find encryption on http://stackoverflow.com/a/992413
            /* Derive the key, given password and salt. */
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), emailAddress.getBytes("UTF-8"), 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

            /* Decrypt the message, given derived key and initialization vector. */
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
            final String decryptedString = new String(cipher.doFinal(message), "UTF-8");
            return decryptedString;
        }
        catch (Exception e)
        {
			System.out.print("Error while decrypting" + e);

        }
        return null;
    }
}
