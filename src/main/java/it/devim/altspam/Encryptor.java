
package it.devim.altspam;

import java.math.BigInteger;
import java.net.NetworkInterface;
import java.util.Base64;
import java.util.Enumeration;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


public class Encryptor {
    
    private String key;
    private String initVector;
    
    public Encryptor() {
        this.initVector = "R7BBomI12tVbcc90"; // 16 bytes IV
        this.key = "BRD1Xd45UUrSsT8 "; // 128 bit key
        try {
            Enumeration<NetworkInterface> net = NetworkInterface.getNetworkInterfaces();
            String macAddress = new BigInteger(1, net.nextElement().getHardwareAddress()).toString(16);
            this.key = (macAddress + this.key).substring(0, 16);
        } catch (Exception e) { }
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public void setInitVector(String initVector) {
        this.initVector = initVector;
    }
    
    public String encrypt(String value) {
        try {
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] encrypted = cipher.doFinal(value.getBytes());

            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    public String decrypt(String encrypted) {
        try {
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));

            return new String(original);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

}
