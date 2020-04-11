package us.kunet.velocityguard.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.util.GameProfile;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.json.JSONConfigurationLoader;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Plugin(id = "velocityguard", name = "VelocityGuard", version = "1.0",
        description = "Verify the connections between Velocity and Servers", authors = {"KuNet"})
public class VelocityGuardProxyPlugin {

    // characters used to build a token
    private static final String TOKEN_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    @NotNull
    private static String generateToken() {
        StringBuilder sb = new StringBuilder();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 64; i++) {
            sb.append(TOKEN_CHARS.charAt(random.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }

    private final Logger logger;
    private final Path configDirectory;
    private String token;

    @Inject
    public VelocityGuardProxyPlugin(Logger logger, @DataDirectory Path configDirectory) {
        this.logger = logger;
        this.configDirectory = configDirectory;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent initializeEvent) {
        File configFile;

        if (!configDirectory.toFile().exists()) {
            if (configDirectory.toFile().mkdir()) logger.info("Config folder created!");
        }

        configFile = new File(configDirectory.toFile(), "token.json");

        @NonNull JSONConfigurationLoader configLoader = JSONConfigurationLoader.builder().setFile(configFile).build();

        try {
            @NonNull ConfigurationNode configNode = configLoader.load();
            loadConfig(configNode);
            logger.info("Config loaded!");

            configLoader.save(configNode);
            configLoader = JSONConfigurationLoader.builder().setFile(configFile).build();

            configNode = configLoader.load();
            token = configNode.getNode("token").getString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (token != null) {
            logger.info("Token loaded!");
        }
    }

    private void loadConfig(@NotNull ConfigurationNode node) {
        if (node.getNode("token").isVirtual()) {
            node.getNode("token").setValue(generateToken());
        }
    }

    @Subscribe
    public void onLogin(@NotNull LoginEvent loginEvent) {
        List<GameProfile.Property> properties = new ArrayList<>(loginEvent.getPlayer().getGameProfileProperties());
        properties.add(new GameProfile.Property("velocityguard-token", token, ""));

        loginEvent.getPlayer().setGameProfileProperties(properties);
    }

}
