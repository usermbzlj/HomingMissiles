package cn.yjj.homingmissiles.service;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal in-process HTTP endpoint for the pixel HUD resource pack.
 *
 * <p>The payload is verified and copied into memory before the listener opens,
 * so later filesystem changes cannot alter what clients receive. Only one exact
 * path is exposed and only GET/HEAD are accepted.</p>
 */
public final class HudPackServer implements AutoCloseable {
    public static final String PACK_FILE_NAME = "HomingMissiles-HUD-1.21.11.zip";
    private static final long MAX_PACK_BYTES = 16L * 1024L * 1024L;

    private final Logger logger;
    private HttpServer server;
    private ExecutorService executor;

    public HudPackServer(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized StartResult restart(Path pluginDataFolder, Config config) throws IOException {
        close();
        if (!config.enabled()) {
            return StartResult.disabled();
        }

        Path dataFolder = pluginDataFolder.toAbsolutePath().normalize();
        Path pack = dataFolder.resolve("hud").resolve(PACK_FILE_NAME).normalize();
        if (!pack.startsWith(dataFolder)) {
            throw new IOException("HUD resource-pack path escaped the plugin data directory");
        }
        if (!Files.isRegularFile(pack, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("HUD resource pack not found or is a symbolic link: " + pack);
        }
        long size = Files.size(pack);
        if (size <= 0 || size > MAX_PACK_BYTES) {
            throw new IOException("HUD resource pack size is outside 1.." + MAX_PACK_BYTES + " bytes: " + size);
        }

        byte[] payload = Files.readAllBytes(pack);
        String actualSha1 = sha1(payload);
        if (!actualSha1.equalsIgnoreCase(config.sha1())) {
            throw new IOException("HUD resource pack SHA-1 mismatch: expected="
                    + config.sha1() + " actual=" + actualSha1);
        }

        HttpServer candidate = HttpServer.create(
                new InetSocketAddress(config.bindAddress(), config.port()), 16);
        ExecutorService candidateExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "HomingMissiles-HUD-HTTP");
            thread.setDaemon(true);
            return thread;
        });
        candidate.setExecutor(candidateExecutor);
        candidate.createContext(config.path(), exchange -> serve(exchange, config.path(), payload, actualSha1));
        candidate.start();

        server = candidate;
        executor = candidateExecutor;
        InetSocketAddress bound = candidate.getAddress();
        return new StartResult(true, bound.getHostString(), bound.getPort(), config.path(), actualSha1, payload.length);
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void serve(HttpExchange exchange, String expectedPath, byte[] payload, String sha1) {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            if (!expectedPath.equals(path)) {
                sendEmpty(exchange, 404);
                return;
            }
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                sendEmpty(exchange, 405);
                return;
            }

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/zip");
            headers.set("Cache-Control", "public, max-age=31536000, immutable");
            headers.set("ETag", '"' + sha1 + '"');
            headers.set("X-Content-Type-Options", "nosniff");
            if ("HEAD".equals(method)) {
                headers.set("Content-Length", Integer.toString(payload.length));
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(payload);
            }
        } catch (IOException | RuntimeException ex) {
            logger.log(Level.FINE, "HUD resource-pack HTTP request failed", ex);
        }
    }

    private static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    static String sha1(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(payload));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Java runtime does not provide SHA-1", ex);
        }
    }

    public record Config(boolean enabled, String bindAddress, int port, String path, String sha1) {
        public Config {
            Objects.requireNonNull(bindAddress, "bindAddress");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(sha1, "sha1");
        }
    }

    public record StartResult(boolean enabled, String bindAddress, int port, String path,
                              String sha1, int contentLength) {
        static StartResult disabled() {
            return new StartResult(false, "", -1, "", "", 0);
        }
    }
}
