package fallen;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.style.Drawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.IntSet;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.graphics.Pal;
import mindustry.input.InputHandler;
import mindustry.input.MobileInput;
import mindustry.mod.Mod;
import mindustry.net.Administration;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.TraceDialog;
import mindustry.ui.fragments.ChatFragment;

import java.lang.reflect.Method;

public class SimpleAdminMode extends Mod {
    private SimpleAdminList adminList = new SimpleAdminList();
    public static ObjectMap<Integer, PlayerData> playerHistory = new ObjectMap<>();
    private ObjectSet<Integer> autoTraceRequested = new ObjectSet<>();
    private IntSet knownPlayerIds = new IntSet();
    private static TraceDialog originalTraces;
    private static ObjectMap<Integer, Float> lastAutoTime;
    private static String lastMapName;
    private static String lastMapAuthor;
    private static String lastServerAddr;
    private static double lastPlaytime;
    private static boolean triggerPadded = false;

    /** Top banner state (like core-under-attack, but under core items). */
    private static float adminBannerTime = 0f;
    private static float adminBannerElapsed = 0f;
    private static String adminBannerText = "";
    /** ~4s @60fps — similar visibility window to core-under-attack strip. */
    private static final float ADMIN_BANNER_DURATION = 240f;
    private static final IntSet adminBannerShown = new IntSet();

    /**
     * FOO / older Arc: Events.fire(Enum) does not bounds-check ordinals.
     * Registering only low-ordinal Triggers leaves a short array → AIOOBE Index N
     * when the client fires a higher Trigger. Pad by touching the last constant.
     */
    public static void ensureTriggerListenersSafe() {
        if (triggerPadded) return;
        triggerPadded = true;
        try {
            EventType.Trigger[] all = EventType.Trigger.values();
            if (all.length > 0) {
                // No-op on highest ordinal forces full-length listener array
                Events.run(all[all.length - 1], () -> {});
            }
        } catch (Throwable t) {
            Log.err("[SAM] Trigger pad failed (non-fatal)", t);
        }
    }

