/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  arc.Core
 *  arc.graphics.Color
 *  arc.input.KeyCode
 *  arc.scene.Element
 *  arc.scene.Group
 *  arc.scene.event.EventListener
 *  arc.scene.event.InputEvent
 *  arc.scene.event.InputListener
 *  arc.scene.event.Touchable
 *  arc.scene.style.Drawable
 *  arc.scene.ui.Button$ButtonStyle
 *  arc.scene.ui.Image
 *  arc.scene.ui.ImageButton
 *  arc.scene.ui.Label
 *  arc.scene.ui.TextButton$TextButtonStyle
 *  arc.scene.ui.TextField
 *  arc.scene.ui.layout.Table
 *  arc.struct.Seq
 *  arc.util.Interval
 *  arc.util.Scaling
 *  arc.util.Strings
 *  mindustry.Vars
 *  mindustry.gen.Call
 *  mindustry.gen.Groups
 *  mindustry.gen.Icon
 *  mindustry.gen.Player
 *  mindustry.gen.Tex
 *  mindustry.graphics.Pal
 *  mindustry.net.Packets$AdminAction
 *  mindustry.ui.Styles
 *  mindustry.ui.dialogs.BaseDialog
 */
package fallen;

import arc.Core;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.EventListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Scaling;
import arc.util.Strings;
import fallen.AdvancedBanDialog;
import fallen.HistoryRender;
import fallen.PlayerData;
import fallen.SimpleAdminMode;
import fallen.SimpleAdminSettings;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.net.Packets;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

public class SimpleAdminList {
    public Table content = new Table().marginRight(13.0f).marginLeft(13.0f);
    private Table mainTable;
    private Table infoPanel;
    private boolean visible = false;
    private Interval timer = new Interval();
    private TextField search;
    private String manualUuid = "";
    private float button_size = 40.0f;
    private float panelWidth = 400.0f;

