package fallen;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Keeps a rolling window of player actions (build / break / chat) and appends
 * a short evidence dump when an admin bans someone.
 *
 * File format (all bans in one file, 3 blank lines between entries):
 *
 *   ник
 *   построено-
 *   ...
 *   сломано-
 *   ...
 *   написано в чат -
 *   ...
 *   время бана -
 *   2026-07-31 18:30:00
 *
 *
 *
 */
public final class BanEvidenceLogger {
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_EVENTS = 4000;
    private static final String DEFAULT_NAME = "sam-ban-evidence.txt";

    private static final Seq<Evt> events = new Seq<>();
    private static boolean loaded = false;

    private BanEvidenceLogger() {}

    /** Master switch — when off, no collection and no file writes. Default: on. */
    public static boolean enabled() {
        return Core.settings.getBool("sam-evidence-enabled", true);
    }

    public static void setEnabled(boolean on) {
        Core.settings.put("sam-evidence-enabled", on);
        if (!on) {
            events.clear();
        }
    }

    public static void init() {
        if (loaded) return;
        loaded = true;

        Events.on(EventType.BlockBuildBeginEvent.class, e -> {
            try {
                if (!enabled()) return;
                if (e == null || e.unit == null || e.unit.getPlayer() == null || e.tile == null) return;
                int id = e.unit.getPlayer().id;
                String nick = NameUtil.normalize(e.unit.getPlayer().name);
                short blockId;
                Building build = e.tile.build;
                if (build instanceof ConstructBlock.ConstructBuild) {
                    ConstructBlock.ConstructBuild cons = (ConstructBlock.ConstructBuild) build;
                    blockId = cons.current != null ? cons.current.id : e.tile.block().id;
                } else {
                    blockId = e.tile.block() != null ? e.tile.block().id : 0;
                }
                String blockName = blockLabel(blockId);
                String pos = "(" + e.tile.x + ", " + e.tile.y + ")";
                add(id, nick, e.breaking ? Kind.BREAK : Kind.BUILD, blockName + " " + pos);
            } catch (Throwable t) {
                Log.err("SAM BanEvidence build", t);
            }
        });

        Events.on(EventType.PlayerChatEvent.class, e -> {
            try {
                if (!enabled()) return;
                if (e == null || e.player == null || e.message == null) return;
                // skip commands
                String msg = e.message;
                if (msg.startsWith("/")) return;
                add(e.player.id, NameUtil.normalize(e.player.name), Kind.CHAT, Strings.stripColors(msg));
            } catch (Throwable t) {
                Log.err("SAM BanEvidence chat", t);
            }
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            // keep recent events across reconnects in same session; trim only old ones
            trimOld();
        });
    }

    private static String blockLabel(short blockId) {
        try {
            Block b = Vars.content.block(blockId);
            if (b != null) {
                String loc = b.localizedName;
                if (loc != null && !loc.isEmpty()) return loc;
                return b.name;
            }
        } catch (Throwable ignored) {}
        return "#" + blockId;
    }

    private static void add(int playerId, String nick, Kind kind, String detail) {
        long now = System.currentTimeMillis();
        events.add(new Evt(now, playerId, nick, kind, detail));
        if (events.size > MAX_EVENTS) {
            events.removeRange(0, events.size - MAX_EVENTS);
        }
        // occasional trim
        if (events.size % 200 == 0) trimOld();
    }

