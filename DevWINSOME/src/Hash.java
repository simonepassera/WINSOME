import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash {
    public static String encrypt(String password) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

        messageDigest.update(password.getBytes());

        byte[] digest = messageDigest.digest();

        StringBuilder hexString = new StringBuilder();

        for (byte b : digest) hexString.append(Integer.toHexString(Byte.toUnsignedInt(b)));

        return hexString.toString();
    }
}
