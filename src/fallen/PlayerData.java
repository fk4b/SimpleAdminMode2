package fallen;

import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Time;
import arc.util.Timer;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Packets;

public class PlayerData {
    public int id;
    /** Raw display name (may include freeze icon from server). */
    public String name;
    /** Color/icon-stripped stable nick for history matching. */
    public String cleanName;
    public String uuid;
    public boolean online;
    public Player player;
    public int builds = 0;
    public int breaks = 0;
    public int configs = 0;
    public String ip = "none";
    public String locale = "nani";
    public boolean modded = false;
    public boolean mobile = false;
    public int timesJoined = 0;
    public int timesKicked = 0;
    public String[] ips = new String[0];
    public String[] names = new String[0];
    public boolean griefWarned = false;
    public boolean autoFrozen = false;
    public int traceAttempts = 0;
    public float lastTraceRequest = 0.0f;
    public Timer.Task traceTask;
    private static ObjectSet<Integer> autoTraceRequested;
    public static final int MAX_TRACE_ATTEMPTS = 3;
    public static final float TRACE_INTERVAL = 3.0f;

    public PlayerData(Player p) {
        this.id = p.id;
        this.name = p.name;
        this.cleanName = NameUtil.normalize(p.name);
        this.uuid = "Loading...";
        this.online = true;
        this.player = p;
    }

    /**
     * Update display name from the live player. Prefer keeping the longer/stable
     * clean base when the server only prepends a freeze glyph.
     */
    public void updateName(String raw) {
        if (raw == null) return;
        this.name = raw;
        String next = NameUtil.normalize(raw);
        if (next.isEmpty()) return;
        // If freeze icon was added, normalized form still matches previous cleanName
        if (this.cleanName == null || this.cleanName.isEmpty()
            || NameUtil.samePlayer(this.cleanName, next)
            || next.length() >= this.cleanName.length()) {
            this.cleanName = next;
        }
        // else: keep previous cleanName (server added junk that normalized poorly)
    }

    public static void setAutoTraceRequested(ObjectSet<Integer> set) {
        autoTraceRequested = set;
    }

    public void startTraceRequests(final Player p) {
        if (this.traceTask != null) {
            return;
        }
        // Admins cannot be traced — don't spam the server / chat with failed requests
        if (p != null && p.admin) {
            this.uuid = "admin?";
            SimpleAdminMode.notifyAdminDetected(p);
            return;
        }
        this.traceTask = new Timer.Task() {
            @Override
            public void run() {
                if (!(PlayerData.this.uuid == null || PlayerData.this.uuid.equals("Loading...") || PlayerData.this.uuid.equals("admin?") || PlayerData.this.uuid.equals("none"))) {
                    this.cancel();
                    PlayerData.this.traceTask = null;
                    return;
                }
                // If player became admin while we were tracing — stop and notify
                if (p != null && p.admin) {
                    this.cancel();
                    PlayerData.this.traceTask = null;
                    PlayerData.this.uuid = "admin?";
                    SimpleAdminMode.notifyAdminDetected(p);
                    return;
                }
                ++PlayerData.this.traceAttempts;
                PlayerData.this.lastTraceRequest = Time.time;
                Log.info("SimpleAdminMode: Attempt #/@ for @ (uuid='@')", PlayerData.this.traceAttempts, 3, PlayerData.this.name, PlayerData.this.uuid);
                if (p != null && p.unit() != null && autoTraceRequested != null) {
                    autoTraceRequested.add(p.id);
                    Call.adminRequest(p, Packets.AdminAction.trace, null);
                }
                if (PlayerData.this.traceAttempts >= 3) {
                    this.cancel();
                    PlayerData.this.traceTask = null;
                    if (PlayerData.this.player != null && PlayerData.this.player.admin) {
                        PlayerData.this.uuid = "admin?";
                        Log.info("SimpleAdminMode: @ marked as 'admin?'", PlayerData.this.name);
                        SimpleAdminMode.notifyAdminDetected(PlayerData.this.player);
                    } else {
                        PlayerData.this.uuid = "none";
                    }
                    return;
                }
                if (!(PlayerData.this.uuid == null || PlayerData.this.uuid.isEmpty() || PlayerData.this.uuid.equals("Loading...") || PlayerData.this.uuid.equals("admin?") || PlayerData.this.uuid.equals("none"))) {
                    this.cancel();
                    PlayerData.this.traceTask = null;
                }
            }
        };
        Timer.schedule(this.traceTask, 1.0f, 3.0f);
    }

    public void stopTraceRequests() {
        if (this.traceTask != null) {
            this.traceTask.cancel();
            this.traceTask = null;
            if (autoTraceRequested != null) {
                autoTraceRequested.remove(this.id);
            }
        }
    }

    public void updateFrom(Administration.TraceInfo info) {
        this.stopTraceRequests();
        this.uuid = info.uuid;
        this.ip = info.ip;
        this.locale = info.locale;
        this.modded = info.modded;
        this.mobile = info.mobile;
        this.timesJoined = info.timesJoined;
        this.timesKicked = info.timesKicked;
        this.ips = info.ips;
        this.names = info.names;
    }
}
