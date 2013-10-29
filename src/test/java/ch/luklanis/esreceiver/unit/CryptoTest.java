package ch.luklanis.esreceiver.unit;

import ch.luklanis.esreceiver.HttpReceive;
import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.AlgorithmParameters;

import static org.junit.Assert.assertEquals;

/**
 * User: lukas
 * Date: 10/28/13
 * Time: 8:19 PM
 */
public class CryptoTest {

    //    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";       // AES
    private static final String CIPHER_ALGORITHM = "DESede/CBC/PKCS5Padding";

    private HttpReceive httpReceive;

    @Before
    public final void setup() throws Exception {
        httpReceive = new HttpReceive();
        httpReceive.init("test@luklanis.ch", "test1234");
    }

    @Test
    public final void testDecryption() throws Exception {
        String secret = "0123456789><";

        String[] toDecrypt = encrypt(httpReceive.getSecretKey(), secret);

        String decrypted = httpReceive.decrypt(toDecrypt[0], toDecrypt[1]);

        assertEquals("Secret and decrypted don't match", secret, decrypted);
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
