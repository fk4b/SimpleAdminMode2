package fallen;

import arc.Core;
import arc.Events;
import arc.struct.ObjectFloatMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.world.blocks.logic.LogicBlock;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.regex.Pattern;

/**
 * Detects attem83-like logic and patches processors.
 * Auto-freeze is delayed + de-duplicated across multiple admins so that
 * freeze/unfreeze toggle spam does not happen.
 */
public class AntiAttemPatcher {
    private static final Pattern ATTEM_PATTERN = Pattern.compile(
        "(ubind @?[^ ]+)\\s+sensor (\\S+) @unit @flag\\s+op add (\\S+) \\3 1\\s+jump \\d+ greaterThanEq \\3 \\d+\\s+jump \\d+ (?:notEqual|always) ([^ ]+) \\2\\s+set \\3 0",
        Pattern.DOTALL
    );
    private static final String WARNING_CODE =
        "print \"Stop build this, or you will get BANNED\"\n" +
        "print \"https://mindustry.dev/attem\"\n";
    private static final float CONFIG_DELAY_SEC = 0.25f;
    /** Base wait before auto-freeze so other admins / server can react first. */
    private static final float FREEZE_BASE_DELAY = 1.0f;
    /** Extra stagger per admin-rank so only one admin typically freezes. */
    private static final float FREEZE_RANK_STAGGER = 0.6f;
    /** Don't re-freeze the same uuid for this many seconds. */
    private static final float FREEZE_COOLDOWN_SEC = 45f;

    private static final Queue<LogicBlock.LogicBuild> patchQueue = new ArrayDeque<>();
    private static boolean processingQueue = false;
    private static boolean loaded = false;

    /** uuid → Time.time when we last handled freeze for them */
    private static final ObjectFloatMap<String> freezeHandledAt = new ObjectFloatMap<>();
    /** uuid currently waiting on a scheduled freeze check */
    private static final ObjectSet<String> freezePending = new ObjectSet<>();

    private static final String[] CONFIRMED_ATTEM = new String[]{
        "read index cell1 1\njump 43 greaterThan i 0\nulocate building core false @copper outx outy found core\nucontrol move outx outy 0 0 0\nucontrol itemTake core itemNeed itemCap 0 0\nwrite outx cell1 5\nwrite outy cell1 6\nend\njump 51 equal item @lead\njump 51 equal item @coal\njump 51 equal item @lead\njump 51 equal item @graphite\njump 51 equal item @metaglass\njump 53 equal item @phase-fabric\njump 53 equal item @surge-alloy\njump 53 greaterThan index 7\nset container1 vault1",
        "write 8 cell1 1\nend\njump 125 greaterThan plast5 600\nsensor cPlast5 core @plastanium\njump 125 equal cPlast5 0\ncontrol config sorter1 @plastanium 0 0 0\nwrite 8 cell1 1\nend\njump 131 greaterThan surge5 500\nsensor cSurge5 core @surge-alloy\njump 131 equal cSurge5 0\ncontrol config sorter1 @surge-alloy 0 0 0\nwrite 8 cell1 1\nend\njump 137 greaterThan phase5 350\nsensor cPhase core @phase-fabric\njump 137 equal cPhase 0\ncontrol config sorter1 @phase-fabric 0 0 0\nwrite 8 cell1 1\nend\njump 143 greaterThan titanium5 100\nsensor cTitanium core @titanium\njump 143 equal cTitanium 0\ncontrol config sorter1 @titanium 0 0 0\nwrite 8 cell1 1\nend\ncontrol config sorter1 null 0 0 0\nend\ncontrol config sorter1 @titanium 0 0 0\n",
        "read max cell1 4\njump 25 notEqual max 0\nprint \"SET UNIT CAP HERE\"\nset max 32\nop mul fx @thisx -10000\nop add flag @thisy fx\nop ceil flag flag fx\nwrite flag cell1 0\nubind UnitType\njump 33 notEqual first null\nset first @unit\njump 34 always first @unit\njump 46 strictEqual first @unit\nop add i i 1\nsensor f @unit @flag",
        "jump 72 notEqual min-item null\nset Wait 1\njump 0 always min-item null\ncontrol config sorter1 min-item 0 0 0\nset Wait 0\nwrite index cell1 1\nwrite total cell1 7\nop div fullness total 14000\nwrite fullness cell1 8\nwrite c cell1 10\nwrite min cell1 11\njump 0 always 0 false",
        "read amount1 cell1 index1\nread amount1 cell1 index1\nop add amount1 amount1 amount\nop add amount2 amount2 amount\nwrite amount1 cell1 index1\nwrite amount2 cell1 index2\njump 17 always f 0\nset i 0\njump 73 greaterThanEq j 13\nwrite 0 cell1 j\nop add j j 1\njump 69 always j 13\nset j 0\nset first null\nend"
    };

