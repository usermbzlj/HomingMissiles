package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.service.HudPackServer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Logger;

public final class HudPackServerTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("homingmissiles-hud-test-");
        try {
            byte[] payload = "PK\u0003\u0004-test-hud-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Path hud = Files.createDirectories(temp.resolve("hud"));
            Files.write(hud.resolve(HudPackServer.PACK_FILE_NAME), payload);
            String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(payload));

            try (HudPackServer server = new HudPackServer(Logger.getLogger("HudPackServerTest"))) {
                HudPackServer.StartResult result = server.restart(temp,
                        new HudPackServer.Config(true, "127.0.0.1", 0, "/hud/test.zip", sha1));
                URI uri = URI.create("http://127.0.0.1:" + result.port() + result.path());
                HttpClient client = HttpClient.newHttpClient();

                HttpResponse<byte[]> get = client.send(
                        HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                equal(get.statusCode(), 200, "GET status");
                if (!java.util.Arrays.equals(get.body(), payload)) {
                    throw new AssertionError("GET payload mismatch");
                }
                equal(get.headers().firstValue("etag").orElse(""), '"' + sha1 + '"', "ETag");

                HttpResponse<byte[]> head = client.send(
                        HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                equal(head.statusCode(), 200, "HEAD status");
                equal(head.body().length, 0, "HEAD body");

                HttpResponse<byte[]> wrongPath = client.send(
                        HttpRequest.newBuilder(URI.create(uri + ".extra")).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                equal(wrongPath.statusCode(), 404, "exact path");
            }

            try (HudPackServer server = new HudPackServer(Logger.getLogger("HudPackServerTest"))) {
                try {
                    server.restart(temp, new HudPackServer.Config(
                            true, "127.0.0.1", 0, "/hud/test.zip", "0000000000000000000000000000000000000000"));
                    throw new AssertionError("SHA-1 mismatch must prevent startup");
                } catch (IOException expected) {
                    // Expected: fail closed before opening the listener.
                }
            }
        } finally {
            deleteTree(temp);
        }
        System.out.println("HudPackServerTest: PASS");
    }

    private static void equal(Object actual, Object expected, String name) {
        if (!actual.equals(expected)) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
