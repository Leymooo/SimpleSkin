package ru.leymooo.simpleskins;

import ru.leymooo.simpleskins.utils.RoundIterator;
import ru.leymooo.simpleskins.utils.DataBaseUtils;
import ru.leymooo.simpleskins.utils.SkinUtils;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.kyori.text.serializer.ComponentSerializers;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import ru.leymooo.simpleskins.command.SkinCommand;

@Plugin(id = "simpleskins", name = "SimpleSkins", version = "1.0.0",
        description = "Simple skins restorer plugin for velocity",
        authors = "Leymooo")
public class SimpleSkins {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final SkinUtils skinUtils;
    private final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private RoundIterator<SkinUtils.FetchResult> defaultSkins;
    private Configuration config;
    private DataBaseUtils dataBaseUtils;

    @Inject
    public SimpleSkins(ProxyServer server, Logger logger, @DataDirectory Path userConfigDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = userConfigDirectory;
        this.skinUtils = new SkinUtils(this);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("Enabling SimspleSkins version 1.0.0");

        if (!loadConfig()) {
            server.getEventManager().unregisterListeners(this);
            logger.error("Config is not loaded. Plugin will be inactive");
            return;
        }
        initDefaultSkins();
        this.dataBaseUtils = new DataBaseUtils(this);

        this.server.getCommandManager().register(new SkinCommand(this), "skin");
        //Config
        //Command
    }

    @Subscribe
    public void onShutDown(ProxyShutdownEvent ev) {
        
    }
    
    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        if (!event.isOnlineMode()) {
            Optional<SkinUtils.FetchResult> maybeCached = fetchSkin(Optional.empty(), event.getUsername(), false, true);
            Optional<SkinUtils.FetchResult> toSet = maybeCached.isPresent()
                    ? maybeCached : fetchSkin(Optional.empty(), event.getUsername(), false, false);
            SkinUtils.FetchResult skin = toSet.orElse(defaultSkins.next());
            if (skin != null) {
                event.setGameProfile(skinUtils.applySkin(event.getGameProfile(), skin.getProperty()));
                if (!maybeCached.isPresent()) {
                    service.execute(() -> {
                        dataBaseUtils.saveUser(event.getUsername(), skin);
                    });
                }
            }
        }
    }

    private boolean loadConfig() {
        File config = new File(dataDirectory.toFile(), "config.yml");
        config.getParentFile().mkdir();
        try {
            if (!config.exists()) {
                try (InputStream in = SimpleSkins.class.getClassLoader().getResourceAsStream("config.yml")) {
                    Files.copy(in, config.toPath());
                }
            }
            this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(config);
        } catch (IOException ex) {
            logger.error("Can not load or save config", ex);
            return false;
        }
        return true;
    }

    private void initDefaultSkins() {
        logger.info("Loading default skins");
        List<SkinUtils.FetchResult> skins = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        for (String user : config.getStringList("default-skins")) {
            futures.add(service.submit(() -> fetchSkin(Optional.empty(), user, true, false)
                    .ifPresent(skin -> skins.add(new SkinUtils.FetchResult(null, skin.getProperty())))));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException ex) {
            }
        }
        this.defaultSkins = new RoundIterator<>(skins);
        logger.info("Default skins loaded");
    }

    public Optional<SkinUtils.FetchResult> fetchSkin(Optional<Player> player, String name, boolean logError, boolean fromDatabase) {
        try {
            return fromDatabase ? dataBaseUtils.getProperty(name) : Optional.of(skinUtils.fetchSkin(name));
        } catch (SkinUtils.UserNotFoundExeption ex) {
            if (logError) {
                logger.error("Can not fetch skin for '{}': {}", name, ex.getMessage());
            }
        } catch (IOException | JsonSyntaxException ex) {
            logger.error("Can not fetch skin", ex);
        }
        player.ifPresent(pl -> pl.sendMessage(ComponentSerializers.LEGACY.deserialize(config.getString("messages.skin-not-found"), '&')));
        return Optional.empty();
    }

    public Logger getLogger() {
        return logger;
    }

    public ProxyServer getProxyServer() {
        return server;
    }

    public Configuration getConfig() {
        return config;
    }

    public ExecutorService getExecutorService() {
        return service;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public DataBaseUtils getDataBaseUtils() {
        return dataBaseUtils;
    }

    public SkinUtils getSkinUtils() {
        return skinUtils;
    }

}
