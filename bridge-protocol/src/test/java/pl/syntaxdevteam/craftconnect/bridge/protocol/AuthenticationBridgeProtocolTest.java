package pl.syntaxdevteam.craftconnect.bridge.protocol;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthenticationBridgeProtocolTest {
    @Test public void onlyMatchingConnectionAndKnownProvidersConfirm() {
        String nonce = UUID.randomUUID().toString();
        assertEquals(nonce, AuthenticationBridgeProtocol.subscriptionNonce(
            AuthenticationBridgeProtocol.subscribe(nonce)));
        for (String provider : Set.of("AuthMe", "nLogin", "AuthGatewayX")) {
            byte[] proof = AuthenticationBridgeProtocol.authenticated(nonce, Set.of(provider));
            assertTrue(AuthenticationBridgeProtocol.confirms(proof, nonce));
            assertFalse(AuthenticationBridgeProtocol.confirms(proof, UUID.randomUUID().toString()));
            assertFalse(AuthenticationBridgeProtocol.confirms(Arrays.copyOf(proof, proof.length - 1), nonce));
            assertFalse(AuthenticationBridgeProtocol.confirms(Arrays.copyOf(proof, proof.length + 1), nonce));
            proof[0] = 2;
            assertFalse(AuthenticationBridgeProtocol.confirms(proof, nonce));
        }
        assertFalse(AuthenticationBridgeProtocol.confirms(AuthenticationBridgeProtocol.subscribe(nonce), nonce));
        assertFalse(AuthenticationBridgeProtocol.confirms(new byte[257], nonce));
    }

    @Test(expected = IllegalArgumentException.class)
    public void noProviderCannotConfirmAuthentication() {
        AuthenticationBridgeProtocol.authenticated(UUID.randomUUID().toString(), Set.of());
    }
}