    private static void trimOld() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS * 5; // keep 5 min buffer
        while (events.size > 0 && events.first().time < cutoff) {
            events.remove(0);
        }
    }

    /** Default evidence file under game data directory (works desktop + mobile). */
    public static Fi defaultFile() {
        return Core.files.local(DEFAULT_NAME);
    }

    public static String getConfiguredPath() {
        String p = Core.settings.getString("sam-evidence-path", "");
        if (p == null || p.trim().isEmpty()) {
            return defaultFile().absolutePath();
        }
        return p.trim();
    }

    public static void setConfiguredPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            Core.settings.remove("sam-evidence-path");
        } else {
            Core.settings.put("sam-evidence-path", path.trim());
        }
    }

    public static Fi resolveFile() {
        String p = Core.settings.getString("sam-evidence-path", "");
        if (p == null || p.trim().isEmpty()) {
            return defaultFile();
        }
        p = p.trim();
        try {
            // Absolute path?
            Fi abs = new Fi(p);
            // If parent exists or path looks absolute, use it
            if (p.contains(":") || p.startsWith("/") || p.startsWith("\\")) {
                return abs;
            }
            // Relative to game local data
            return Core.files.local(p);
        } catch (Throwable t) {
            return defaultFile();
        }
    }

    /**
     * Append evidence for a ban (last 60 seconds of activity).
     */
    public static void writeOnBan(int playerId, String name, String uuid, String time, String reason) {
        if (!enabled()) return;
        try {
            long now = System.currentTimeMillis();
            long cutoff = now - WINDOW_MS;
            String nick = NameUtil.normalize(name);

            Seq<String> built = new Seq<>();
            Seq<String> broken = new Seq<>();
            Seq<String> chats = new Seq<>();

            for (Evt e : events) {
                if (e.time < cutoff) continue;
                boolean match = (playerId > 0 && e.playerId == playerId)
                    || NameUtil.samePlayer(e.nick, nick);
                if (!match) continue;
                switch (e.kind) {
                    case BUILD: built.add(e.detail); break;
                    case BREAK: broken.add(e.detail); break;
                    case CHAT: chats.add(e.detail); break;
                }
            }

            String banTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(now));
            StringBuilder sb = new StringBuilder();
            sb.append(nick.isEmpty() ? name : nick).append('\n');
            if (uuid != null && !uuid.isEmpty()) {
                sb.append("uuid- ").append(uuid).append('\n');
            }
            if (reason != null && !reason.isEmpty()) {
                sb.append("причина- ").append(Strings.stripColors(reason)).append('\n');
            }
            sb.append("построено-\n");
            if (built.isEmpty()) sb.append("(нет за последнюю минуту)\n");
            else for (String s : built) sb.append(s).append('\n');
            sb.append("сломано-\n");
            if (broken.isEmpty()) sb.append("(нет за последнюю минуту)\n");
            else for (String s : broken) sb.append(s).append('\n');
            sb.append("написано в чат -\n");
            if (chats.isEmpty()) sb.append("(нет за последнюю минуту)\n");
            else for (String s : chats) sb.append(s).append('\n');
            sb.append("время бана -\n");
            sb.append(banTime);
            if (time != null && !time.isEmpty()) {
                sb.append(" (").append(time).append(')');
            }
            sb.append("\n\n\n"); // 3 blank lines before next ban

            Fi file = resolveFile();
            try {
                Fi parent = file.parent();
                if (parent != null && !parent.exists()) parent.mkdirs();
            } catch (Throwable ignored) {}

            file.writeString(sb.toString(), true); // append
            Log.info("[SAM] Ban evidence written to @", file.absolutePath());
            if (Vars.ui != null) {
                Vars.ui.showInfoFade("[accent]Доказательства → [white]" + file.name());
            }
        } catch (Throwable t) {
            Log.err("SAM BanEvidence write failed", t);
            if (Vars.ui != null) {
                Vars.ui.showInfoFade("[red]Не удалось записать доказательства бана");
            }
        }
    }

    private enum Kind { BUILD, BREAK, CHAT }

    private static final class Evt {
        final long time;
        final int playerId;
        final String nick;
        final Kind kind;
        final String detail;

        Evt(long time, int playerId, String nick, Kind kind, String detail) {
            this.time = time;
            this.playerId = playerId;
            this.nick = nick != null ? nick : "";
            this.kind = kind;
            this.detail = detail != null ? detail : "";
        }
    }
}
