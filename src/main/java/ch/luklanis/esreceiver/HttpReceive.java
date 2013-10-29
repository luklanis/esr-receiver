package ch.luklanis.esreceiver;

import ch.luklanis.esreceiver.connectionstate.ConnectionState;
import ch.luklanis.esreceiver.connectionstate.ConnectionStateChangedEvent;
import ch.luklanis.esreceiver.connectionstate.OnConnectionStateChangeListener;
import ch.luklanis.esreceiver.datareceived.DataReceivedEvent;
import ch.luklanis.esreceiver.datareceived.OnDataReceivedListener;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpReceive {

    public static final String PROVIDER = "BC";
    public static final int PBE_ITERATION_COUNT = 1000;

    private static final String HASH_ALGORITHM = "SHA-512";
    //    private static final String PBE_ALGORITHM = "PBEWithSHA256And256BitAES-CBC-BC";       // AES
    private static final String PBE_ALGORITHM = "PBEWithSHAAnd3-KeyTripleDES-CBC";
    //    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";       // AES
    private static final String CIPHER_ALGORITHM = "DESede/CBC/PKCS5Padding";
    //    private static final String SECRET_KEY_ALGORITHM = "AES";       // AES
    private static final String SECRET_KEY_ALGORITHM = "DESede";

    //    private static final int KEY_LENGTH = 256;       // AES
    private static final int KEY_LENGTH = 192;

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

                    dataReceived(decrypt(iv, message));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                future = Unirest.get(url)
                        .asJsonAsync(unirestCallback);
            } else {
                try {
                    if (response.getString("error").equals("timeout")) {
                        System.out.println("Timeout. Start long polling again...");
                        future = Unirest.get(url)
                                .asJsonAsync(unirestCallback);
                    } else {
                        changeConnectionState(ConnectionState.AuthenticationError);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void cancelled() {
            System.out.println("Http request cancelled");
        }
    };

    public HttpReceive() {
        // AES
        Security.addProvider(new org.bouncycastle.jce.provider
                .BouncyCastleProvider());
    }

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

    public void init(String emailAddress, String password) throws Exception {
        this.emailAddress = emailAddress;
        this.password = password;

        this.url = String.format("http://esr-relay.herokuapp.com/%s/%s", emailAddress, getHash(password, emailAddress));
    }

    public void connect() {
        changeConnectionState(ConnectionState.Waiting);

        future = Unirest.get(url)
                .asJsonAsync(unirestCallback);
    }

    public void connect(String emailAddress, String password) {
        try {
            init(emailAddress, password);
        } catch (Exception e) {
            e.printStackTrace();
            changeConnectionState(ConnectionState.AuthenticationError);
        }

        connect();
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

    public String getHash(String password, String salt) throws NoSuchProviderException, NoSuchAlgorithmException, UnsupportedEncodingException {
        String input = password + salt;
        MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] out = md.digest(input.getBytes("UTF-8"));
        return Base64.encodeBase64URLSafeString(out);
    }

//	public static String sha256(String base) {
//	    try{
//	        MessageDigest digest = MessageDigest.getInstance("SHA-256");
//	        byte[] hash = digest.digest(base.getBytes("UTF-8"));
//	        StringBuffer hexString = new StringBuffer();
//
//	        for (int i = 0; i < hash.length; i++) {
//	            String hex = Integer.toHexString(0xff & hash[i]);
//	            if(hex.length() == 1) hexString.append('0');
//	            hexString.append(hex);
//	        }
//
//            return Base64.encodeBase64String(hash) ;
//	    } catch(Exception ex){
//	       throw new RuntimeException(ex);
//	    }
//	}

    // info: AES enc/dec best practice from http://stackoverflow.com/q/8622367
    public String decrypt(String iv, String encrypted)
            throws NoSuchPaddingException,
            NoSuchAlgorithmException,
            NoSuchProviderException,
            InvalidKeySpecException,
            InvalidAlgorithmParameterException,
            InvalidKeyException,
            BadPaddingException,
            IllegalBlockSizeException,
            UnsupportedEncodingException {

        byte[] decodedMsg = Base64.decodeBase64(encrypted);
        byte[] decodedIv = Base64.decodeBase64(iv);

        Cipher decryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM);
        IvParameterSpec ivSpec = new IvParameterSpec(decodedIv);
        decryptionCipher.init(Cipher.DECRYPT_MODE, getSecretKey(), ivSpec);
        byte[] decryptedText = decryptionCipher.doFinal(decodedMsg);
        return new String(decryptedText, "UTF-8");
    }

    public SecretKey getSecretKey()
            throws NoSuchProviderException,
            NoSuchAlgorithmException,
            InvalidKeySpecException,
            UnsupportedEncodingException {
        char[] pw = toHexString(password.getBytes("UTF-8")).toCharArray();
        PBEKeySpec pbeKeySpec = new PBEKeySpec(pw, emailAddress.getBytes("UTF-8"), PBE_ITERATION_COUNT, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBE_ALGORITHM, PROVIDER);
        SecretKey tmp = factory.generateSecret(pbeKeySpec);
        return new SecretKeySpec(tmp.getEncoded(), SECRET_KEY_ALGORITHM);
    }

    private static String toHexString(byte[] data) {
        StringBuilder hexString = new StringBuilder();

        for (byte d : data) {
            String hex = Integer.toHexString(0xff & d);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }
}
