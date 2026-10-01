package pl.syntaxdevteam.craftconnect.bridge.auth;

import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthenticationGateTest {
    private AuthenticationProvider provider(String name, boolean authenticated) {
        return new AuthenticationProvider() {
            public String name() { return name; }
            public boolean isAuthenticated(Player player) { return authenticated; }
        };
    }

    @Test public void requiresAllDetectedPluginsToAgree() throws ReflectiveOperationException {
        for (String name : List.of("AuthMe", "nLogin", "AuthGatewayX")) {
            assertEquals(Set.of(), AuthenticationGate.authenticatedProviders(List.of(provider(name, false)), null));
            assertEquals(Set.of(name), AuthenticationGate.authenticatedProviders(List.of(provider(name, true)), null));
        }
        assertEquals(Set.of(), AuthenticationGate.authenticatedProviders(List.of(), null));
        assertEquals(Set.of(), AuthenticationGate.authenticatedProviders(
            List.of(provider("AuthMe", true), provider("nLogin", false)), null));
    }

    @Test(expected = ReflectiveOperationException.class)
    public void apiFailureCannotBecomeSuccessfulAuthentication() throws ReflectiveOperationException {
        AuthenticationGate.authenticatedProviders(List.of(new AuthenticationProvider() {
            public String name() { return "AuthMe"; }
            public boolean isAuthenticated(Player player) throws ReflectiveOperationException {
                throw new ReflectiveOperationException("Unavailable");
            }
        }), null);
    }
}
