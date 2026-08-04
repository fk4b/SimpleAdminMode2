package fallen;

import arc.Core;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * Hierarchical ban menu: category → sub-rules → time slider.
 * Custom reason tab at the bottom with free-form reason + time string.
 */
public class AdvancedBanDialog extends BaseDialog {
    private String currentScope = "here";
    private final String loxName;
    private final String uuid;
    private final ObjectSet<String> expanded = new ObjectSet<>();
    private Table rulesTable;
    private Table contentRoot;

    public AdvancedBanDialog(Player player, String uuid) {
        super(Core.bundle.format("sam.ban.title", Strings.stripColors(player.name)));
        this.uuid = uuid;
        this.loxName = player != null && player.name != null ? player.name : uuid;
        this.addCloseButton();

        cont.table(top -> {
            // Same ID as in player list (from /trace) — used for /ban and /rollback
            top.add("[lightgray]ID: [accent]" + uuid).padRight(20f);
            top.table(st -> {
                ButtonGroup sg = new ButtonGroup();
                st.button("HERE", Styles.togglet, () -> this.currentScope = "here")
                    .size(80f, 40f).group(sg).checked(true);
                st.button("ALL", Styles.togglet, () -> this.currentScope = "all")
                    .size(80f, 40f).group(sg);
                st.button("Attack", Styles.togglet, () -> this.currentScope = "attack")
                    .size(80f, 40f).group(sg);
                st.button("Survival", Styles.togglet, () -> this.currentScope = "survival")
                    .size(80f, 40f).group(sg);
                st.button("PvP", Styles.togglet, () -> this.currentScope = "pvp")
                    .size(80f, 40f).group(sg);
            });
        }).row();

        cont.image().color(Pal.accent).fillX().height(3f).pad(10f).row();

        cont.pane(pane -> {
            this.contentRoot = pane;
            pane.defaults().pad(2f).fillX();
            this.rulesTable = new Table();
            pane.add(rulesTable).growX().row();
            rebuildRules();
            pane.image().color(Pal.gray).fillX().height(2f).padTop(12f).padBottom(8f).row();
            buildCustomReasonSection(pane);
        }).grow().minWidth(Vars.mobile ? 300f : 520f).maxWidth(Vars.mobile ? 480f : 900f).row();
    }

    private void rebuildRules() {
        rulesTable.clear();
        rulesTable.defaults().pad(2f).fillX();

        // ── 2.x Grief ──────────────────────────────────────────────
        addSectionHeader("sam.ban.section.grief");

        addCategory("2.1", "sam.ban.rule.2_1", new Rule[]{
            rule("2.1.1", "sam.ban.rule.2_1_1", 3, 14, 7, "d"),
            rule("2.1.2", "sam.ban.rule.2_1_2", 14, 90, 30, "d"),
            rule("2.1.3", "sam.ban.rule.2_1_3", 14, 90, 30, "d"),
            rule("2.1.4", "sam.ban.rule.2_1_4", 7, 30, 14, "d"),
            rule("2.1.5", "sam.ban.rule.2_1_5", 30, 90, 30, "d"),
        });

        addCategory("2.2", "sam.ban.rule.2_2", new Rule[]{
            rule("2.2.1", "sam.ban.rule.2_2_1", 1, 14, 3, "d"),
            rule("2.2.2", "sam.ban.rule.2_2_2", 1, 7, 3, "d"),
            rule("2.2.3", "sam.ban.rule.2_2_3", 1, 7, 3, "d"),
            rule("2.2.4", "sam.ban.rule.2_2_4", 1, 14, 3, "d"),
        });

        addCategory("2.3", "sam.ban.rule.2_3", new Rule[]{
            rulePerm("2.3.1", "sam.ban.rule.2_3_1"),
            rule("2.3.2", "sam.ban.rule.2_3_2", 3, 7, 5, "d"),
        });

        // ── 3 Visual ───────────────────────────────────────────────
        addSectionHeader("sam.ban.section.visual");

        addCategory("3", "sam.ban.rule.3", new Rule[]{
            rule("3.1", "sam.ban.rule.3_1", 14, 30, 14, "d"),
            rule("3.2", "sam.ban.rule.3_2", 14, 90, 30, "d"),
            rule("3.3", "sam.ban.rule.3_3", 7, 14, 7, "d"),
        });

        // ── 4 Chat ─────────────────────────────────────────────────
        addSectionHeader("sam.ban.section.chat");

        addCategory("4", "sam.ban.rule.4", new Rule[]{
            rule("4.1", "sam.ban.rule.4_1", 1, 3, 1, "d"),
            rule("4.2", "sam.ban.rule.4_2", 2, 7, 3, "d"),
            rule("4.3", "sam.ban.rule.4_3", 3, 7, 3, "d"),
            rulePerm("4.4", "sam.ban.rule.4_4"),
            rule("4.5", "sam.ban.rule.4_5", 3, 14, 7, "d"),
            rulePerm("4.6", "sam.ban.rule.4_6"),
            rule("4.7", "sam.ban.rule.4_7", 7, 30, 14, "d"),
        });

        // ── 5 Other ────────────────────────────────────────────────
        addSectionHeader("sam.ban.section.other");

        addCategory("5.1", "sam.ban.rule.5_1", new Rule[]{
            rule("5.1.1", "sam.ban.rule.5_1_1", 2, 2, 2, "d"),
            rule("5.1.2", "sam.ban.rule.5_1_2", 2, 30, 10, "d"),
        });

        addCategory("5.2", "sam.ban.rule.5_2", new Rule[]{
            rule("5.2.1", "sam.ban.rule.5_2_1", 1, 3, 2, "d"),
            rule("5.2.2", "sam.ban.rule.5_2_2", 1, 7, 3, "d"),
            rule("5.2.3", "sam.ban.rule.5_2_3", 14, 30, 14, "d"),
        });

        // leaf categories without sub-items
        addLeafCategory("5.3", "sam.ban.rule.5_3", true, 0, 0, 0, "perm");
        addLeafCategory("5.4", "sam.ban.rule.5_4", false, 7, 30, 14, "d");
        addLeafCategory("5.5", "sam.ban.rule.5_5", false, 3, 14, 7, "d");
    }