    public static void load() {
        if (loaded) return;
        loaded = true;
        SimpleAdminMode.ensureTriggerListenersSafe();

        Events.on(EventType.WorldLoadEvent.class, e -> {
            freezeHandledAt.clear();
            freezePending.clear();
            if (Core.settings.getBool("sam-aa", true)) {
                Timer.schedule(() -> {
                    if (Vars.net.client() && Vars.player != null && Vars.player.unit() != null) {
                        scanExistingProcessors();
                    }
                }, 5f);
            }
        });

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (Core.settings.getBool("sam-aa", true)) {
                checkNewProcessor(event);
            }
        });

        Events.on(EventType.ConfigEvent.class, event -> {
            if (Core.settings.getBool("sam-aa", true)) {
                checkConfProcessor(event);
            }
        });
    }

    private static void checkNewProcessor(EventType.BlockBuildEndEvent event) {
        if (event.breaking || event.tile == null || event.tile.build == null) return;
        if (!Vars.net.client()) return;

        PlayerData playerData;
        if (event.unit == null || event.unit.getPlayer() == null) {
            Player fake = Player.create();
            fake.name = "[gray]<?>Unknown[]";
            playerData = new PlayerData(fake);
        } else {
            playerData = SimpleAdminMode.playerHistory.get(event.unit.getPlayer().id);
        }

        Building build = event.tile.build;
        if (build instanceof LogicBlock.LogicBuild) {
            LogicBlock.LogicBuild processor = (LogicBlock.LogicBuild) build;
            if (build.team == Vars.player.team() && containsBadCode(processor.code, playerData, processor)) {
                queuePatch(processor);
            }
        }
    }

    private static void checkConfProcessor(EventType.ConfigEvent event) {
        if (!Vars.net.client() || Vars.player == null || event.tile == null) return;

        PlayerData playerData;
        if (event.player == null) {
            Player fake = Player.create();
            fake.name = "[gray]<?>Unknown[]";
            playerData = new PlayerData(fake);
        } else {
            playerData = SimpleAdminMode.playerHistory.get(event.player.id);
        }

        Building build = event.tile;
        if (build instanceof LogicBlock.LogicBuild) {
            LogicBlock.LogicBuild processor = (LogicBlock.LogicBuild) build;
            if (build.team == Vars.player.team() && containsBadCode(processor.code, playerData, processor)) {
                queuePatch(processor);
            }
        }
    }

    private static void scanExistingProcessors() {
        if (Vars.player == null || Vars.player.team() == null) return;
        Seq<Building> builds = Vars.player.team().data().buildings;
        int patched = 0;
        for (Building build : builds) {
            if (!(build instanceof LogicBlock.LogicBuild)) continue;
            LogicBlock.LogicBuild processor = (LogicBlock.LogicBuild) build;
            if (build.team != Vars.player.team() || !containsBadCode(processor.code, null, processor)) continue;
            queuePatch(processor);
            ++patched;
        }
        if (patched > 0) {
            Vars.player.sendMessage(Core.bundle.format("sam.aa.patch-count", patched));
        }
    }

    private static void queuePatch(LogicBlock.LogicBuild processor) {
        if (!patchQueue.contains(processor)) {
            patchQueue.add(processor);
            processQueue();
        }
    }

    private static void processQueue() {
        if (patchQueue.isEmpty() || processingQueue) return;
        processingQueue = true;
        LogicBlock.LogicBuild processor = patchQueue.poll();
        patchProcessorImmediate(processor);
        Timer.schedule(() -> {
            processingQueue = false;
            processQueue();
        }, CONFIG_DELAY_SEC);
    }

    private static void patchProcessorImmediate(LogicBlock.LogicBuild processor) {
        if (processor == null) return;
        byte[] compressedCode = LogicBlock.compress(WARNING_CODE, processor.relativeConnections());
        processor.updateCode(WARNING_CODE);
        Call.tileConfig(Vars.player, processor, compressedCode);
        Vars.player.sendMessage(Core.bundle.format("sam.aa.patch-cords", processor.tileX(), processor.tileY()));
    }

    private static boolean containsBadCode(String code, PlayerData playerData, LogicBlock.LogicBuild processor) {
        if (code == null || code.isEmpty()) return false;

        boolean isBad = false;
        String dataName = playerData != null ? playerData.name : "[gray]<?>Unknown[]";
        String dataUuid = playerData != null ? playerData.uuid : null;

        if (ATTEM_PATTERN.matcher(code).find()) {
            Log.info("AntiAttemPatcher: Detected Regex Attem83 signature!");
            isBad = true;
        } else {
            for (String pattern : CONFIRMED_ATTEM) {
                if (code.contains(pattern)) {
                    Log.info("AntiAttemPatcher: Matched known forbidden code sample.");
                    isBad = true;
                    break;
                }
            }
        }

        if (!isBad) return false;

        if (dataUuid != null && !dataUuid.isEmpty()
            && !dataUuid.equals("Loading...") && !dataUuid.equals("none") && !dataUuid.equals("admin?")) {
            if (Core.settings.getBool("sam-aab", false)) {
                // auto-ban path
                if (Core.settings.getBool("sam-oaa", false)) {
                    Call.sendChatMessage(Core.bundle.format("sam.aa.ban-mes", dataName, dataUuid, processor.tileX(), processor.tileY()));
                } else {
                    Vars.player.sendMessage(Core.bundle.format("sam.aa.ban-mes", dataName, dataUuid, processor.tileX(), processor.tileY()));
                }
                Call.sendChatMessage("/ban " + dataUuid + " 1d here 5.2.3 Automatic ban. https://mindustry.dev/attem");
                BanKickMessages.ban(dataName != null ? dataName : dataUuid);
            } else {
                // auto-freeze path — delayed + de-duplicated
                scheduleSafeFreeze(dataName, dataUuid, processor.tileX(), processor.tileY(), playerData);
            }
        }
        return true;
    }

    /**
     * Wait 1s (+ stagger by admin rank), re-check if player is already frozen
     * or recently handled by us / another admin, then freeze once.
     */
    private static void scheduleSafeFreeze(String dataName, String dataUuid, int tileX, int tileY, PlayerData playerData) {
        if (freezePending.contains(dataUuid)) {
            Log.info("AntiAttemPatcher: freeze already pending for @", dataUuid);
            return;
        }
        if (wasRecentlyHandled(dataUuid)) {
            Log.info("AntiAttemPatcher: freeze recently handled for @, skip", dataUuid);
            return;
        }

        // If already frozen right now — don't even schedule
        Player target = findPlayerByUuid(dataUuid, playerData);
        if (isPlayerFrozen(target)) {
            markHandled(dataUuid);
            Vars.player.sendMessage(Core.bundle.format("sam.aa.already-frozen", dataName));
            return;
        }

        freezePending.add(dataUuid);
        float delay = FREEZE_BASE_DELAY + adminRank() * FREEZE_RANK_STAGGER;

        Log.info("AntiAttemPatcher: scheduling freeze for @ in @s (rank @)", dataUuid, delay, adminRank());

        Timer.schedule(() -> {
            freezePending.remove(dataUuid);

            if (wasRecentlyHandled(dataUuid)) {
                Log.info("AntiAttemPatcher: cooldown hit after delay for @", dataUuid);
                return;
            }

            Player p = findPlayerByUuid(dataUuid, playerData);
            if (isPlayerFrozen(p)) {
                markHandled(dataUuid);
                Vars.player.sendMessage(Core.bundle.format("sam.aa.already-frozen", dataName));
                Log.info("AntiAttemPatcher: player already frozen, skip freeze for @", dataUuid);
                return;
            }

            // Another admin with lower id is still online — they should handle it (if AA is on).
            // We still freeze if after our stagger nobody did — avoids "nobody freezes" when
            // lower-id admin has AA off. isPlayerFrozen check above covers the common case.
            markHandled(dataUuid);
            Call.sendChatMessage(Core.bundle.format("sam.aa.freeze-mes", dataName, dataUuid, tileX, tileY));
            // Auto-freeze uses "/freeze <id> true". Panel freeze still uses "/freeze <id>".
            Call.sendChatMessage("/freeze " + dataUuid + " true");
            if (playerData != null) playerData.autoFrozen = true;
            Log.info("AntiAttemPatcher: sent /freeze @ true", dataUuid);
        }, delay);
    }

    private static boolean wasRecentlyHandled(String uuid) {
        float t = freezeHandledAt.get(uuid, -999999f);
        return (Time.time - t) < FREEZE_COOLDOWN_SEC * 60f; // Time.time is in frames (~60/s)
    }

    private static void markHandled(String uuid) {
        freezeHandledAt.put(uuid, Time.time);
    }

    /**
     * Rank among online admins (0 = lowest id = "leader").
     * Used only to stagger freezes so multiple SAM admins don't toggle simultaneously.
     */
    private static int adminRank() {
        if (Vars.player == null) return 0;
        int myId = Vars.player.id;
        int rank = 0;
        for (Player p : Groups.player) {
            if (p != null && p.admin && p.id < myId) {
                rank++;
            }
        }
        return rank;
    }

    private static Player findPlayerByUuid(String uuid, PlayerData data) {
        if (data != null && data.player != null && data.online) {
            return data.player;
        }
        if (data != null) {
            Player byId = Groups.player.getByID(data.id);
            if (byId != null) return byId;
        }
        // fallback: scan history for matching uuid
        for (PlayerData pd : SimpleAdminMode.playerHistory.values()) {
            if (pd != null && uuid != null && uuid.equals(pd.uuid) && pd.player != null && pd.online) {
                return pd.player;
            }
        }
        return null;
    }

    /**
     * Heuristics for "player is frozen" (snowflake / block unit / unmoving).
     * Freeze icon near nick usually = StatusEffects.freezing on the unit,
     * or unit type block / unmoving status from the plugin.
     */
    public static boolean isPlayerFrozen(Player p) {
        if (p == null) return false;
        // Local flag set by our own auto-freeze
        PlayerData pd = SimpleAdminMode.playerHistory.get(p.id);
        if (pd != null && pd.autoFrozen) return true;

        Unit u = p.unit();
        if (u == null) return false;
        try {
            // Many plugins put frozen players into a "block" unit
            if (u.type == UnitTypes.block) return true;
        } catch (Throwable ignored) {}
        try {
            // Snowflake status icon near nick
            if (u.hasEffect(StatusEffects.freezing)) return true;
            if (u.hasEffect(StatusEffects.unmoving)) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}
