package fallen;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.world.Block;

public class HistoryRender {
    public static String targetNick = null;
    /** Stable filter by player id (-1 = none). Survives freeze-icon nick changes. */
    public static int targetPlayerId = -1;
    private static float brokenFade = 0.0f;
    static float alphaMult = 0.5f;
    private static float nickStartTime = 0.0f;
    private static float timerAlpha = 1.0f;
    private static boolean inited = false;

    public static void init() {
        if (inited) return;
        inited = true;
        // Avoid static Core.settings access before game ready
        try {
            alphaMult = (float) Core.settings.getInt("fadedblockallplayers", 5) / 10.0f;
        } catch (Throwable ignored) {
            alphaMult = 0.5f;
        }

        // Pad Trigger enum array so FOO/arc Events.fire never AIOOBE on high ordinals
        SimpleAdminMode.ensureTriggerListenersSafe();

        Events.run(EventType.Trigger.draw, () -> {
            try {
                if (!Vars.state.isGame()) return;
                drawActionHistory();
            } catch (Throwable t) {
                // never crash the draw loop
            }
        });
    }

    public static void setTarget(String nick) {
        setTarget(nick, -1);
    }

    public static void setTarget(String nick, int playerId) {
        String norm = NameUtil.normalize(nick);
        boolean same = (playerId > 0 && playerId == targetPlayerId)
            || (norm != null && norm.equals(targetNick) && playerId <= 0);
        if (same && targetNick != null) {
            targetNick = null;
            targetPlayerId = -1;
            return;
        }
        targetNick = norm;
        targetPlayerId = playerId;
        nickStartTime = Time.globalTime;
        timerAlpha = 1.0f;
        Vars.ui.hudfrag.showToast("[#ffaa55]Просмотр истории:\n[white]" + (norm != null ? norm : "?"));
    }

    public static void setTarget(PlayerData data) {
        if (data == null) {
            targetNick = null;
            targetPlayerId = -1;
            return;
        }
        setTarget(data.name, data.id);
    }

    private static void drawActionHistory() {
        brokenFade = Mathf.lerpDelta(brokenFade, 1.0f, 0.1f);
        if (targetNick == null && targetPlayerId < 0) {
            return;
        }
        float elapsed = (Time.globalTime - nickStartTime) / 60.0f;
        if (elapsed > 10.0f) {
            targetNick = null;
            targetPlayerId = -1;
            timerAlpha = 0.0f;
            return;
        } else {
            timerAlpha = elapsed > 7.0f ? (10.0f - elapsed) / 3.0f : 1.0f;
        }
        drawBlocks();
        drawConfigs();
    }

    private static boolean matches(int planPlayerId, String planNick) {
        if (targetPlayerId > 0 && planPlayerId > 0 && planPlayerId == targetPlayerId) {
            return true;
        }
        if (targetNick == null || targetNick.isEmpty()) {
            return targetPlayerId > 0 && planPlayerId == targetPlayerId;
        }
        return NameUtil.samePlayer(planNick, targetNick);
    }

    private static void drawBlocks() {
        for (ActionsHistory.BlockPlayerPlan plan : ActionsHistory.blocksplayersplans) {
            if (plan.lastacs == null && plan.playerId <= 0) continue;
            if (!matches(plan.playerId, plan.lastacs)) continue;
            Block b = Vars.content.block(plan.block);
            if (b == null) continue;
            float px = (float) (plan.x * 8) + b.offset;
            float py = (float) (plan.y * 8) + b.offset;
            if (!Core.camera.bounds(Tmp.r1).grow(16.0f).contains(px, py)) continue;
            Draw.z(120.0f);
            Draw.alpha(0.5f * brokenFade * alphaMult * timerAlpha);
            Color mix = plan.wasbreaking ? Color.red : Color.green;
            Draw.mixcol(mix, 0.4f + Mathf.absin(Time.globalTime, 6.0f, 0.2f));
            Draw.rect(b.fullIcon, px, py, b.rotate ? (float) (plan.rotation * 90) : 0.0f);
            Draw.reset();
        }
    }

    private static void drawConfigs() {
        for (ActionsHistory.BlockConfigPlayerPlan plan : ActionsHistory.blockconfplayersplans) {
            if (plan.lastacs == null && plan.playerId <= 0) continue;
            if (!matches(plan.playerId, plan.lastacs)) continue;
            Block b = Vars.content.block(plan.block);
            if (b == null) continue;
            float px = (float) (plan.x * 8) + b.offset;
            float py = (float) (plan.y * 8) + b.offset;
            if (!Core.camera.bounds(Tmp.r1).grow(16.0f).contains(px, py)) continue;
            Draw.z(120.0f);
            Draw.alpha(0.45f * brokenFade * alphaMult * timerAlpha);
            Draw.mixcol(Color.blue, 0.5f + Mathf.absin(Time.globalTime, 6.0f, 0.2f));
            Draw.rect(b.fullIcon, px, py);
            Draw.reset();
        }
    }
}