    private void addSectionHeader(String bundleKey) {
        rulesTable.add("[accent]" + Core.bundle.get(bundleKey))
            .left().padTop(10f).padBottom(4f).row();
        rulesTable.image().color(Pal.accent).fillX().height(2f).padBottom(4f).row();
    }

    private void addCategory(String id, String titleKey, Rule[] children) {
        boolean open = expanded.contains(id);
        rulesTable.table(row -> {
            row.background(Tex.underline);
            String arrow = open ? "[accent]▼ " : "[lightgray]▶ ";
            row.button(arrow + Core.bundle.get(titleKey), Styles.flatBordert, () -> {
                if (expanded.contains(id)) expanded.remove(id);
                else expanded.add(id);
                rebuildRules();
            }).height(48f).growX().left().get().getLabel().setAlignment(arc.util.Align.left);
        }).margin(3f).growX().row();

        if (open) {
            for (Rule r : children) {
                addRuleRow(r, true);
            }
        }
    }

    private void addLeafCategory(String id, String titleKey, boolean perm, int min, int max, int def, String unit) {
        if (perm) {
            addRuleRow(rulePerm(id, titleKey), false);
        } else {
            addRuleRow(rule(id, titleKey, min, max, def, unit), false);
        }
    }

    private void addRuleRow(Rule r, boolean indented) {
        rulesTable.table(row -> {
            row.background(Tex.underline);
            float leftPad = indented ? 18f : 0f;

            if (r.perm) {
                row.button("[red]" + Core.bundle.get(r.descKey), Styles.flatBordert, () ->
                    executeBan("perm", r.ruleId)
                ).height(52f).growX().padLeft(leftPad).get().getLabel().setWrap(true);
                return;
            }

            int[] currentVal = new int[]{r.def};
            TextButton btn = row.button("[red]" + Core.bundle.get(r.descKey), Styles.flatBordert, () -> {
                String finalTime = currentVal[0] > r.max ? "perm" : currentVal[0] + r.unit;
                executeBan(finalTime, r.ruleId);
            }).height(52f).growX().padLeft(leftPad).get();
            btn.getLabel().setWrap(true);

            row.table(s -> {
                Label l = s.add("").width(64f).get();
                Runnable updateLabel = () ->
                    l.setText(currentVal[0] > r.max ? "[accent]perm" : "[accent]" + currentVal[0] + r.unit);
                updateLabel.run();
                s.slider((float) r.min, (float) (r.max + 1), 1f, (float) currentVal[0], v -> {
                    currentVal[0] = (int) v;
                    updateLabel.run();
                }).width(110f);
            }).padLeft(8f).padRight(4f);
        }).margin(4f).growX().row();
    }

