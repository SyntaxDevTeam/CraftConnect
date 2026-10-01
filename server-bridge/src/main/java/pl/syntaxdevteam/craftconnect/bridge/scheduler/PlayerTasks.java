package pl.syntaxdevteam.craftconnect.bridge.scheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Player checks and plugin messages execute on the owning entity thread. */
public final class PlayerTasks {
    private PlayerTasks() { }

    public static Runnable repeat(Plugin plugin, Player player, Runnable task, Runnable retired)
        throws ReflectiveOperationException {
        Method accessor;
        try {
            accessor = Player.class.getMethod("getScheduler");
        } catch (NoSuchMethodException spigot) {
            BukkitTask scheduled = plugin.getServer().getScheduler().runTaskTimer(plugin, task, 1L, 20L);
            return scheduled::cancel;
        }
        Object scheduler = accessor.invoke(player);
        Class<?> schedulerType = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
        Method repeat = schedulerType.getMethod("runAtFixedRate", Plugin.class, Consumer.class,
            Runnable.class, long.class, long.class);
        Object scheduled = repeat.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run(),
            retired, 1L, 20L);
        if (scheduled == null) {
            retired.run();
            return () -> { };
        }
        Method cancel = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask")
            .getMethod("cancel");
        return () -> {
            try { cancel.invoke(scheduled); }
            catch (ReflectiveOperationException ignored) { }
        };
    }
}
