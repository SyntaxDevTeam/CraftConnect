package pl.syntaxdevteam.craftconnect.bridge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import pl.syntaxdevteam.craftconnect.bridge.auth.AuthenticationGate;
import pl.syntaxdevteam.craftconnect.bridge.auth.AuthenticationProvider;
import pl.syntaxdevteam.craftconnect.bridge.auth.ProviderResolver;
import pl.syntaxdevteam.craftconnect.bridge.protocol.AuthenticationBridgeProtocol;
import pl.syntaxdevteam.craftconnect.bridge.scheduler.PlayerTasks;

public final class CraftConnectBridge extends JavaPlugin implements Listener, PluginMessageListener {
    private final Map<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    private List<AuthenticationProvider> providers = List.of();

    @Override public void onEnable() {
        try {
            providers = ProviderResolver.resolve(this);
        } catch (ReflectiveOperationException | LinkageError failure) {
            getLogger().severe("Authentication API unavailable; no login confirmations will be sent: "
                + failure.getClass().getSimpleName());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Authentication providers: " + providers.stream().map(AuthenticationProvider::name).toList());
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, AuthenticationBridgeProtocol.CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, AuthenticationBridgeProtocol.CHANNEL);
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(AuthenticationBridgeProtocol.CHANNEL) || providers.isEmpty()
            || !player.hasPermission("craftconnect.bridge.use")) return;
        String nonce;
        try { nonce = AuthenticationBridgeProtocol.subscriptionNonce(message); }
        catch (IllegalArgumentException invalid) { return; }
        Subscription subscription = new Subscription(nonce);
        if (subscriptions.putIfAbsent(player.getUniqueId(), subscription) != null) return;
        try {
            subscription.cancel = PlayerTasks.repeat(this, player, () -> check(player, subscription),
                () -> subscriptions.remove(player.getUniqueId(), subscription));
            if (subscription.confirmed) subscription.cancel.run();
        } catch (ReflectiveOperationException failure) {
            subscriptions.remove(player.getUniqueId(), subscription);
            getLogger().warning("Cannot schedule authentication check: " + failure.getClass().getSimpleName());
        }
    }

    private void check(Player player, Subscription subscription) {
        if (!player.isOnline() || !player.hasPermission("craftconnect.bridge.use")) {
            remove(player.getUniqueId());
            return;
        }
        if (subscription.confirmed) return;
        try {
            Set<String> confirmed = AuthenticationGate.authenticatedProviders(providers, player);
            if (confirmed.isEmpty()) return;
            player.sendPluginMessage(this, AuthenticationBridgeProtocol.CHANNEL,
                AuthenticationBridgeProtocol.authenticated(subscription.nonce, confirmed));
            subscription.confirmed = true;
            subscription.cancel.run();
        } catch (ReflectiveOperationException | LinkageError failure) {
            if (!subscription.warned) {
                subscription.warned = true;
                getLogger().warning("Authentication check unavailable; confirmation withheld: "
                    + failure.getClass().getSimpleName());
            }
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { remove(event.getPlayer().getUniqueId()); }

    private void remove(UUID uuid) {
        Subscription subscription = subscriptions.remove(uuid);
        if (subscription != null) subscription.cancel.run();
    }

    @Override public void onDisable() {
        subscriptions.keySet().forEach(this::remove);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    private static final class Subscription {
        final String nonce;
        volatile Runnable cancel = () -> { };
        boolean confirmed;
        boolean warned;
        Subscription(String nonce) { this.nonce = nonce; }
    }
}
