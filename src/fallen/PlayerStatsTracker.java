package fallen;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.ui.Fonts;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock;

public class PlayerStatsTracker {
    private static boolean inited = false;

    public static void init() {
        if (inited) return;
        inited = true;

        Events.on(EventType.BlockBuildBeginEvent.class, e -> {
            try {
                if (Core.settings.getBool("sam-ag-build-warn", true) || (e != null && e.breaking)) {
                    antiGrief(e);
                }
                if (!Core.settings.getBool("sam-show-stats", false) && !Core.settings.getBool("sam-log-save", false)) {
                    return;
                }
                if (e == null || e.unit == null || e.unit.getPlayer() == null) return;
                if (SimpleAdminMode.playerHistory == null) return;

                PlayerData data = SimpleAdminMode.playerHistory.get(e.unit.getPlayer().id);
                if (data == null) return;

                // Always keep a stable clean name for history keys
                String clean = NameUtil.normalize(data.name);

                if (Core.settings.getBool("sam-log-save", false) || Core.settings.getBool("sam-show-stats", false)) {
                    short blockId;
                    Building build = e.tile != null ? e.tile.build : null;
                    if (build instanceof ConstructBlock.ConstructBuild) {
                        ConstructBlock.ConstructBuild cons = (ConstructBlock.ConstructBuild) build;
                        blockId = cons.current != null ? cons.current.id : e.tile.block().id;
                    } else {
                        blockId = e.tile != null && e.tile.block() != null ? e.tile.block().id : 0;
                    }
                    int rotation = build != null ? build.rotation : 0;
                    Object config = build != null ? build.config() : null;
                    ActionsHistory.blocksplayersplans.addFirst(
                        new ActionsHistory.BlockPlayerPlan(
                            e.tile.x, e.tile.y, (short) rotation, blockId, config,
                            clean, e.breaking, data.id
                        )
                    );
                }
                if (e.breaking) {
                    ++data.breaks;
                } else {
                    ++data.builds;
                }
            } catch (Throwable t) {
                // never crash event bus
            }
        });

        Events.on(EventType.ConfigEvent.class, e -> {
            try {
                if (!Core.settings.getBool("sam-show-stats", false) || e == null || e.player == null) return;
                PlayerData data = SimpleAdminMode.playerHistory.get(e.player.id);
                if (data != null) {
                    ++data.configs;
                    if (Core.settings.getBool("sam-log-save", false) || Core.settings.getBool("sam-show-stats", false)) {
                        String clean = NameUtil.normalize(data.name);
                        short blockId = e.tile != null && e.tile.block != null ? e.tile.block.id : 0;
                        int tx = e.tile != null ? (int) e.tile.x / 8 : 0;
                        int ty = e.tile != null ? (int) e.tile.y / 8 : 0;
                        ActionsHistory.blockconfplayersplans.addFirst(
                            new ActionsHistory.BlockConfigPlayerPlan(tx, ty, blockId, clean, data.id)
                        );
                    }
                }
            } catch (Throwable ignored) {}
        });

        Events.on(EventType.BuildRotateEvent.class, e -> {
            try {
                if (!Core.settings.getBool("sam-show-stats", false) || e == null || e.unit == null || e.unit.getPlayer() == null) {
                    return;
                }
                PlayerData data = SimpleAdminMode.playerHistory.get(e.unit.getPlayer().id);
                if (data != null) {
                    ++data.configs;
                    if (Core.settings.getBool("sam-log-save", false) || Core.settings.getBool("sam-show-stats", false)) {
                        String clean = NameUtil.normalize(data.name);
                        short blockId = e.build != null && e.build.block != null ? e.build.block.id : 0;
                        int tx = e.build != null ? (int) e.build.x / 8 : 0;
                        int ty = e.build != null ? (int) e.build.y / 8 : 0;
                        ActionsHistory.blockconfplayersplans.addFirst(
                            new ActionsHistory.BlockConfigPlayerPlan(tx, ty, blockId, clean, data.id)
                        );
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void antiGrief(EventType.BlockBuildBeginEvent e) {
        if (e == null || e.breaking || e.unit == null || e.unit.getPlayer() == null) return;
        Building building = e.tile != null ? e.tile.build : null;
        if (!(building instanceof ConstructBlock.ConstructBuild)) return;
        ConstructBlock.ConstructBuild cons = (ConstructBlock.ConstructBuild) building;
        Block block = cons.current;
        if (block == null) return;
        String key = "";
        if (block == Blocks.thoriumReactor) {
            key = "thorium";
        } else if (block == Blocks.incinerator) {
            key = "incinerator";
        } else if (block == Blocks.melter) {
            key = "melter";
        }
        if (key.isEmpty()) return;
        if (!Core.settings.getBool("sam-ag-" + key + "-enabled", true)) return;
        PlayerData data = SimpleAdminMode.playerHistory.get(e.unit.getPlayer().id);
        if (data == null || data.uuid.equals("Loading...")) return;
        int minJ = Core.settings.getInt("sam-ag-min-joins", 5);
        int maxK = Core.settings.getInt("sam-ag-max-kicks", 1);
        if (data.timesJoined < minJ && data.timesKicked >= maxK) {
            Seq cores = e.unit.team().cores();
            if (cores.isEmpty()) return;
            Building closestCore = (Building) cores.min(c -> ((Building) c).dst((Position) e.tile));
            float radius = Core.settings.getInt("sam-ag-" + key + "-radius", 40);
            if (e.tile.dst((Position) closestCore) < radius * 8.0f) {
                Vars.player.sendMessage(Core.bundle.format("sam.ag.buildAlert",
                    e.unit.getPlayer().name,
                    block.localizedName + " " + Fonts.getUnicodeStr(block.name)
                        + "(" + Mathf.round(e.tile.getX() / 8.0f) + " , " + Mathf.round(e.tile.getY() / 8.0f) + ")"));
            }
        }
    }
}
