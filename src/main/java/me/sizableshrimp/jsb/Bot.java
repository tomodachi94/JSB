package me.sizableshrimp.jsb;

import me.sizableshrimp.jsb.api.DiscordConfiguration;
import me.sizableshrimp.jsb.api.EventHandler;
import okhttp3.HttpUrl;
import org.fastily.jwiki.core.Wiki;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

public class Bot {
    public static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);
    private static Config config;
    private static Wiki wiki;
    private static long firstOnline;
    // private static final WQuery.QTemplate SITEINFO = new WQuery.QTemplate(FL.pMap("action", "query", "meta", "siteinfo", "siprop", "general"), "query");

    // Examples - https://github.com/fastily/jwiki/wiki/Examples
    public static void main(String[] args) {
        // Loads the wiki instance only if the config instance was loaded
        if (!loadConfig() || !loadWiki())
            return;

        DiscordConfiguration.login(config.getBotToken(), client -> {
            EventHandler handler = new EventHandler(wiki);
            handler.register(client.getEventDispatcher());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> client.logout().timeout(Duration.ofSeconds(15)).block()));
        }).block();
    }

    private static boolean loadWiki() {
        wiki = setupWiki(false);
        if (wiki == null) {
            LOGGER.error("Failed to login to MediaWiki");
            return false;
        }
        LOGGER.info("Logged into MediaWiki as user \"{}\"", wiki.whoami());
        return true;
    }

    private static boolean loadConfig() {
        try {
            String path = System.getenv("CONFIG");
            config = Config.getConfig(Path.of(path));
            LOGGER.info("Loaded config from path \"{}\"", path);
            return true;
        } catch (IOException e) {
            LOGGER.error("Couldn't load the config.", e);
            return false;
        }
    }

    /**
     * Setup the wiki instance using a {@link Config} instance and whether to enable jwiki's logger.
     * Note that this logger can be helpful for debugging, but does not integrate with ANY logging APIs.
     * Returns null if the config isn't loaded or the api endpoint is invalid.
     *
     * @param defaultLogger Whether to enable the default logger used by jwiki.
     * @return The created {@link Wiki} instance, or null.
     */
    private static Wiki setupWiki(boolean defaultLogger) {
        if (config == null || config.getApi() == null) {
            LOGGER.error("API endpoint is null");
            return null;
        }

        HttpUrl apiEndpoint = HttpUrl.parse(config.getApi());
        if (apiEndpoint == null) {
            LOGGER.error("Invalid API endpoint: {}", config.getApi());
            return null;
        }

        Wiki.Builder builder = new Wiki.Builder();
        if (config.doLogin())
            builder.withLogin(config.getUsername(), config.getPassword());
        return builder
                .withApiEndpoint(apiEndpoint)
                .withUserAgent(config.getUserAgent())
                .withDefaultLogger(defaultLogger)
                .build();
    }

    public static Config getConfig() {
        return config;
    }

    public static long getFirstOnline() {
        return firstOnline;
    }

    public static void setFirstOnline(long millis) {
        Bot.firstOnline = millis;
    }
}