    private void buildCustomReasonSection(Table pane) {
        pane.add("[accent]" + Core.bundle.get("sam.ban.custom.title"))
            .left().padTop(4f).padBottom(6f).row();
        pane.add("[gray]" + Core.bundle.get("sam.ban.custom.hint"))
            .left().wrap().growX().padBottom(6f).row();

        TextField[] reasonField = new TextField[1];
        TextField[] timeField = new TextField[1];

        pane.table(form -> {
            form.defaults().pad(3f);
            form.add(Core.bundle.get("sam.ban.custom.reason")).left().padRight(8f);
            reasonField[0] = form.field("", t -> {}).growX().height(40f).get();
            reasonField[0].setMessageText(Core.bundle.get("sam.ban.custom.reason.hint"));
            form.row();
            form.add(Core.bundle.get("sam.ban.custom.time")).left().padRight(8f);
            timeField[0] = form.field("1d", t -> {}).width(120f).height(40f).get();
            timeField[0].setMessageText("15, 30m, 12h, 3d, 2w, 1y");
            form.add("[lightgray]" + Core.bundle.get("sam.ban.custom.time.help"))
                .left().padLeft(8f).growX();
            form.row();
            form.button(Core.bundle.get("sam.ban.custom.apply"), Icon.ok, Styles.flatt, () -> {
                String reason = reasonField[0].getText();
                String time = timeField[0].getText();
                if (reason == null || reason.trim().isEmpty()) {
                    Vars.ui.showInfoFade(Core.bundle.get("sam.ban.custom.empty"));
                    return;
                }
                if (time == null || time.trim().isEmpty()) {
                    time = "1d";
                }
                time = time.trim();
                if (!isValidTime(time)) {
                    Vars.ui.showInfoFade(Core.bundle.get("sam.ban.custom.badtime"));
                    return;
                }
                executeBan(time, reason.trim());
            }).height(48f).growX().colspan(3).padTop(8f);
        }).growX().pad(4f).row();
    }

    /**
     * Valid: perm | bare number (minutes) | number + s/h/d/w/m/y
     * m = months (not minutes — bare number is minutes).
     */
    static boolean isValidTime(String time) {
        if (time == null) return false;
        time = time.trim().toLowerCase();
        if (time.equals("perm") || time.equals("permanent") || time.equals("0")) return true;
        return time.matches("\\d+") || time.matches("\\d+[shdwmy]");
    }

    private void executeBan(String time, String reason) {
        String cmd = Strings.format("/ban @ @ @ @", uuid, time, currentScope, reason);
        Call.sendChatMessage(cmd);
        Vars.player.sendMessage("[gray][Sent]: [white]" + cmd);
        BanKickMessages.ban(loxName);

        // Entity id only for local evidence matching (builds/breaks/chat).
        int pid = resolveEntityId();

        // Write last-minute evidence (builds / breaks / chat) into one shared log file
        try {
            BanEvidenceLogger.writeOnBan(pid, loxName, uuid, time, reason);
        } catch (Throwable ignored) {}

        // /rollback must use the same ID the mod shows (trace ID / short ID),
        // NOT Mindustry entity player.id — servers expect the displayed ID.
        scheduleRollback(uuid);

        hide();
    }

    /**
     * Local entity id for evidence log matching only.
     * Prefer history entry with matching displayed ID; fall back to live player.
     */
    private int resolveEntityId() {
        if (uuid != null) {
            for (PlayerData pd : SimpleAdminMode.playerHistory.values()) {
                if (pd != null && uuid.equals(pd.uuid) && pd.id > 0) {
                    return pd.id;
                }
            }
            try {
                for (Player p : mindustry.gen.Groups.player) {
                    if (p == null) continue;
                    // Match by displayed/trace id first, then by connection uuid as fallback
                    PlayerData pd = SimpleAdminMode.playerHistory.get(p.id);
                    if (pd != null && uuid.equals(pd.uuid)) {
                        return p.id;
                    }
                    if (p.uuid() != null && uuid.equals(p.uuid())) {
                        return p.id;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    /**
     * If enabled, after 2 seconds send {@code /rollback <id>} where {@code id} is the
     * ID shown in the admin list (from /trace), not the internal entity id.
     */
    private void scheduleRollback(String banId) {
        if (!Core.settings.getBool("sam-ban-rollback", true)) return;
        if (banId == null || banId.isEmpty()
            || banId.equals("Loading...") || banId.equals("none") || banId.equals("admin?")) {
            Vars.ui.showInfoFade(Core.bundle.get("sam.settings.rollback.noId"));
            return;
        }
        final String id = banId;
        Timer.schedule(() -> {
            try {
                String rcmd = "/rollback " + id;
                Call.sendChatMessage(rcmd);
                if (Vars.player != null) {
                    Vars.player.sendMessage("[gray][Sent]: [white]" + rcmd);
                }
            } catch (Throwable ignored) {}
        }, 2f);
    }

    // ── helpers ────────────────────────────────────────────────────

    private static Rule rule(String id, String descKey, int min, int max, int def, String unit) {
        Rule r = new Rule();
        r.ruleId = id;
        r.descKey = descKey;
        r.min = min;
        r.max = max;
        r.def = def;
        r.unit = unit;
        r.perm = false;
        return r;
    }

    private static Rule rulePerm(String id, String descKey) {
        Rule r = new Rule();
        r.ruleId = id;
        r.descKey = descKey;
        r.perm = true;
        return r;
    }

    private static class Rule {
        String ruleId;
        String descKey;
        int min, max, def;
        String unit;
        boolean perm;
    }
}
