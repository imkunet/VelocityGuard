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

public class VelocityGuardBackendPlugin extends JavaPlugin {

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

    private VelocityGuardBackendPlugin velocityGuardBackendPlugin;

    PreLoginListener(VelocityGuardBackendPlugin plugin) {
        this.velocityGuardBackendPlugin = plugin;
    }

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
                        + event.getAddress().getHostName() + " - Invalid token in their properties.");
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    // Thanks to Leymooo on the Velocity Discord for providing code for reflection https://github.com/Leymooo

    private static String getVersion() {
        return Bukkit.getServer().getClass().getPackage().getName()
                .replace(".", ",").split(",")[3];
    }

    private static Class<?> getCraftPlayerClass() throws ClassNotFoundException {
        return Class.forName("org.bukkit.craftbukkit." + getVersion() + "." + "entity.CraftPlayer");
    }

    private static Class<?> getEntityHumanClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + getVersion() + "." + "EntityHuman");
    }
}