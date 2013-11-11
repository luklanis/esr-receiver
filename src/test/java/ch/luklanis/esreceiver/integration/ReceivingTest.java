package ch.luklanis.esreceiver.integration;

import ch.luklanis.esreceiver.HttpReceive;
import ch.luklanis.esreceiver.connectionstate.ConnectionState;
import ch.luklanis.esreceiver.connectionstate.ConnectionStateChangedEvent;
import ch.luklanis.esreceiver.connectionstate.OnConnectionStateChangeListener;
import ch.luklanis.esreceiver.datareceived.DataReceivedEvent;
import ch.luklanis.esreceiver.datareceived.OnDataReceivedListener;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.AlgorithmParameters;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * User: lukas
 * Date: 11/6/13
 * Time: 4:18 PM
 */
public class ReceivingTest {

    //    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";       // AES
    private static final String CIPHER_ALGORITHM = "DESede/CBC/PKCS5Padding";

    String receivedSecret;
    String secret = "0123456789><";
    String sendUrl = "http://esr-relay.herokuapp.com";
    String password = "test1234";
    String wrongPassword = "test";
    String emailAddress = "test@luklanis.ch";

    /**
     * Countdown latch
     */
    private CountDownLatch lock = new CountDownLatch(1);
    private ConnectionState newConnectionState;

    @Test
    public final void testAuthenticationError() throws Exception {
        HttpReceive httpReceive = new HttpReceive();
        httpReceive.init(emailAddress, wrongPassword);
        receivedSecret = null;
        newConnectionState = ConnectionState.Disconnected;
        final CountDownLatch connectionLock = new CountDownLatch(1);

        httpReceive.setOnDataReceivedListener(new OnDataReceivedListener() {
            @Override
            public void dataReceived(DataReceivedEvent event) {
                receivedSecret = event.getData();
                lock.countDown();
            }
        });
        httpReceive.setOnConnectionStateChangeListener(new OnConnectionStateChangeListener() {
            @Override
            public void connectionStateChanged(ConnectionStateChangedEvent event) {
                if (event.getConnectionState() == ConnectionState.AuthenticationError) {
                    connectionLock.countDown();
                    newConnectionState = event.getConnectionState();
                }
            }
        });

        String[] encrypted = encrypt(httpReceive.getSecretKey(), "test");

        JSONObject json = new JSONObject();
        json.put("hash", httpReceive.getHash(password, emailAddress));
        json.put("id", emailAddress);
        json.put("iv", encrypted[0]);
        json.put("message", encrypted[1]);

        HttpResponse<String> response = Unirest.post(sendUrl)
                .body(json.toString())
                .asString();

        assertEquals("Answer", "OK", response.getBody());

        httpReceive.connect();

        connectionLock.await(2000, TimeUnit.MILLISECONDS);
        assertEquals(ConnectionState.AuthenticationError, newConnectionState);
    }

    @Test
    public final void testReceiving() throws Exception {
        HttpReceive httpReceive = new HttpReceive();
        httpReceive.init(emailAddress, password);
        receivedSecret = null;
        newConnectionState = ConnectionState.Disconnected;
        final CountDownLatch connectionLock = new CountDownLatch(1);

        httpReceive.setOnDataReceivedListener(new OnDataReceivedListener() {
            @Override
            public void dataReceived(DataReceivedEvent event) {
                receivedSecret = event.getData();
                lock.countDown();
            }
        });
        httpReceive.setOnConnectionStateChangeListener(new OnConnectionStateChangeListener() {
            @Override
            public void connectionStateChanged(ConnectionStateChangedEvent event) {
                connectionLock.countDown();
                newConnectionState = event.getConnectionState();
            }
        });
        httpReceive.connect();

        connectionLock.await(2000, TimeUnit.MILLISECONDS);
        assertEquals(ConnectionState.Waiting, newConnectionState);

        String[] encrypted = encrypt(httpReceive.getSecretKey(), secret);

        JSONObject json = new JSONObject();
        json.put("hash", httpReceive.getHash(password, emailAddress));
        json.put("id", emailAddress);
        json.put("iv", encrypted[0]);
        json.put("message", encrypted[1]);

        HttpResponse<String> response = Unirest.post(sendUrl)
                .body(json.toString())
                .asString();

        assertEquals("Answer", "OK", response.getBody());
        lock.await(2000, TimeUnit.MILLISECONDS);

        assertNotNull(receivedSecret);
        assertEquals("Secret", secret, receivedSecret);
    }

    public static String[] encrypt(SecretKey secret, String cleartext) throws Exception {
        Cipher encryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM);
        encryptionCipher.init(Cipher.ENCRYPT_MODE, secret);
        AlgorithmParameters params = encryptionCipher.getParameters();
        byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
        byte[] encryptedText = encryptionCipher.doFinal(cleartext.getBytes("UTF-8"));

        return new String[]{Base64.encodeBase64String(iv), Base64.encodeBase64String(
                encryptedText)};
    }
}
