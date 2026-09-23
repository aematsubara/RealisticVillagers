package me.matsubara.realisticvillagers.appearance;

import com.sun.net.httpserver.HttpServer;
import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Hands the appearance pack to players itself, so nobody has to host anything.
 * <p>
 * The shapes live in a resource pack, and a pack normally means a web server, a link and a hash
 * pasted into server.properties — three chores between installing a plugin and seeing it work, and
 * three things to get wrong. The game has been able to take a second pack alongside whatever a
 * server already uses since 1.20.3, so this serves the file over a small HTTP server of its own and
 * hands each player the link as they join. It arrives in the ten or twenty seconds it takes to
 * download, and nothing they already had is replaced.
 * <p>
 * The web server is the one built into Java. No library, no shading, and it only ever answers with
 * one file it made itself.
 */
public final class PackServer implements Listener {

    private final RealisticVillagers plugin;

    private @Nullable HttpServer http;
    private byte @Nullable [] zip;
    private byte @Nullable [] sha1;
    private @Nullable String url;

    /**
     * Names the pack so it can be replaced rather than stacked.
     * <p>
     * Fixed rather than random: a player who reconnects after a restart should have the old copy
     * swapped for the new one, not end up wearing both.
     */
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("realisticvillagers-appearance".getBytes());

    private static final String PATH = "/appearance.zip";