    public void build(Group parent) {
        this.content.name = "players";
        parent.fill(cont -> {
            cont.name = "playerlist";
            cont.visible(() -> this.visible);
            cont.touchable = Touchable.enabled;
            cont.clicked(() -> {
                if (Core.settings.getBool("sam-close-listoutside", false)) {
                    this.toggle();
                }
            });
            cont.update(() -> {
                if (!Vars.net.active() || !Vars.state.isGame()) {
                    this.visible = false;
                    return;
                }
                if (this.visible && this.timer.get(180.0f)) {
                    this.rebuild();
                    this.content.pack();
                    this.content.act(Core.graphics.getDeltaTime());
                    Core.app.post(() -> Core.scene.act(Math.min(Core.graphics.getDeltaTime(), 0.033f)));
                }
            });
            this.mainTable = (Table)cont.table(Tex.buttonTrans, pane -> {
                pane.touchable = Touchable.enabled;
                pane.addListener((EventListener)new InputListener(){

                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                        event.stop();
                        return true;
                    }
                });
                pane.label(() -> Core.bundle.format(SimpleAdminMode.playerHistory.size == 1 ? "players.single" : "players", new Object[]{SimpleAdminMode.playerHistory.size}));
                pane.row();
                this.search = (TextField)pane.field(null, text -> this.rebuild()).grow().pad(8.0f).name("search").maxTextLength(40).get();
                this.search.setMessageText(Core.bundle.get("players.search"));
                pane.row();
                pane.pane((Element)this.content).grow().scrollX(false);
                pane.row();
                pane.table(menu -> {
                    menu.defaults().growX().height(50.0f).fillY();
                    menu.name = "menu";
                    menu.table(manual -> {
                        manual.background(Styles.black3).margin(4.0f);
                        TextField field = (TextField)manual.field("", text -> {
                            this.manualUuid = text;
                        }).growX().height(45.0f).get();
                        field.setMessageText(Core.bundle.get("sam.list.manualUUID"));
                        manual.button((Drawable)Icon.waves, Styles.clearNonei, () -> Call.sendChatMessage((String)("/freeze " + this.manualUuid))).size(45.0f).padLeft(8.0f).tooltip(Core.bundle.get("sam.list.freeze"));
                        manual.button((Drawable)Icon.hammer, Styles.clearNonei, () -> {
                            if (this.manualUuid.isEmpty()) {
                                Vars.ui.showInfoFade(Core.bundle.get("sam.info.smallUUID"));
                                return;
                            }
                            Player fake = Player.create();
                            fake.name = "[gray]Manual Entry[]";
                            new AdvancedBanDialog(fake, this.manualUuid).show();
                        }).size(45.0f).padLeft(8.0f).tooltip(Core.bundle.get("sam.list.ban"));
                    }).padTop(10.0f).row();
                    menu.table(buttons -> {
                        buttons.defaults().height(50.0f).fillY();
                        buttons.button("@close", this::toggle).growX();
                        buttons.button((Drawable)Icon.settings, Styles.defaulti, () -> new SimpleAdminSettings().show()).width(50.0f).padLeft(4.0f);
                    }).growX().padLeft(4.0f);
                }).margin(0.0f).pad(10.0f).growX();
            }).touchable(Touchable.enabled).margin(14.0f).minWidth(this.panelWidth).get();
        });
        this.rebuild();
    }

    public void rebuild() {
        float h = 50.0f;
        int defaultBtn = Vars.mobile ? 48 : 40;
        int defaultW = Vars.mobile ? 360 : 400;
        this.button_size = Core.settings.getInt("sam-btn-size", defaultBtn);
        this.panelWidth = Core.settings.getInt("sam-list-w", defaultW);
        // Cap panel so it fits phone screens
        if (Vars.mobile && Core.scene != null) {
            float maxW = Core.scene.getWidth() - 24f;
            if (this.panelWidth > maxW) this.panelWidth = Math.max(260f, maxW);
        }
        if (this.mainTable != null) {
            this.mainTable.setWidth(this.panelWidth);
            this.mainTable.invalidate();
        }
        float buttonWidth = this.panelWidth - 60.0f;
        boolean found = false;
        Seq<PlayerData> filtered = Seq.with(SimpleAdminMode.playerHistory.values());
        if (this.search.getText().length() > 0) {
            String query = this.search.getText().toLowerCase();
            filtered = filtered.copy().retainAll(d -> Strings.stripColors((CharSequence)d.name.toLowerCase()).contains(query));
        }
        filtered.sort((PlayerData a, PlayerData b) -> {
            boolean bAdmin;
            if (a.online != b.online) {
                return a.online ? -1 : 1;
            }
            boolean aAdmin = a.player != null && a.player.admin;
            boolean bl = bAdmin = b.player != null && b.player.admin;
            if (aAdmin != bAdmin) {
                return aAdmin ? -1 : 1;
            }
            return Strings.stripColors((CharSequence)a.name).compareToIgnoreCase(Strings.stripColors((CharSequence)b.name));
        });
        this.content.clear();
        boolean lastWasOnline = true;
        for (PlayerData user : filtered) {
            found = true;
            if (lastWasOnline && !user.online) {
                this.content.add().height(8.0f).row();
                this.content.add((CharSequence)Core.bundle.get("sam.list.offline")).color(Pal.redLight).center().row();
                this.content.add().height(4.0f).row();
            }
            lastWasOnline = user.online;
            // Fixed action-button column so icons never "drift" when stats/lang wrap.
            final float btn = this.button_size;
            Table button = new Table();
            button.left();
            button.margin(4.0f).marginBottom(6.0f);
            button.background(Tex.underline);
            button.touchable = Touchable.enabled;
            button.clicked(() -> {});

            // Left: name + info rows. Right: fixed 2x2 action grid.
            button.table(body -> {
                body.left().top();

                // ── Name row ──────────────────────────────────────
                body.table(nameTable -> {
                    nameTable.left().defaults().pad(2.0f);
                    boolean hasUnit = user.player != null && user.player.unit() != null && user.player.unit().type != null;
                    if (hasUnit) {
                        ((ImageButton) nameTable.button(Styles.cleart.disabled, () ->
                            Vars.control.input.spectate(user.player.unit())
                        ).width(50.0f).height(35.0f).get())
                            .image(user.player.unit().icon()).size(20.0f).scaling(Scaling.fit);
                    } else {
                        nameTable.button((Drawable) Icon.cancelSmall, Styles.cleari, () -> {})
                            .width(50.0f).height(35.0f);
                    }
                    // freeze indicator next to nick
                    if (user.online && user.player != null && AntiAttemPatcher.isPlayerFrozen(user.player)) {
                        nameTable.add("[cyan]❄").padRight(4f);
                    }
                    nameTable.add((CharSequence) user.name).left().growX().wrap().minWidth(40f);
                }).growX().left();

                body.row();

                // ── Info row (UUID / lang / stats) — no action buttons here ──
                body.table(infoTable -> {
                    infoTable.left().defaults().height(28.0f).pad(1.0f);
                    // Shown value is server trace ID (often short ID, not raw UUID)
                    String uuidText = user.uuid.equals("admin?") ? "[green]admin"
                        : (user.uuid.equals("Loading...") ? "[gray]waiting..."
                        : (user.uuid.equals("none") ? "[gray]none" : user.uuid));
                    infoTable.add((CharSequence) ("[accent]ID: [white]" + uuidText)).growX().left().wrap();
                    if (Core.settings.getBool("sam-fastlang", false)) {
                        infoTable.add((CharSequence) ("[accent] L: [white]" + user.locale)).right().padLeft(4f);
                    }
                    if (Core.settings.getBool("sam-show-stats", false)) {
                        infoTable.button(st -> {
                            st.defaults().padLeft(2.0f).padRight(2.0f).fontScale(0.8f);
                            st.add((Element) new Label(() ->
                                "[green]" + user.builds + "[]| [red]" + user.breaks + "[]| [sky]" + user.configs
                            )).minWidth(60.0f);
                        }, (Button.ButtonStyle) Styles.flatBordert, () -> HistoryRender.setTarget(user))
                            .right().height(24.0f).padLeft(4.0f);
                    }
                }).growX().left();
            }).growX().left().top();

            // ── Fixed action column (always same size / position) ──
            button.table(actions -> {
                actions.top().defaults().size(btn).pad(1.5f);
                // row 1: info + menu
                actions.button((Drawable) Icon.info, Styles.cleari, () -> {
                    this.showInfoPanel(user);
                    if (Core.settings.getBool("sam-close-list")) {
                        this.toggle();
                    }
                }).tooltip(Core.bundle.get("sam.list.showSaveData"));
                if (user.online) {
                    actions.button((Drawable) Icon.menu, Styles.cleari, () -> this.showPlayerMenu(user))
                        .tooltip(Core.bundle.get("sam.list.admActions"));
                } else {
                    actions.add().size(btn); // placeholder keeps grid aligned
                }
                actions.row();
                // row 2: freeze + ban
                if (user.online) {
                    actions.button((Drawable) Icon.wavesSmall, Styles.cleari, () -> {
                        if (!user.uuid.equals("Loading...") && !user.uuid.equals("none") && !user.uuid.equals("admin?")) {
                            Call.sendChatMessage("/freeze " + user.uuid);
                        } else {
                            Vars.ui.showInfoFade("[red]ID ещё не получен");
                        }
                    }).tooltip(Core.bundle.get("sam.list.freeze"));
                } else {
                    actions.add().size(btn);
                }
                actions.button((Drawable) Icon.hammer, Styles.cleari, () -> {
                    if (!user.uuid.equals("Loading...") && !user.uuid.equals("none")) {
                        Player p = Groups.player.getByID(user.id);
                        if (p == null) {
                            p = Player.create();
                        }
                        p.name = user.name;
                        new AdvancedBanDialog(p, user.uuid).show();
                    } else {
                        Vars.ui.showInfoFade("[red]ID ещё не получен");
                    }
                }).tooltip(Core.bundle.get("sam.list.ban"));
            }).top().right().padLeft(6f);

            this.content.add((Element) button).width(buttonWidth).padBottom(4.0f);
            this.content.row();
        }
        if (!found) {
            this.content.add((CharSequence)Core.bundle.format("players.notfound", new Object[0])).padBottom(6.0f).width(350.0f).maxHeight(h + 14.0f);
        }
        this.content.marginBottom(5.0f);
    }

    private void showPlayerMenu(PlayerData user) {
        BaseDialog dialog = new BaseDialog(user.name);
        dialog.title.setColor(Color.white);
        dialog.titleTable.remove();
        dialog.closeOnBack();
        TextButton.TextButtonStyle bstyle = Styles.defaultt;
        dialog.cont.add((CharSequence)user.name).left().row();
        dialog.cont.image(Tex.whiteui, Pal.accent).fillX().height(3.0f).pad(4.0f).row();
        dialog.cont.pane(t -> {
            t.defaults().size(220.0f, 55.0f).pad(3.0f);
            t.button("@player.ban", (Drawable)Icon.hammer, bstyle, () -> {
                Vars.ui.showConfirm("@confirm", Core.bundle.format("confirmban", new Object[]{user.name}), () -> {
                    Call.adminRequest((Player)user.player, (Packets.AdminAction)Packets.AdminAction.ban, null);
                    BanKickMessages.ban(user.name);
                });
                dialog.hide();
            }).row();
            t.button("@player.kick", (Drawable)Icon.cancel, bstyle, () -> {
                Vars.ui.showConfirm("@confirm", Core.bundle.format("confirmkick", new Object[]{user.name}), () -> {
                    Call.adminRequest((Player)user.player, (Packets.AdminAction)Packets.AdminAction.kick, null);
                    BanKickMessages.kick(user.name);
                });
                dialog.hide();
            }).row();
            t.button("@player.trace", (Drawable)Icon.zoom, bstyle, () -> {
                Call.adminRequest((Player)user.player, (Packets.AdminAction)Packets.AdminAction.trace, null);
                dialog.hide();
            }).row();
        }).row();
        dialog.cont.button("@back", (Drawable)Icon.left, () -> ((BaseDialog)dialog).hide()).padTop(-1.0f).size(220.0f, 55.0f);
        dialog.show();
    }

    public void toggle() {
        boolean bl = this.visible = !this.visible;
        if (this.visible) {
            this.rebuild();
        } else {
            Core.scene.setKeyboardFocus(null);
            this.search.clearText();
        }
    }

    private void showInfoPanel(PlayerData data) {
        if (this.infoPanel != null) {
            this.infoPanel.remove();
        }
        this.infoPanel = new Table();
        this.infoPanel.setFillParent(true);
        this.infoPanel.touchable = Touchable.enabled;
        this.infoPanel.clicked(() -> {
            if (Core.settings.getBool("sam-close-outside", true)) {
                this.infoPanel.remove();
                this.infoPanel = null;
            }
        });
        this.infoPanel.table(Tex.buttonTrans, t -> {
            t.touchable = Touchable.enabled;
            t.clicked(() -> {});
            t.addListener((EventListener)new InputListener(){

                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    event.stop();
                    return true;
                }
            });
            t.margin(12.0f).defaults().left();
            t.table(h -> {
                h.add((Element)new Image((Drawable)Icon.infoSmall)).padRight(8.0f);
                h.add((CharSequence)Core.bundle.format("sam.info.title", new Object[]{data.name})).growX();
                h.button((Drawable)Icon.leftOpen, Styles.cleari, () -> {
                    this.infoPanel.remove();
                    this.infoPanel = null;
                    this.toggle();
                }).size(32.0f);
                h.button((Drawable)Icon.cancel, Styles.cleari, () -> {
                    this.infoPanel.remove();
                    this.infoPanel = null;
                }).size(32.0f);
            }).growX().row();
            t.image().color(Pal.accent).fillX().height(2.0f).padTop(4.0f).padBottom(8.0f).row();
            t.pane(p -> {
                p.defaults().left().growX().margin(2.0f);
                this.addCopyRow((Table)p, Core.bundle.get("sam.info.name"), data.name);
                this.addCopyRow((Table)p, Core.bundle.get("sam.info.uuid"), data.uuid);
                this.addCopyRow((Table)p, Core.bundle.get("sam.info.ip"), data.ip);
                this.addCopyRow((Table)p, Core.bundle.get("sam.info.lang"), data.locale);
                this.addCopyRow((Table)p, Core.bundle.get("sam.info.joins"), String.valueOf(data.timesJoined));
                this.addCopyRow((Table)p, Core.bundle.get("sam.info.kicks"), String.valueOf(data.timesKicked));
                this.addCopyRow((Table)p, Core.bundle.get("sam.info.mobile"), data.mobile ? Core.bundle.get("sam.info.yes") : Core.bundle.get("sam.info.no"));
                this.addCopyRow((Table)p, Core.bundle.get("sam.info.modded"), data.modded ? Core.bundle.get("sam.info.yes") : Core.bundle.get("sam.info.no"));
                if (data.names.length > 1) {
                    p.add((CharSequence)Core.bundle.get("sam.info.history.name")).padTop(8.0f).row();
                    for (String s : data.names) {
                        this.addCopyRow((Table)p, "", s);
                    }
                }
                if (data.ips.length > 1) {
                    p.add((CharSequence)Core.bundle.get("sam.info.history.ip")).padTop(8.0f).row();
                    for (String s : data.ips) {
                        this.addCopyRow((Table)p, "", s);
                    }
                }
            }).size(340.0f, 300.0f).row();
            t.button(Core.bundle.get("sam.info.stats"), (Drawable)Icon.refresh, () -> {
                Call.sendChatMessage((String)("/stats " + data.uuid));
                this.infoPanel.remove();
            }).margin(10.0f).growX().height(45.0f).padTop(10.0f).row();
            if (data.online) {
                t.button(Core.bundle.get("sam.info.update"), (Drawable)Icon.refresh, () -> {
                    Player p = (Player)Groups.player.getByID(data.id);
                    if (p != null) {
                        Call.adminRequest((Player)p, (Packets.AdminAction)Packets.AdminAction.trace, null);
                    }
                    this.infoPanel.remove();
                }).margin(10.0f).growX().height(45.0f).padTop(10.0f);
            }
        }).center();
        Core.scene.add((Element)this.infoPanel);
    }

    private void addCopyRow(Table table, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String displayText = label.isEmpty() ? "[white]" + value : "[lightgray]" + label + ": [accent]" + value;
        table.button(b -> {
            b.left().margin(4.0f);
            b.add((CharSequence)displayText).left().wrap().growX().fontScale(0.9f);
        }, (Button.ButtonStyle)Styles.flatBordert, () -> {
            Core.app.setClipboardText(value);
            Vars.ui.showInfoFade("[accent]" + (label.isEmpty() ? value : label) + Core.bundle.get("sam.info.copy"));
        }).growX().height(32.0f).padBottom(2.0f).row();
    }
}
