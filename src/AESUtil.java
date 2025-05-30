import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;

public class AESUtil {
    private static final String ALGO = "AES";
    private static final String KEYSTORE_TYPE = "JCEKS";
    private static final String KEYSTORE_FILE = "chat_keystore.jks";
    private static final String KEY_ALIAS = "chatkey";
    private static final char[] KEYSTORE_PASSWORD = "chatapp123".toCharArray(); // In production, use environment variables

    //initialize keystore and create key if it doesn't exist
    static {
        try {
            File keystoreFile = new File(KEYSTORE_FILE);
            KeyStore keyStore;

            if (!keystoreFile.exists()) {
                //Create new keystore and generate a key
                keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
                keyStore.load(null, KEYSTORE_PASSWORD);

                //Generate a strong AES key
                KeyGenerator keyGen = KeyGenerator.getInstance(ALGO);
                keyGen.init(256, new SecureRandom());
                SecretKey secretKey = keyGen.generateKey();

                //Store the key in the keystore
                KeyStore.SecretKeyEntry keyEntry = new KeyStore.SecretKeyEntry(secretKey);
                keyStore.setEntry(KEY_ALIAS, keyEntry,
                        new KeyStore.PasswordProtection(KEYSTORE_PASSWORD));

                //Save the keystore
                try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
                    keyStore.store(fos, KEYSTORE_PASSWORD);
                }

                System.out.println("New encryption key generated and stored securely");
            }
        } catch (Exception e) {
            System.err.println("Error initializing encryption: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static SecretKey getKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            try (FileInputStream fis = new FileInputStream(KEYSTORE_FILE)) {
                keyStore.load(fis, KEYSTORE_PASSWORD);
            }

            KeyStore.SecretKeyEntry keyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(
                    KEY_ALIAS, new KeyStore.PasswordProtection(KEYSTORE_PASSWORD));
            return keyEntry.getSecretKey();
        } catch (Exception e) {
            System.err.println("Error retrieving encryption key: " + e.getMessage());
            // Fallback to a default key only if absolutely necessary
            return new SecretKeySpec("MySuperSecretKey".getBytes(), ALGO);
        }
    }

    public static String encrypt(String data) {
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encrypted = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            System.err.println("Encryption error: " + e.getMessage());
            return null;
        }
    }

    public static String decrypt(String encryptedData) {
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, getKey());
            byte[] original = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(original);
        } catch (Exception e) {
            System.err.println("Decryption error: " + e.getMessage());
            return null;
        }
    }
}
