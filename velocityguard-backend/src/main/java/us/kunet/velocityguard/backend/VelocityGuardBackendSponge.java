package us.kunet.velocityguard.backend;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.profile.property.ProfileProperty;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.yaml.snakeyaml.DumperOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Plugin(id = "velocityguard", name = "VelocityGuard", version = "1.0",
        description = "Verify the connections between Velocity and Sponge")
public class VelocityGuardBackendSponge {
    @Inject
    private Logger logger;

    private List<Object> tokens;
    private String removalMessage;

    @Listener
    public void onServerStarting(GameStartingServerEvent startingEvent) throws IOException {
        File defaultDirectory = new File("config/velocityguard/");
        File defaultConfig = new File("config/velocityguard/config.yml");

        logger.info("Loading configuration...");

        boolean createDir = false;
        if (!defaultDirectory.exists()) {
            createDir = defaultDirectory.mkdir();
        }

        if (createDir || !defaultConfig.exists()) {
            Files.copy(getClass().getResourceAsStream("/config.yml"), defaultConfig.toPath());
        }

        YAMLConfigurationLoader configLoader = YAMLConfigurationLoader.builder()
                .setFile(defaultConfig)
                .setFlowStyle(DumperOptions.FlowStyle.BLOCK)
                .build();

        ConfigurationNode rootNode = configLoader.load();

        tokens = rootNode.getNode("allowed-tokens").getList(String::valueOf);
        if (!tokens.isEmpty()) {
            logger.info("Token loaded!");
        }

        removalMessage = rootNode.getNode("removal-message").getString();
    }

    @Listener
    public void onLogin(ClientConnectionEvent.@NotNull Login loginEvent) {
        GameProfile gameProfile = loginEvent.getProfile();
        boolean good = false;
        for (ProfileProperty profileProperty : gameProfile.getPropertyMap().get("velocityguard-token")) {
            if (tokens.contains(profileProperty.getValue())) {
                good = true;
                break;
            }
        }

        if (good) {
            loginEvent.getProfile().getPropertyMap().removeAll("velocityguard-token");
        } else {
            loginEvent.setCancelled(true);
            loginEvent.setMessage(TextSerializers.FORMATTING_CODE.deserialize(removalMessage));

            logger.warn("Denied connection from "
                    + loginEvent.getTargetUser().getName() + " (" + loginEvent.getTargetUser().getUniqueId() + ") @ "
                    + loginEvent.getConnection().getAddress() + " - Invalid token in their properties.");
        }
    }
}