    public SimpleAdminMode() {
        // Pad before any Trigger.run registration in this mod
        ensureTriggerListenersSafe();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            try {
                // Mobile-friendly defaults (only if user never touched them)
                if (Vars.mobile) {
                    if (!Core.settings.has("sam-btn-size")) {
                        Core.settings.put("sam-btn-size", 48);
                    }
                    if (!Core.settings.has("sam-list-w")) {
                        int sceneW = Core.scene != null ? (int) Core.scene.getWidth() : 400;
                        int w = Math.min(420, Math.max(280, (int) (sceneW * 0.85f)));
                        Core.settings.put("sam-list-w", w);
                    }
                }

                PlayerStatsTracker.init();
                HistoryRender.init();
                AntiAttemPatcher.load();
                BanEvidenceLogger.init();
                this.adminList.build(Core.scene.root);
                this.setupSettings();
                this.setupTraceOverride();
                this.setupAdminBanner();
                this.setupChatFilter();
                tryInstallFreeCam();
                setupHudButton();
            } catch (Throwable t) {
                Log.err("[SAM] ClientLoad failed", t);
            }
        });
        Events.on(EventType.WorldLoadEvent.class, e -> {
            try {
                boolean isSameSession;
                String currentMap = Vars.state.map != null ? Vars.state.map.name() : "";
                String currentAuthor = Vars.state.map != null ? Vars.state.map.author() : "";
                String currentServer = Vars.net.client()
                    ? (Vars.player != null && Vars.player.con != null ? Vars.player.con.address : "remote")
                    : "local";
                double currentPlaytime = Vars.state.tick;
                isSameSession = currentMap.equals(lastMapName) && currentAuthor.equals(lastMapAuthor)
                    && currentServer.equals(lastServerAddr) && currentPlaytime >= lastPlaytime - 600.0;
                if (!isSameSession) {
                    playerHistory.clear();
                    this.autoTraceRequested.clear();
                    this.knownPlayerIds.clear();
                    lastAutoTime.clear();
                    adminBannerShown.clear();
                    ActionsHistory.clearactionhistory();
                    HistoryRender.targetNick = null;
                    HistoryRender.targetPlayerId = -1;
                    Log.info("[SAM] New session detected (Map reset or change). History cleared.");
                } else {
                    Log.info("[SAM] Reconnect/Sync detected. History preserved. Time delta: "
                        + (currentPlaytime - lastPlaytime) / 60.0 + "s");
                }
                lastMapName = currentMap;
                lastMapAuthor = currentAuthor;
                lastServerAddr = currentServer;
                lastPlaytime = currentPlaytime;
                this.setupTraceOverride();
                Timer.schedule(() -> {
                    if (Vars.net.client() && Vars.player != null && Vars.player.unit() != null
                        && Core.settings.getBool("sam-vanish", false)) {
                        Call.sendChatMessage("/vanish 1");
                        Log.info("[#00ff]Vanish on");
                    }
                }, 5.0f);
                if (Vars.state.isMenu() || !Vars.net.active()) {
                    return;
                }
                Time.run(120.0f, () -> {
                    for (Player p : Groups.player) {
                        this.processPlayer(p);
                    }
                });
            } catch (Throwable t) {
                Log.err("[SAM] WorldLoad failed", t);
            }
        });
        Events.run(EventType.Trigger.update, () -> {
            try {
                if (Vars.state.isMenu() || !Vars.net.active()) {
                    return;
                }
                if (Vars.net.active() && Vars.state.isGame() && Core.graphics.getFrameId() % 60L == 0L) {
                    try {
                        this.syncPlayers();
                    } catch (Exception ex) {
                        Log.err("Error in syncPlayers", ex);
                    }
                }
                if (Vars.net.active() && Vars.state.isGame() && Core.graphics.getFrameId() % 300L == 0L) {
                    this.checkGriefers();
                }
                scrubCannotTraceMessages();
            } catch (Throwable ignored) {
                // never let update-hook crash the game (FOO client Events.fire path)
            }
        });
    }

    private void tryInstallFreeCam() {
        if (!Vars.mobile || !Core.settings.getBool("sam-freecam", false)) return;
        try {
            if (Vars.control == null || Vars.control.input == null) return;
            if (Vars.control.input instanceof FreeCamMobileInput) return;
            if (!(Vars.control.input instanceof MobileInput)) return;
            MobileInput oldInput = (MobileInput) Vars.control.input;
            FreeCamMobileInput newInput = new FreeCamMobileInput();
            newInput.block = oldInput.block;
            newInput.rotation = oldInput.rotation;
            newInput.mode = oldInput.mode;
            newInput.selectPlans.addAll(oldInput.selectPlans);
            newInput.linePlans.addAll(oldInput.linePlans);
            newInput.down = oldInput.down;
            newInput.manualShooting = oldInput.manualShooting;
            newInput.targetPos.set(oldInput.targetPos);
            newInput.movement.set(oldInput.movement);
            Vars.control.input = newInput;
        } catch (Throwable t) {
            Log.err("[SAM] FreeCam install failed (mobile)", t);
        }
    }

    private void setupHudButton() {
        Vars.ui.hudGroup.fill(t -> {
            t.name = "sam-hud-button";
            t.right();
            float btnSize = Vars.mobile ? 52f : 50f;
            t.table(bt -> {
                Cell adminBtnCell = bt.button((Drawable) Icon.admin, () -> {}).size(btnSize);
                ImageButton adminBtn = (ImageButton) adminBtnCell.get();
                float[] holdTimer = new float[]{0.0f};
                boolean[] longPressedTriggered = new boolean[]{false};
                adminBtn.update(() -> {
                    if (adminBtn.isPressed()) {
                        holdTimer[0] = holdTimer[0] + Core.graphics.getDeltaTime() * 60.0f;
                        if (holdTimer[0] > 60.0f && !longPressedTriggered[0]) {
                            Call.sendChatMessage("/history");
                            longPressedTriggered[0] = true;
                        }
                    } else {
                        holdTimer[0] = 0.0f;
                    }
                });
                adminBtn.clicked(() -> {
                    if (!longPressedTriggered[0]) {
                        this.adminList.toggle();
                    }
                    longPressedTriggered[0] = false;
                });
                if (Vars.mobile && Core.settings.getBool("sam-freecam", false)) {
                    bt.row();
                    bt.button((Drawable) Icon.move, Styles.clearNoneTogglei, () -> {
                        InputHandler input = Vars.control.input;
                        if (input instanceof FreeCamMobileInput) {
                            FreeCamMobileInput fi = (FreeCamMobileInput) input;
                            fi.setFreeCam(!fi.isFreeCam());
                        }
                    }).update(b -> {
                        boolean active = false;
                        InputHandler input = Vars.control.input;
                        if (input instanceof FreeCamMobileInput) {
                            FreeCamMobileInput fi = (FreeCamMobileInput) input;
                            active = fi.isFreeCam();
                        }
                        b.setChecked(active);
                        b.getImage().setColor(active ? Color.cyan : Color.white);
                    }).size(btnSize - 5f).tooltip("Free Camera");
                }
                int[] lastY = new int[]{-1};
                bt.update(() -> {
                    int currentY = Core.settings.getInt("sam-hud-y", Vars.mobile ? 80 : 60);
                    if (lastY[0] != currentY) {
                        bt.margin((float) currentY, 0.0f, 0.0f, Vars.mobile ? 14.0f : 10.0f);
                        lastY[0] = currentY;
                        bt.invalidateHierarchy();
                    }
                });
            });
        });
    }

    /** Called when an admin is detected (cannot be traced / has admin flag). */
    public static void notifyAdminDetected(Player p) {
        if (p == null || p == Vars.player) return; // don't banner yourself
        if (adminBannerShown.contains(p.id)) return;
        adminBannerShown.add(p.id);
        String name = p.name != null ? p.name : "?";
        adminBannerText = Core.bundle.format("sam.admin.joined", name);
        adminBannerTime = ADMIN_BANNER_DURATION;
        adminBannerElapsed = 0f;
        Log.info("[SAM] Admin joined banner: " + Strings.stripColors(name));
    }

    private void setupAdminBanner() {
        // Same style as "@coreattack": full-width top strip, black6 bar, orange↔scarlet pulse
        Vars.ui.hudGroup.fill(t -> {
            t.name = "sam-admin-banner";
            t.top();
            t.update(() -> {
                if (adminBannerTime > 0f && !Vars.state.isMenu() && !Vars.state.isPaused()) {
                    float d = Time.delta;
                    adminBannerTime -= d;
                    adminBannerElapsed += d;
                }

                // Sit under core resources (same place core-under-attack would appear)
                float top = 8f;
                try {
                    if (Vars.ui.hudfrag != null
                        && Vars.ui.hudfrag.shown
                        && Core.settings.getBool("coreitems", true)
                        && Vars.ui.hudfrag.coreItems != null) {
                        float h = Vars.ui.hudfrag.coreItems.getHeight();
                        if (h < 1f) h = Vars.ui.hudfrag.coreItems.getPrefHeight();
                        top = (h > 1f) ? (h + 6f) : 36f;
                    } else {
                        top = 8f;
                    }
                    if (Core.settings.getBool("macnotch", false)) {
                        top += 32f;
                    }
                } catch (Throwable ignored) {
                    top = 48f;
                }
                t.marginTop(top);
            });

            t.table(Styles.black6, banner -> {
                Label label = banner.add("").pad(8f).growX().get();
                label.setAlignment(arc.util.Align.center);
                label.setFontScale(1f);
                label.update(() -> {
                    label.setText(adminBannerText);
                    // Same pulse as core-under-attack (HudFragment coreattack)
                    label.color.set(Color.orange).lerp(Color.scarlet, Mathf.absin(Time.time, 2f, 1f));
                    banner.color.a = 1f;
                    label.color.a = 1f;
                });
            }).visible(() ->
                adminBannerTime > 0f
                    && !Vars.state.isMenu()
                    && Vars.ui.hudfrag.shown
                    && !Vars.state.isPaused()
            ).touchable(arc.scene.event.Touchable.disabled).fillX();
        });
    }

    private void setupChatFilter() {
        Events.on(EventType.PlayerChatEvent.class, e -> {
            if (e.message == null) return;
            if (isCannotTraceMessage(e.message)) {
                // Remove after ChatFragment inserts it
                Core.app.post(SimpleAdminMode::scrubCannotTraceMessages);
                Core.app.post(SimpleAdminMode::scrubCannotTraceMessages);
            }
        });
    }

    private static boolean isCannotTraceMessage(String message) {
        if (message == null) return false;
        String clean = Strings.stripColors(message).toLowerCase();
        return clean.contains("you cannot trace this player")
            || clean.contains("cannot trace this player")
            || clean.contains("нельзя отследить")
            || clean.contains("невозможно отследить");
    }

    private static void scrubCannotTraceMessages() {
        if (Vars.ui == null || Vars.ui.chatfrag == null) return;
        ChatFragment frag = Vars.ui.chatfrag;
        if (frag.messages == null || frag.messages.isEmpty()) return;
        frag.messages.removeAll(m -> m != null && isCannotTraceMessage(m.message));
    }

    private void checkGriefers() {
        if (!Core.settings.getBool("sam-ag-enabled", false)) {
            return;
        }
        int minB = Core.settings.getInt("sam-ag-min-build", 10);
        int maxBr = Core.settings.getInt("sam-ag-max-break", 100);
        int minJ = Core.settings.getInt("sam-ag-min-joins", 5);
        int maxK = Core.settings.getInt("sam-ag-max-kicks", 1);
        for (PlayerData data : playerHistory.values()) {
            if (!data.online || data.uuid.equals("Loading...")) continue;
            if (!data.griefWarned) {
                boolean suspicious = data.builds < minB && data.breaks > maxBr && data.timesJoined < minJ && data.timesKicked >= maxK;
                if (suspicious) {
                    data.griefWarned = true;
                    Vars.player.sendMessage(Core.bundle.format("sam.ag.alert", data.name) + "\n" + Core.bundle.format("sam.ag.stats", data.builds, data.breaks, data.timesJoined, data.timesKicked));
                }
            }
            if (data.player.admin || data.autoFrozen || !Core.settings.getBool("sam-ag-afr", false) || data.builds >= minB * 2 || data.breaks <= maxBr * 2) continue;
            data.autoFrozen = true;
            Call.sendChatMessage("/freeze " + data.uuid);
            Vars.player.sendMessage(Core.bundle.format("sam.ag.autoFreeze", data.name, maxBr * 2));
        }
    }

    private void setupSettings() {
        Vars.ui.settings.addCategory(Core.bundle.get("sam.settings.title"), (Drawable) Icon.admin, table -> {
            table.left().row();
            table.check(Core.bundle.get("sam.settings.stats"), Core.settings.getBool("sam-show-stats", false), val -> Core.settings.put("sam-show-stats", val)).left().row();
            table.check(Core.bundle.get("sam.settings.vanish"), Core.settings.getBool("sam-vanish", false), val -> Core.settings.put("sam-vanish", val)).left().row();
            table.check(Core.bundle.get("sam.settings.freecam"), Core.settings.getBool("sam-freecam", false), val -> Core.settings.put("sam-freecam", val)).left().row();
            this.addSlider(table, "sam.settings.btnSize", "sam-btn-size", 30, 80, 40);
            this.addSlider(table, "sam.settings.hudY", "sam-hud-y", 0, 600, 60);
            table.button(Core.bundle.get("sam.settings.resetSettings"), () -> {
                Core.settings.put("sam-show-stats", false);
                Core.settings.put("sam-btn-size", 40);
                Core.settings.put("sam-hud-y", 60);
                Vars.ui.showInfoFade(Core.bundle.get("sam.settings.resetDone"));
            }).margin(10.0f).width(240.0f).padTop(20.0f);
        });
    }

    private void addSlider(Table table, String bundleKey, String settingKey, int min, int max, int def) {
        table.table(t -> {
            t.left().defaults().left();
            t.add(Core.bundle.get(bundleKey)).row();
            t.table(s -> {
                s.slider((float) min, (float) max, 1.0f, (float) Core.settings.getInt(settingKey, def), val -> Core.settings.put(settingKey, (int) val)).width(350.0f).height(50.0f).padRight(10.0f);
                s.label(() -> String.valueOf(Core.settings.getInt(settingKey, def))).color(Pal.accent).width(40.0f);
            }).row();
        }).padTop(10.0f).row();
    }

    private void setupTraceOverride() {
        if (originalTraces == null) {
            originalTraces = Vars.ui.traces;
        }
        if (!(Vars.ui.traces instanceof CustomTraceDialog)) {
            Vars.ui.traces = new CustomTraceDialog();
            PlayerData.setAutoTraceRequested(this.autoTraceRequested);
            Log.info("SimpleAdminMode: TraceDialog overridden");
        }
    }

    private void syncPlayers() {
        IntSet currentIds = new IntSet();
        for (Player p : Groups.player) {
            currentIds.add(p.id);
            this.processPlayer(p);
        }
        currentIds.each(id -> {
            Player p;
            if (!this.knownPlayerIds.contains(id) && (p = Groups.player.getByID(id)) != null) {
                Log.info("SimpleAdminMode: New player ID=@ (name=@)", id, p.name);
                Vars.ui.showInfoFade("[green]+ [white]" + p.name);
            }
        });
        this.knownPlayerIds.each(id -> {
            if (!currentIds.contains(id)) {
                Log.info("SimpleAdminMode: Player left ID=@ ", id);
                PlayerData data = playerHistory.get(id);
                if (data != null) {
                    data.online = false;
                    data.stopTraceRequests();
                    Vars.ui.showInfoFade("[red]- [white]" + data.name);
                }
                adminBannerShown.remove(id);
            }
        });
        this.knownPlayerIds.clear();
        this.knownPlayerIds.addAll(currentIds);
    }

    private void processPlayer(Player p) {
        PlayerData data = playerHistory.get(p.id);
        if (data == null) {
            data = new PlayerData(p);
            playerHistory.put(p.id, data);
            if (p.admin) {
                data.uuid = "admin?";
                notifyAdminDetected(p);
            } else {
                data.startTraceRequests(p);
            }
        } else {
            // Keep display name, but preserve clean base name so freeze icons
            // (❄ near nick) do not break action-history matching.
            data.updateName(p.name);
            data.player = p;
            data.online = true;
            // If player gained admin mid-session while still being traced
            if (p.admin && (data.uuid.equals("Loading...") || data.uuid.equals("none"))) {
                data.stopTraceRequests();
                data.uuid = "admin?";
                notifyAdminDetected(p);
            }
        }
    }

    static {
        lastAutoTime = new ObjectMap<>();
        lastMapName = "";
        lastMapAuthor = "";
        lastServerAddr = "";
        lastPlaytime = 0.0;
    }

    public class CustomTraceDialog extends TraceDialog {
        public void show(Player player, Administration.TraceInfo info) {
            if (!this.handleTraceLogic(player, info)) {
                originalTraces.show(player, info);
            }
        }

        public void show(Player player, Administration.TraceInfo info, boolean offline) {
            if (!this.handleTraceLogic(player, info)) {
                try {
                    Method method = originalTraces.getClass().getMethod("show", Player.class, Administration.TraceInfo.class, Boolean.TYPE);
                    method.invoke(originalTraces, player, info, offline);
                } catch (Exception e) {
                    originalTraces.show(player, info);
                }
            }
        }

        private boolean handleTraceLogic(Player player, Administration.TraceInfo info) {
            PlayerData data = playerHistory.get(player.id);
            boolean wasAuto = SimpleAdminMode.this.autoTraceRequested.contains(player.id);
            if (data != null && info.uuid != null && !info.uuid.isEmpty()) {
                data.updateFrom(info);
            }
            float lastTime = lastAutoTime.get(player.id, 0f);
            boolean isDuplicate = Time.time - lastTime < 1.5f;
            if (wasAuto || isDuplicate) {
                lastAutoTime.put(player.id, Time.time);
                Log.info("📋 [accent]" + player.name + "[white]: " + (data != null && data.uuid.equals("admin?") ? "admin (local)" : "data received"));
                return true;
            }
            return false;
        }
    }
}
