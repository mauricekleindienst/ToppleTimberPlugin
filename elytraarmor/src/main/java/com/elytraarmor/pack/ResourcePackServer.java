package com.elytraarmor.pack;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourcePackServer {

    private static final String MCMETA = """
        {
          "pack": {
            "pack_format": 55,
            "supported_formats": [46, 999],
            "description": "ElytraArmor — combined equipment models"
          }
        }
        """;

    // Wings layer — same as vanilla elytra.json
    private static final String WINGS = """
              "wings": [
                { "texture": "minecraft:elytra", "use_player_texture": true }
              ]
        """;

    private final HttpServer server;
    private final byte[] sha1;
    private final byte[] packBytes;

    public ResourcePackServer(int port) throws IOException {
        this.packBytes = buildZip();
        this.sha1      = sha1(packBytes);

        server = HttpServer.create(new InetSocketAddress(port), 64);
        server.createContext("/pack.zip", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, packBytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(packBytes);
            }
        });
        server.setExecutor(null);
        server.start();
    }

    public byte[] getSha1()      { return sha1; }
    public byte[] getPackBytes() { return packBytes; }
    public void   stop()         { server.stop(0); }

    // ── Pack contents ─────────────────────────────────────────────────────

    private static byte[] buildZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            addEntry(zip, "pack.mcmeta", MCMETA);

            // Simple single-texture armor tiers
            for (String tier : new String[]{"chainmail", "gold", "iron", "diamond", "netherite"}) {
                addEntry(zip, "assets/elytraarmor/equipment/" + tier + "_elytra.json",
                    simpleModel(tier));
            }

            // Leather — dyeable base + fixed overlay
            addEntry(zip, "assets/elytraarmor/equipment/leather_elytra.json", leatherModel());
        }
        return baos.toByteArray();
    }

    private static String simpleModel(String tier) {
        return """
            {
              "layers": {
                "humanoid": [
                  { "texture": "minecraft:%s" }
                ],
            %s  }
            }
            """.formatted(tier, WINGS);
    }

    private static String leatherModel() {
        return """
            {
              "layers": {
                "humanoid": [
                  { "texture": "minecraft:leather", "dyeable": { "color_when_undyed": -6265536 } },
                  { "texture": "minecraft:leather_overlay" }
                ],
            %s  }
            }
            """.formatted(WINGS);
    }

    private static void addEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
