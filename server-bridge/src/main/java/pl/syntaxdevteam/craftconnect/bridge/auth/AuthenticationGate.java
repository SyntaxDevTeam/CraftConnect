package pl.syntaxdevteam.craftconnect.bridge.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;

/** Every detected provider must agree. Missing providers never imply success. */
public final class AuthenticationGate {
    private AuthenticationGate() { }

    public static Set<String> authenticatedProviders(List<AuthenticationProvider> providers, Player player)
        throws ReflectiveOperationException {
        Set<String> confirmed = new LinkedHashSet<>();
        for (AuthenticationProvider provider : providers) {
            if (!provider.isAuthenticated(player)) return Set.of();
            confirmed.add(provider.name());
        }
        return confirmed;
    }
}
