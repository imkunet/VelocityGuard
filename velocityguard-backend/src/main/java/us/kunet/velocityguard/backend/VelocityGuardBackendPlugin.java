package us.kunet.velocityguard.backend;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

public class VelocityGuardBackendPlugin extends JavaPlugin {

    // Reflection methods adapted from https://github.com/lucko/commodore/blob/master/src/main/java/me/lucko/commodore/ReflectionUtil.java
    // licensed under MIT, refer to License.txt

    private List<String> allowedTokens;
    private String removalMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        this.allowedTokens = config.getStringList("allowed-tokens");
        this.removalMessage = config.getString("removal-message");

        Bukkit.getPluginManager().registerEvents(new PreLoginListener(this), this);
    }

    public List<String> getAllowedTokens() {
        return allowedTokens;
    }

    public String getRemovalMessage() {
        return removalMessage;
    }
}

class PreLoginListener implements Listener {

    private final VelocityGuardBackendPlugin velocityGuardBackendPlugin;

    PreLoginListener(VelocityGuardBackendPlugin plugin) {
        this.velocityGuardBackendPlugin = plugin;
    }

    private static final String SERVER_VERSION = getServerVersion();
    private static Method getHandle;
    private static Method getProfile;
    static {
        try {
            getHandle = getCraftPlayerClass().getDeclaredMethod("getHandle");
            getProfile = getEntityHumanClass().getDeclaredMethod("getProfile");
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private static String getServerVersion() {
        Class<?> server = Bukkit.getServer().getClass();
        if (!server.getSimpleName().equals("CraftServer")) {
            return ".";
        }
        if (server.getName().equals("org.bukkit.craftbukkit.CraftServer")) {
            // Non versioned class
            return ".";
        } else {
            String version = server.getName().substring("org.bukkit.craftbukkit".length());
            return version.substring(0, version.length() - "CraftServer".length());
        }
    }

    @EventHandler
    public void onPreLogin(@NotNull PlayerLoginEvent event) {
        boolean good = false;
        try {
            GameProfile gameProfile = (GameProfile) getProfile.invoke(getHandle.invoke(event.getPlayer()));
            for (Property property : gameProfile.getProperties().get("velocityguard-token")) {
                if (velocityGuardBackendPlugin.getAllowedTokens().contains(property.getValue())) {
                    good = true;
                    break;
                }
            }
            if (good) {
                gameProfile.getProperties().removeAll("velocityguard-token");
            } else {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ChatColor
                        .translateAlternateColorCodes('&', velocityGuardBackendPlugin.getRemovalMessage()));

                velocityGuardBackendPlugin.getLogger().warning("Denied connection from "
                        + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ") @ "
                        + event.getAddress() + " - Invalid token in their properties.");
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            velocityGuardBackendPlugin.getLogger().log(Level.SEVERE, "YOUR SERVER IS IN DANGER! " +
                    "VelocityGuard doesn't seem to be working, please reach out for support!");

            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ChatColor
                    .translateAlternateColorCodes('&', "&cPlease contact server admins"));
        }
    }

    // Thanks to Leymooo on the Velocity Discord for providing code for reflection https://github.com/Leymooo
    // Thanks to mikroskeem on the Velocity Discord for suggesting support for relocation support.

    private static @NotNull Class<?> getCraftPlayerClass() throws ClassNotFoundException {
        return Class.forName("org.bukkit.craftbukkit" + SERVER_VERSION + "entity.CraftPlayer");
    }

    private static @NotNull Class<?> getEntityHumanClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.server" + SERVER_VERSION + "EntityHuman");
    }
}