package me.statsplugin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class WebServer {

    private final JavaPlugin plugin;
    private final VanillaStatsReader reader;
    private final String bind;
    private final int port;
    private HttpServer server;

    public WebServer(JavaPlugin plugin, VanillaStatsReader reader, String bind, int port) {
        this.plugin  = plugin;
        this.reader  = reader;
        this.bind    = bind;
        this.port    = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "StatsDashboard");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // ── Request dispatch ──────────────────────────────────────────────────────

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/api/stats")) {
            respond(ex, "application/json", buildJson());
        } else {
            respond(ex, "text/html; charset=utf-8", buildHtml());
        }
    }

    private void respond(HttpExchange ex, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ── HTML ──────────────────────────────────────────────────────────────────

    private String buildHtml() {
        List<PlayerStat> stats = reader.getCached();

        // Online player UUIDs — reading from web thread is acceptable for a dashboard
        Set<UUID> online = Bukkit.getOnlinePlayers().stream()
            .map(p -> p.getUniqueId()).collect(Collectors.toSet());

        long totalMined = stats.stream().mapToLong(PlayerStat::totalMined).sum();
        long onlineCount = online.size();
        long maxMined = stats.isEmpty() ? 1L : stats.get(0).totalMined();
        String topPlayer = stats.isEmpty() ? "—" : esc(stats.get(0).name());

        StringBuilder rows = new StringBuilder();
        if (stats.isEmpty()) {
            rows.append("<tr><td colspan='6' class='empty'>No stats yet — let players join and break things!</td></tr>");
        } else {
            for (int i = 0; i < stats.size(); i++) {
                rows.append(row(i + 1, stats.get(i), maxMined, online));
            }
        }

        return HTML
            .replace("%%ONLINE%%",      String.valueOf(onlineCount))
            .replace("%%TOTAL_PLAYERS%%", String.valueOf(stats.size()))
            .replace("%%TOTAL_MINED%%", fmt(totalMined))
            .replace("%%TOP_PLAYER%%",  topPlayer)
            .replace("%%ROWS%%",        rows.toString());
    }

    private String row(int rank, PlayerStat s, long maxMined, Set<UUID> online) {
        String rankCls = rank <= 3 ? "rank-" + rank : "";
        int pct = maxMined > 0 ? (int) (s.totalMined() * 100L / maxMined) : 0;
        boolean isOnline = online.contains(s.uuid());
        String onlineDot = isOnline
            ? "<span class='dot online' title='Online'>●</span>"
            : "<span class='dot offline' title='Offline'>●</span>";
        String playtime = fmtTime(s.playtimeSeconds());

        return "<tr>"
            + "<td class='rank " + rankCls + "'>" + rank + "</td>"
            + "<td><div class='pc'>"
            +   onlineDot
            +   "<img class='head' src='https://crafatar.com/avatars/" + s.uuid()
            +       "?size=48&amp;default=MHF_Steve&amp;overlay' alt='" + esc(s.name()) + "' loading='lazy'>"
            +   "<div class='pinfo'><span class='pname'>" + esc(s.name()) + "</span>"
            +     "<span class='sub'>" + esc(s.topBlock()) + "</span></div>"
            + "</div></td>"
            + "<td><div class='count'>" + String.format("%,d", s.totalMined()) + "</div>"
            +     "<div class='bar-bg'><div class='bar-fill' style='width:" + pct + "%'></div></div></td>"
            + "<td class='stat'>" + playtime + "</td>"
            + "<td class='stat death'>" + s.deaths() + "</td>"
            + "<td class='stat kill'>" + s.kills() + "</td>"
            + "</tr>\n";
    }

    // ── JSON API ──────────────────────────────────────────────────────────────

    private String buildJson() {
        StringBuilder sb = new StringBuilder("[");
        List<PlayerStat> stats = reader.getCached();
        for (int i = 0; i < stats.size(); i++) {
            if (i > 0) sb.append(",");
            PlayerStat s = stats.get(i);
            sb.append("{")
              .append("\"uuid\":\"").append(s.uuid()).append("\",")
              .append("\"name\":\"").append(escJson(s.name())).append("\",")
              .append("\"mined\":").append(s.totalMined()).append(",")
              .append("\"playtime\":").append(s.playtimeSeconds()).append(",")
              .append("\"deaths\":").append(s.deaths()).append(",")
              .append("\"kills\":").append(s.kills())
              .append("}");
        }
        return sb.append("]").toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String esc(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private static String escJson(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"");
    }
    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
    private static String fmtTime(long secs) {
        if (secs <= 0) return "—";
        long h = secs / 3600, m = (secs % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    // ── HTML template ─────────────────────────────────────────────────────────

    private static final String HTML = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>MCStats — Server Dashboard</title>
          <link rel="preconnect" href="https://fonts.googleapis.com">
          <link href="https://fonts.googleapis.com/css2?family=VT323&display=swap" rel="stylesheet">
          <style>
            :root {
              --green:   #5cba3c; --dkgreen: #1e3d12;
              --gold:    #ffaa00; --dkgold:  #7a5000;
              --red:     #e05252; --dkred:   #6a1a1a;
              --blue:    #52aaee; --dkblue:  #1a3a6a;
              --bg:      #1a1a1a; --panel:   #2a2a2a;
              --border:  #555;    --text:    #f0f0f0;
              --muted:   #888;    --dirt:    #4a3728;
            }
            *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
            body {
              font-family: 'VT323', monospace;
              background-color: var(--bg);
              background-image:
                repeating-linear-gradient(0deg,  rgba(0,0,0,.05) 0 2px, transparent 2px 16px),
                repeating-linear-gradient(90deg, rgba(0,0,0,.05) 0 2px, transparent 2px 16px);
              color: var(--text);
              min-height: 100vh;
              font-size: 18px;
            }

            /* ── HEADER ─────────────────────────────────────────────── */
            header {
              background: linear-gradient(180deg,
                #6ecf44 0%, #4aa828 6%, #2d7a1b 22%, #1e4d10 60%, #4a3728 100%);
              padding: 32px 20px 28px;
              text-align: center;
              border-bottom: 8px solid #2a1a10;
              position: relative;
            }
            header::after {
              content: '';
              position: absolute;
              bottom: 0; left: 0; right: 0; height: 8px;
              background: repeating-linear-gradient(90deg,
                #3d2a1a 0 16px, #2a1a0e 16px 32px);
            }
            .hdr-icon { font-size: 2.8rem; display: block; margin-bottom: 4px; }
            h1 {
              font-size: 4.5rem; letter-spacing: 6px; line-height: 1;
              color: #fff; text-shadow: 4px 4px 0 #111, -1px -1px 0 rgba(255,255,255,.15);
            }
            h1 .g { color: var(--green); text-shadow: 4px 4px 0 #0e2a07, -1px -1px 0 #7ddb55; }
            .subtitle { color: #b8f0a0; font-size: 1.4rem; margin-top: 8px; letter-spacing: 3px; }

            /* ── LAYOUT ──────────────────────────────────────────────── */
            .wrap { max-width: 1140px; margin: 0 auto; padding: 32px 20px; }

            /* ── CARDS ───────────────────────────────────────────────── */
            .cards {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
              gap: 16px; margin-bottom: 36px;
            }
            .card {
              background: var(--panel);
              border: 3px solid var(--border);
              box-shadow: inset 2px 2px 0 #3a3a3a, inset -2px -2px 0 #111, 5px 5px 0 #000;
              padding: 20px 16px; text-align: center;
            }
            .card-label { color: var(--muted); font-size: .95rem; letter-spacing: 3px; text-transform: uppercase; }
            .card-value { font-size: 3rem; color: var(--gold); text-shadow: 3px 3px 0 var(--dkgold); line-height: 1.2; margin-top: 4px; }
            .card-value.g  { color: var(--green); text-shadow: 3px 3px 0 #0e2a07; font-size: 2rem; }
            .card-value.bl { color: var(--blue);  text-shadow: 3px 3px 0 var(--dkblue); }

            /* ── SECTION ──────────────────────────────────────────────── */
            .sec {
              font-size: 2rem; color: var(--gold); text-shadow: 2px 2px 0 var(--dkgold);
              margin-bottom: 10px; display: flex; align-items: center; gap: 12px;
            }
            .sec::after { content: ''; flex: 1; height: 2px; background: linear-gradient(90deg, var(--gold), transparent); }
            .meta { display: flex; justify-content: space-between; align-items: center; color: #555; font-size: 1rem; margin-bottom: 12px; }
            .meta a { color: #555; text-decoration: none; }
            .meta a:hover { color: var(--green); }

            /* ── TABLE ────────────────────────────────────────────────── */
            .panel {
              background: var(--panel);
              border: 3px solid var(--border);
              box-shadow: inset 2px 2px 0 #3a3a3a, inset -2px -2px 0 #111, 5px 5px 0 #000;
              overflow-x: auto;
            }
            table { width: 100%; border-collapse: collapse; min-width: 640px; }
            thead tr { background: #14290d; border-bottom: 3px solid var(--green); }
            thead th { padding: 14px 14px; color: var(--green); font-size: 1.25rem; text-align: left; letter-spacing: 1px; white-space: nowrap; }
            tbody tr { border-bottom: 2px solid #2d2d2d; }
            tbody tr:last-child { border-bottom: none; }
            tbody tr:nth-child(odd)  { background: #252525; }
            tbody tr:nth-child(even) { background: #202020; }
            tbody tr:hover { background: #353535 !important; }
            td { padding: 10px 14px; vertical-align: middle; }

            /* rank */
            .rank { font-size: 2rem; width: 52px; text-align: center; color: var(--muted); }
            .rank-1 { color: #ffd700; text-shadow: 0 0 14px #ffd70055; }
            .rank-2 { color: #c0c0c0; text-shadow: 0 0 10px #c0c0c055; }
            .rank-3 { color: #cd7f32; text-shadow: 0 0 10px #cd7f3255; }

            /* player cell */
            .pc { display: flex; align-items: center; gap: 10px; }
            .dot { font-size: .7rem; line-height: 1; }
            .dot.online  { color: var(--green); }
            .dot.offline { color: #444; }
            .head { width: 48px; height: 48px; image-rendering: pixelated; border: 2px solid #555; background: #333; flex-shrink: 0; }
            .pinfo { display: flex; flex-direction: column; }
            .pname { font-size: 1.5rem; color: #fff; line-height: 1.1; }
            .sub { font-size: 1rem; color: var(--muted); }

            /* mined cell (count + bar) */
            .count { color: var(--green); font-size: 1.35rem; white-space: nowrap; }
            .bar-bg { background: #111; border: 2px solid #444; height: 8px; margin-top: 4px; min-width: 80px; }
            .bar-fill { height: 100%; background: linear-gradient(90deg, #1a4a0a 0%, var(--green) 100%); }

            /* stat cells */
            .stat { font-size: 1.35rem; white-space: nowrap; }
            .death { color: var(--red); }
            .kill  { color: var(--blue); }

            /* empty */
            .empty { text-align: center; color: #555; padding: 48px; font-size: 1.4rem; }

            /* footer */
            footer { text-align: center; color: #444; font-size: 1.1rem; padding: 28px; border-top: 2px solid #2a2a2a; margin-top: 48px; }
            footer .g { color: var(--green); }

            @media (max-width: 600px) {
              h1 { font-size: 2.8rem; }
              .bar-bg, .sub { display: none; }
            }
          </style>
        </head>
        <body>

        <header>
          <span class="hdr-icon">⛏</span>
          <h1><span class="g">MC</span>STATS</h1>
          <p class="subtitle">&#x26A1; Server Statistics Dashboard &#x26A1;</p>
        </header>

        <div class="wrap">
          <div class="cards">
            <div class="card">
              <div class="card-label">Online Now</div>
              <div class="card-value bl">%%ONLINE%%</div>
            </div>
            <div class="card">
              <div class="card-label">Known Players</div>
              <div class="card-value">%%TOTAL_PLAYERS%%</div>
            </div>
            <div class="card">
              <div class="card-label">Blocks Mined</div>
              <div class="card-value">%%TOTAL_MINED%%</div>
            </div>
            <div class="card">
              <div class="card-label">Top Miner</div>
              <div class="card-value g">%%TOP_PLAYER%%</div>
            </div>
          </div>

          <div class="sec">&#x26D2; Leaderboard</div>
          <div class="meta">
            <span>&#9679; green = online &nbsp;&nbsp; &#8635; refreshes every 30s</span>
            <span><a href="/api/stats">JSON API &rarr;</a></span>
          </div>

          <div class="panel">
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Player</th>
                  <th>Blocks Mined</th>
                  <th>Playtime</th>
                  <th>&#x2620; Deaths</th>
                  <th>&#x2694; Kills</th>
                </tr>
              </thead>
              <tbody>%%ROWS%%</tbody>
            </table>
          </div>
        </div>

        <footer><span class="g">StatsPlugin</span> &bull; Reads vanilla Minecraft statistics &bull; Paper</footer>

        <script>setTimeout(() => location.reload(), 30000);</script>
        </body>
        </html>
        """;
}
