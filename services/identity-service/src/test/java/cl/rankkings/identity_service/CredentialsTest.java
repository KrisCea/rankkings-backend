package cl.rankkings.identity_service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CredentialsTest {
    @Test void passwordHashingAndTokens() {
        String hash=Credentials.hashPassword("UnaClaveLarga#2026");
        assertTrue(Credentials.checkPassword("UnaClaveLarga#2026",hash));
        assertFalse(Credentials.checkPassword("incorrecta",hash));
        assertNotEquals(Credentials.hashPassword("UnaClaveLarga#2026"),hash);
        assertNotEquals(Credentials.randomToken(),Credentials.randomToken());
        assertEquals(64,Credentials.sha256("token").length());
    }
}
