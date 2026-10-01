package pl.syntaxdevteam.craftconnect.bridge.auth;

import org.bukkit.entity.Player;

public interface AuthenticationProvider {
    String name();
    boolean isAuthenticated(Player player) throws ReflectiveOperationException;
}
