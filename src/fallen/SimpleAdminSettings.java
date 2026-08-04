/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  arc.Core
 *  arc.graphics.Color
 *  arc.scene.Element
 *  arc.scene.style.Drawable
 *  arc.scene.ui.Label
 *  arc.scene.ui.TextField
 *  arc.scene.ui.layout.Table
 *  arc.util.Strings
 *  mindustry.Vars
 *  mindustry.content.Blocks
 *  mindustry.gen.Icon
 *  mindustry.graphics.Pal
 *  mindustry.ui.Fonts
 *  mindustry.ui.Styles
 *  mindustry.ui.dialogs.BaseDialog
 *  mindustry.world.Block
 */
package fallen;

import arc.Core;
import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.scene.ui.Label;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;

public class SimpleAdminSettings
extends BaseDialog {
    public SimpleAdminSettings() {
        super(Core.bundle.get("sam.settings.title"));
        this.addCloseButton();
        this.setup();
    }

    private void setup() {
        this.cont.clear();
        this.cont.table(Styles.black6, main -> {
            main.margin(20.0f);
            main.pane(table -> {
                table.left().defaults().left().pad(4.0f);
                this.header((Table)table, "sam.settings.interface");
                this.addSlider((Table)table, "sam.settings.btnSize", "sam-btn-size", 30, 80, 1, 40);
                this.addSlider((Table)table, "sam.settings.hudY", "sam-hud-y", -600, 600, 10, 60);
                this.addSlider((Table)table, "sam.settings.listW", "sam-list-w", 200, 1000, 10, 400);
                table.check(Core.bundle.get("sam.settings.closeOutside"), Core.settings.getBool("sam-close-outside", true), val -> Core.settings.put("sam-close-outside", (Object)val)).row();
                table.check(Core.bundle.get("sam.settings.closeListOnInfo"), Core.settings.getBool("sam-close-list", false), val -> Core.settings.put("sam-close-list", (Object)val)).row();
                table.check(Core.bundle.get("sam.settings.closeListOutside"), Core.settings.getBool("sam-close-listoutside", false), val -> Core.settings.put("sam-close-listoutside", (Object)val)).row();
                this.header((Table)table, "sam.settings.functions");
                table.check(Core.bundle.get("sam.settings.stats"), Core.settings.getBool("sam-show-stats", false), val -> Core.settings.put("sam-show-stats", (Object)val)).row();
                table.check(Core.bundle.get("sam.settings.save"), Core.settings.getBool("sam-log-save", false), val -> Core.settings.put("sam-log-save", (Object)val)).row();
                table.check(Core.bundle.get("sam.settings.vanish"), Core.settings.getBool("sam-vanish", false), val -> Core.settings.put("sam-vanish", (Object)val)).row();
                table.check(Core.bundle.get("sam.settings.fastlang"), Core.settings.getBool("sam-fastlang", false), val -> Core.settings.put("sam-fastlang", (Object)val)).row();
                table.check(Core.bundle.get("sam.settings.freecam"), Core.settings.getBool("sam-freecam", false), val -> Core.settings.put("sam-freecam", (Object)val)).row();
                table.check(Core.bundle.get("sam.settings.rollback"), Core.settings.getBool("sam-ban-rollback", true), val -> Core.settings.put("sam-ban-rollback", (Object)val)).row();
                table.add(Core.bundle.get("sam.settings.rollback.desc")).wrap().growX().left().padBottom(4f).padLeft(8f).row();

                this.header((Table)table, "sam.settings.evidence");
                table.check(Core.bundle.get("sam.settings.evidence.enabled"), BanEvidenceLogger.enabled(), BanEvidenceLogger::setEnabled).row();
                table.add(Core.bundle.get("sam.settings.evidence.desc")).wrap().growX().left().padBottom(4f).row();
                table.table(pathRow -> {
                    pathRow.left();
                    TextField pathField = pathRow.field(
                        Core.settings.getString("sam-evidence-path", ""),
                        text -> BanEvidenceLogger.setConfiguredPath(text)
                    ).growX().height(40f).get();
                    pathField.setMessageText(BanEvidenceLogger.defaultFile().absolutePath());
                    pathRow.button(Core.bundle.get("sam.settings.evidence.default"), () -> {
                        BanEvidenceLogger.setConfiguredPath("");
                        pathField.setText("");
                        Vars.ui.showInfoFade(Core.bundle.get("sam.settings.evidence.reset"));
                    }).width(100f).height(40f).padLeft(6f);
                }).growX().padBottom(4f).row();
                table.add(new Label(() -> "[gray]" + Core.bundle.get("sam.settings.evidence.current") + " [white]" + BanEvidenceLogger.getConfiguredPath()))
                    .wrap().growX().left().padBottom(8f).row();

                this.header((Table)table, "sam.settings.bankick");
                table.check(Core.bundle.get("sam.bankick.enabled"), BanKickMessages.enabled(), BanKickMessages::setEnabled).row();
                table.button(Core.bundle.get("sam.bankick.openEditor"), (Drawable)Icon.edit, () -> {
                    new BanKickMessageEditor().show();
                }).width(320.0f).height(50.0f).padTop(6.0f).padBottom(8.0f).row();
                this.header((Table)table, "sam.settings.antiGrief");
                table.check(Core.bundle.get("sam.settings.agEnabled"), Core.settings.getBool("sam-ag-enabled", false), val -> Core.settings.put("sam-ag-enabled", (Object)val)).row();
                table.check(Core.bundle.get("sam.settings.agAFreeze"), Core.settings.getBool("sam-ag-afr", false), val -> Core.settings.put("sam-ag-afr", (Object)val)).row();
                this.addIntInput((Table)table, "sam.settings.agMinBuild", "sam-ag-min-build", 10);
                this.addIntInput((Table)table, "sam.settings.agMaxBreak", "sam-ag-max-break", 100);
                this.addIntInput((Table)table, "sam.settings.agMinJoins", "sam-ag-min-joins", 5);
                this.addIntInput((Table)table, "sam.settings.agMaxKicks", "sam-ag-max-kicks", 1);
                table.check(Core.bundle.get("sam.settings.agBuildWarn"), Core.settings.getBool("sam-ag-build-warn", true), val -> Core.settings.put("sam-ag-build-warn", (Object)val)).row();
                table.add((CharSequence)Core.bundle.get("sam.settings.agBlocks")).padTop(10.0f).color(Pal.accent).row();
                this.addBlockAgSetting((Table)table, Blocks.thoriumReactor, "thorium");
                this.addBlockAgSetting((Table)table, Blocks.incinerator, "incinerator");
                this.addBlockAgSetting((Table)table, Blocks.melter, "melter");
                table.button(Core.bundle.get("sam.settings.resetSettings"), (Drawable)Icon.refresh, () -> {
                    Core.settings.put("sam-btn-size", (Object)40);
                    Core.settings.put("sam-hud-y", (Object)60);
                    Core.settings.put("sam-list-w", (Object)400);
                    this.setup();
                    Vars.ui.showInfoFade(Core.bundle.get("sam.settings.resetDone"));
                }).width(240.0f).height(50.0f).padTop(20.0f);
                table.row();
                this.header((Table)table, "sam.settings.iHateAttems");
                table.check(Core.bundle.get("sam.settings.iHateAttems"), Core.settings.getBool("sam-aa", false), val -> Core.settings.put("sam-aa", (Object)val)).row();
                table.check(Core.bundle.get("sam.settings.onAttemAlarms"), Core.settings.getBool("sam-oaa", false), val -> Core.settings.put("sam-oaa", (Object)val)).row();
            }).grow();
        }).width(Vars.mobile ? Math.min(520f, (Core.scene != null ? Core.scene.getWidth() : 400f) - 20f) : 650.0f)
            .fillY().center();
    }

    private void header(Table table, String bundleKey) {
        table.add((CharSequence)Core.bundle.get(bundleKey)).left().color(Pal.accent).padTop(20.0f).row();
        table.image().color(Pal.accent).fillX().height(2.0f).padBottom(10.0f).row();
    }

    private void addSlider(Table t, String bundleKey, String key, int min, int max, int step, int def) {
        t.table(s -> {
            s.left().defaults().left();
            s.table(labels -> {
                labels.left();
                labels.add((CharSequence)(Core.bundle.get(bundleKey) + ": ")).color(Color.lightGray);
                labels.add((Element)new Label(() -> String.valueOf(Core.settings.getInt(key, def)))).color(Pal.accent);
            }).left().row();
            s.slider((float)min, (float)max, (float)step, (float)Core.settings.getInt(key, def), val -> Core.settings.put(key, (Object)((int)val))).width(450.0f).height(40.0f).padTop(2.0f);
        }).padTop(4.0f).padBottom(4.0f).left().row();
    }

    private void addBlockAgSetting(Table t, Block block, String key) {
        t.table(s -> {
            s.left().defaults().left();
            s.check(block.localizedName + " " + Fonts.getUnicodeStr((String)block.name), Core.settings.getBool("sam-ag-" + key + "-enabled", true), val -> Core.settings.put("sam-ag-" + key + "-enabled", (Object)val)).padBottom(2.0f).row();
            Table sub = new Table();
            sub.left();
            this.addSlider(sub, "sam.settings.agBlockRadius", "sam-ag-" + key + "-radius", 0, 100, 1, 10);
            s.add((Element)sub).padLeft(20.0f);
        }).left().padBottom(10.0f).row();
    }

    private void addIntInput(Table t, String bundleKey, String key, int def) {
        t.table(i -> {
            i.left();
            i.add((CharSequence)(Core.bundle.get(bundleKey) + ": ")).color(Color.lightGray).width(230.0f);
            ((TextField)i.field(String.valueOf(Core.settings.getInt(key, def)), text -> {
                if (Strings.canParseInt((String)text)) {
                    Core.settings.put(key, (Object)Strings.parseInt((String)text));
                }
            }).width(100.0f).get()).setMessageText(String.valueOf(def));
        }).left().padBottom(4.0f).row();
    }
}
