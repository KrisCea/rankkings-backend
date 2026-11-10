package cl.rankkings.identity_service;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class Credentials {
    private static final SecureRandom RNG=new SecureRandom();
    private Credentials() {}
    public static String randomToken() { byte[] b=new byte[32]; RNG.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    public static String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch(Exception e){throw new IllegalStateException(e);} }
    public static String hashPassword(String pass) {
        byte[] salt=new byte[16]; RNG.nextBytes(salt);
        return "pbkdf2:310000:"+Base64.getEncoder().encodeToString(salt)+":"+derive(pass,salt,310000);
    }
    public static boolean checkPassword(String pass,String stored) {
        try {
            String[] p=stored.split(":"); if(p.length!=4 || !p[0].equals("pbkdf2")) return false;
            int iterations=Integer.parseInt(p[1]); if(iterations<100000 || iterations>1000000) return false;
            byte[] expected=Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(expected,Base64.getDecoder().decode(derive(pass,Base64.getDecoder().decode(p[2]),iterations)));
        } catch(Exception e) { return false; }
    }
    private static String derive(String pass, byte[] salt,int iterations) {
        PBEKeySpec key=new PBEKeySpec(pass.toCharArray(),salt,iterations,256);
        try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded()); }
        catch(Exception e){throw new IllegalStateException(e);} finally { key.clearPassword(); }
    }
}
