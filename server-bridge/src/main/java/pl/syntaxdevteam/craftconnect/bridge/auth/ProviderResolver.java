package pl.syntaxdevteam.craftconnect.bridge.auth;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

/** Optional APIs are obtained from their owner, never bundled into the bridge. */
public final class ProviderResolver {
    private ProviderResolver() { }

    public static List<AuthenticationProvider> resolve(Plugin owner) throws ReflectiveOperationException {
        List<AuthenticationProvider> providers = new ArrayList<>();
        for (String name : List.of("AuthMe", "nLogin", "AuthGatewayX")) {
            Plugin plugin = owner.getServer().getPluginManager().getPlugin(name);
            if (plugin == null || !plugin.isEnabled()) continue;
            if (name.equals("AuthGatewayX")) {
                providers.add(authGateway(plugin, owner.getServer().getServicesManager()));
            } else {
                String type = name.equals("AuthMe")
                    ? "fr.xephi.authme.api.v3.AuthMeApi" : "com.nickuc.login.api.nLoginAPI";
                Class<?> api = Class.forName(type, true, plugin.getClass().getClassLoader());
                Method accessor = api.getMethod(name.equals("AuthMe") ? "getInstance" : "getApi");
                Method check = api.getMethod("isAuthenticated", name.equals("AuthMe") ? Player.class : String.class);
                providers.add(new AuthenticationProvider() {
                    public String name() { return name; }
                    public boolean isAuthenticated(Player player) throws ReflectiveOperationException {
                        if (!plugin.isEnabled()) return false;
                        Object instance = accessor.invoke(null);
                        return instance != null && Boolean.TRUE.equals(check.invoke(
                            instance, name.equals("AuthMe") ? player : player.getName()));
                    }
                });
            }
        }
        return providers;
    }

    private static AuthenticationProvider authGateway(Plugin plugin, ServicesManager services) {
        return new AuthenticationProvider() {
            public String name() { return "AuthGatewayX"; }
            public boolean isAuthenticated(Player player) throws ReflectiveOperationException {
                if (!plugin.isEnabled()) return false;
                for (RegisteredServiceProvider<?> service : services.getRegistrations(plugin)) {
                    if (service.getService().getName().equals(
                        "pl.syntaxdevteam.authgatewayx.api.AuthenticationStatusProvider")) {
                        Method check = service.getService().getMethod("isAuthenticated", UUID.class);
                        return Boolean.TRUE.equals(check.invoke(service.getProvider(), player.getUniqueId()));
                    }
                }
                return false;
            }
        };
    }
}