    public PackServer(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    /**
     * Reads the pack, starts serving it and works out the link players will be given.
     *
     * @return whether it is being served.
     */
    public boolean start(@NotNull java.io.File file, int port, @NotNull String host) {
        stop();

        try {
            zip = Files.readAllBytes(file.toPath());
            sha1 = MessageDigest.getInstance("SHA-1").digest(zip);

            http = bind(port);
            port = http.getAddress().getPort();
            http.createContext(PATH, exchange -> {
                byte[] body = zip;
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }

                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, body.length);

                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });

            // A single thread: this serves one small file to each player once.
            http.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "RealisticVillagers-Pack");
                thread.setDaemon(true);
                return thread;
            }));

            http.start();

            url = "http://" + resolve(host) + ":" + port + PATH;
            plugin.getLogger().info("Appearance pack served at " + url
                    + " (" + zip.length / 1024 + " KB); players are given it as they join.");

            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not serve the appearance pack ("
                    + throwable.getMessage() + "). Host " + file.getName() + " yourself if you want body traits.");
            stop();
            return false;
        }
    }

    /**
     * Opens the first free port at or just above the one asked for.
     * <p>
     * A fixed port is one more thing to collide with — a second server on the same machine, or the
     * last one still letting go of the socket. Rather than give up and leave players without the
     * shapes, it steps along a few and says which it settled on.
     */
    private @NotNull HttpServer bind(int wanted) throws java.io.IOException {
        java.io.IOException last = null;

        for (int port = wanted; port < wanted + 10; port++) {
            try {
                return HttpServer.create(new InetSocketAddress(port), 0);
            } catch (java.io.IOException exception) {
                last = exception;
            }
        }

        throw last != null ? last : new java.io.IOException("no free port near " + wanted);
    }

    /**
     * The address a client should ask for the pack.
     * <p>
     * Guessed only when it has to be. The address a server binds is usually blank, meaning "all of
     * them", which is not something a client can connect to — so the machine's own address is used
     * instead. On anything but a home network that guess will be wrong, and the setting exists to
     * say so.
     */
    private @NotNull String resolve(@NotNull String host) {
        if (!host.isBlank() && !"auto".equalsIgnoreCase(host)) return host;

        String bound = plugin.getServer().getIp();
        if (!bound.isBlank()) return bound;

        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Throwable throwable) {
            return "127.0.0.1";
        }
    }

    /**
     * Swaps in a freshly written pack without dropping the port, and hands it to everyone online.
     * <p>
     * Where a shape sits is decided in the pack, because a worn item carries no transform anyone can
     * change from here. So a nudge to the fit is a new pack rather than a new packet, and restarting
     * for it would mean players reconnecting between guesses. This replaces only the bytes and the
     * hash, and the changed hash is what tells each client to fetch it again rather than reuse what
     * it cached.
     *
     * @return whether the new pack is now the one being served.
     */
    public boolean reload(@NotNull java.io.File file) {
        if (http == null) return false;

        byte[] fresh;
        byte[] hash;

        try {
            fresh = Files.readAllBytes(file.toPath());
            hash = MessageDigest.getInstance("SHA-1").digest(fresh);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not re-read the appearance pack: " + throwable.getMessage());
            return false;
        }

        // Written again, but not necessarily different. Handing out an unchanged pack is not free:
        // the hash is what a client decides by, so an identical one is quietly ignored, but only
        // after it has been offered — and a pack offered is a pack the player is asked about.
        boolean same = sha1 != null && java.util.Arrays.equals(sha1, hash);

        zip = fresh;
        sha1 = hash;

        if (!same) sendToAll();
        return true;
    }

    /** Gives the pack to everyone already online, for a reload. */
    public void sendToAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) send(player);
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        send(event.getPlayer());

        // Nothing of ours is drawn for them until their client says it has the pack. The figures
        // already standing around them would otherwise be placeholders for as long as the download
        // takes — and for ever, for somebody who refuses it.
        var figures = plugin.getPlayerAppearanceManager();
        if (figures != null) figures.hideFrom(event.getPlayer());
    }

    /**
     * What each player's client said about the pack, or nothing if it has not said anything.
     * <p>
     * Kept because a pack that never arrived and a figure drawn too faintly look the same from a
     * chat window: in both cases the answer to "is it working" is "not really". They are not the
     * same problem, and without this the only way to tell them apart is to guess.
     */
    private final java.util.Map<UUID, String> accepted = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Notes what a client did with the pack, and says so out loud when it did not take it.
     * <p>
     * Out loud because the failure is otherwise silent, and its symptom is misleading: a model the
     * client cannot resolve is drawn as a small dark placeholder, which looks like a figure that
     * came out wrong rather than a pack that never came at all. An admin seeing that would go
     * looking in the wrong place, which is exactly what happened here.
     */
    @EventHandler
    public void onPackStatus(org.bukkit.event.player.@NotNull PlayerResourcePackStatusEvent event) {
        // Other plugins hand out packs too, and a client refusing one of those says nothing about
        // this one. The id is the one it was sent under.
        if (!ours(event)) return;

        String status = event.getStatus().name();
        accepted.put(event.getPlayer().getUniqueId(), status);

        // What the client can draw has just changed, so what it is shown changes with it.
        var figures = plugin.getPlayerAppearanceManager();
        if (figures != null) {
            if (status.equals("SUCCESSFULLY_LOADED")) figures.showTo(event.getPlayer());
            else figures.hideFrom(event.getPlayer());
        }

        if (status.equals("SUCCESSFULLY_LOADED") || status.equals("ACCEPTED")
                || status.equals("DOWNLOADED")) return;

        String why = status.equals("DECLINED")
                ? "declined it"
                : "could not fetch it (" + status.toLowerCase(java.util.Locale.ROOT) + ")";

        plugin.getLogger().warning(event.getPlayer().getName() + " " + why + " from " + url
                + " — body shapes will not be drawn for them. If this happens to everyone, that"
                + " address is not reachable from where players connect; set appearance.pack-host"
                + " to one that is.");
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.@NotNull PlayerQuitEvent event) {
        accepted.remove(event.getPlayer().getUniqueId());
    }

    /** Whether this status is about the pack the figures are drawn from, and not somebody else's. */
    private boolean ours(org.bukkit.event.player.@NotNull PlayerResourcePackStatusEvent event) {
        try {
            return PACK_ID.equals(event.getID());
        } catch (Throwable ignored) {
            // A server old enough to report a status without saying which pack it was about only
            // ever has one in play, and on such a server this is it.
            return true;
        }
    }

    /**
     * Whether this player's client is holding the pack the figures are made of.
     * <p>
     * Anything short of loaded counts as no, the half-way answers included: a client still
     * fetching the pack cannot draw a model out of it any more than one that refused it can, and
     * what it draws in the meantime is the placeholder this is all here to avoid.
     */
    public boolean hasPack(@NotNull Player player) {
        return "SUCCESSFULLY_LOADED".equals(accepted.get(player.getUniqueId()));
    }

    /** What this player's client last said about the pack, in words rather than in an enum. */
    public @NotNull String statusFor(@NotNull Player player) {
        String status = accepted.get(player.getUniqueId());
        if (status == null) return "&enot answered yet";

        return switch (status) {
            case "SUCCESSFULLY_LOADED" -> "&aloaded";
            case "ACCEPTED", "DOWNLOADED" -> "&estill arriving (" + status.toLowerCase(java.util.Locale.ROOT) + ")";
            case "DECLINED" -> "&crefused by the player — figures cannot be drawn without it";
            case "FAILED_DOWNLOAD", "FAILED_RELOAD", "INVALID_URL" ->
                    "&cnever arrived (" + status.toLowerCase(java.util.Locale.ROOT)
                            + ") — check appearance.pack-host reaches them";
            default -> "&e" + status.toLowerCase(java.util.Locale.ROOT);
        };
    }

    /**
     * Adds the pack for one player.
     * <p>
     * Added rather than set, so a server that already sends its own pack keeps it: the client holds
     * several at once and this one only contributes the shapes. Not forced either — a player who
     * refuses simply sees villagers as they were.
     */
    private void send(@NotNull Player player) {
        if (url == null || sha1 == null) return;

        try {
            player.addResourcePack(PACK_ID, url, sha1, "Villager appearance", false);
        } catch (Throwable throwable) {
            // An old client, or one that refused: villagers are drawn without the shapes.
        }
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }

        zip = null;
        sha1 = null;
        url = null;
    }

    public @Nullable String url() {
        return url;
    }
}
