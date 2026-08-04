package fallen;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Label;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * Editor for ban/kick chat message templates.
 * Layout: text before nick | {lox} | text after nick + live preview.
 */
public class BanKickMessageEditor extends BaseDialog{

    private enum Mode{ KICK, BAN }

    private Mode mode = Mode.KICK;
    private Seq<String> list = new Seq<>();
    private int selected = -1;
    private Table listTable;
    private TextField beforeField;
    private TextField afterField;
    private TextArea rawArea;
    private Label previewLabel;
    private Label countLabel;
    private boolean suppressSave;

    public BanKickMessageEditor(){
        super(Core.bundle.get("sam.bankick.editor.title"));
        addCloseButton();
        shown(this::reload);
        cont.clear();
        buildUI();
    }

    private void buildUI(){
        cont.table(root -> {
            root.top().defaults().pad(4f);

            root.check(Core.bundle.get("sam.bankick.enabled"),
                BanKickMessages.enabled(),
                BanKickMessages::setEnabled).left().padBottom(8f).row();

            root.table(tabs -> {
                tabs.defaults().height(44f).growX();
                tabs.button(Core.bundle.get("sam.bankick.tab.kick"), Styles.togglet, () -> {
                    if(mode != Mode.KICK){
                        saveCurrentList();
                        mode = Mode.KICK;
                        reload();
                    }
                }).update(b -> b.setChecked(mode == Mode.KICK));
                tabs.button(Core.bundle.get("sam.bankick.tab.ban"), Styles.togglet, () -> {
                    if(mode != Mode.BAN){
                        saveCurrentList();
                        mode = Mode.BAN;
                        reload();
                    }
                }).update(b -> b.setChecked(mode == Mode.BAN));
            }).growX().row();

            countLabel = root.add("").left().padTop(4f).get();
            root.row();

            root.table(body -> {
                // LEFT list
                body.table(Styles.black6, left -> {
                    left.top();
                    left.add(Core.bundle.get("sam.bankick.list")).color(Pal.accent).pad(6f).left().row();
                    left.pane(p -> {
                        listTable = p;
                        listTable.top().left().defaults().growX().pad(2f);
                    }).grow().width(340f).maxHeight(440f).row();

                    left.table(btns -> {
                        btns.defaults().height(40f).growX().pad(2f);
                        btns.button(Core.bundle.get("sam.bankick.add"), Icon.add, Styles.flatt, this::addNew).row();
                        btns.button(Core.bundle.get("sam.bankick.delete"), Icon.trash, Styles.flatt, this::deleteSelected)
                            .disabled(b -> selected < 0 || selected >= list.size).row();
                        btns.button(Core.bundle.get("sam.bankick.reset"), Icon.refresh, Styles.flatt, this::resetDefaults).row();
                    }).growX().pad(4f);
                }).growY().top();

                // RIGHT editor
                body.table(Styles.black6, right -> {
                    right.top().defaults().growX().pad(4f);
                    right.add(Core.bundle.get("sam.bankick.edit")).color(Pal.accent).pad(6f).left().row();

                    right.add(Core.bundle.get("sam.bankick.before")).left().color(Color.lightGray).row();
                    beforeField = right.field("", s -> onPartsChanged()).height(40f).get();
                    beforeField.setMessageText(Core.bundle.get("sam.bankick.before.hint"));
                    right.row();

                    right.table(nick -> {
                        nick.left();
                        nick.add(Core.bundle.get("sam.bankick.nick")).color(Color.lightGray).padRight(8f);
                        nick.table(Styles.black3, box -> {
                            box.add("[accent]{lox}[]  " + Core.bundle.get("sam.bankick.nick.mark")).pad(6f);
                        }).height(36f);
                    }).left().padTop(8f).padBottom(8f).row();

                    right.add(Core.bundle.get("sam.bankick.after")).left().color(Color.lightGray).row();
                    afterField = right.field("", s -> onPartsChanged()).height(40f).get();
                    afterField.setMessageText(Core.bundle.get("sam.bankick.after.hint"));
                    right.row();

                    right.add(Core.bundle.get("sam.bankick.raw")).left().color(Color.lightGray).padTop(10f).row();
                    rawArea = new TextArea("");
                    rawArea.setPrefRows(3);
                    right.add(rawArea).height(90f).growX().row();
                    right.button(Core.bundle.get("sam.bankick.applyRaw"), Styles.flatt, () -> {
                        if(selected < 0 || selected >= list.size) return;
                        String raw = rawArea.getText();
                        if(raw == null) raw = "";
                        raw = raw.replace("\r", "").replace("\n", " ");
                        if(!raw.contains(BanKickMessages.PLACEHOLDER)){
                            raw = raw + BanKickMessages.PLACEHOLDER;
                        }
                        list.set(selected, raw);
                        loadSelectedIntoFields();
                        saveCurrentList();
                        rebuildList();
                    }).height(40f).padTop(4f).row();

                    right.add(Core.bundle.get("sam.bankick.preview")).left().color(Pal.accent).padTop(12f).row();
                    previewLabel = right.add("").left().wrap().width(440f).pad(6f).get();
                    previewLabel.setAlignment(Align.left);

                    right.table(prev -> {
                        prev.defaults().pad(3f).height(36f);
                        prev.button(Core.bundle.get("sam.bankick.previewSample"), Styles.flatt,
                            () -> updatePreview("TestPlayer"));
                        prev.button(Core.bundle.get("sam.bankick.previewSelf"), Styles.flatt, () -> {
                            String n = Vars.player != null ? Vars.player.name : "Player";
                            updatePreview(n);
                        });
                    }).left().padTop(4f).row();

                    right.add(Core.bundle.get("sam.bankick.help")).left().wrap().width(440f)
                        .color(Color.gray).padTop(10f).row();
                }).grow().top().padLeft(8f);
            }).grow().row();

            root.table(bot -> {
                bot.button(Core.bundle.get("sam.bankick.save"), Icon.ok, Styles.flatt, () -> {
                    saveCurrentList();
                    Vars.ui.showInfoFade(Core.bundle.get("sam.bankick.saved"));
                }).size(160f, 48f);
                bot.button("@close", Icon.left, Styles.flatt, this::hide).size(160f, 48f).padLeft(8f);
            }).padTop(10f);
        }).grow();
    }

    private void reload(){
        list = mode == Mode.KICK ? BanKickMessages.getKicks() : BanKickMessages.getBans();
        selected = list.isEmpty() ? -1 : 0;
        rebuildList();
        loadSelectedIntoFields();
        updateCount();
    }

    private void updateCount(){
        if(countLabel == null) return;
        String type = mode == Mode.KICK ? "KICK" : "BAN";
        countLabel.setText("[lightgray]" + type + ": [accent]" + list.size + "[] "
            + Core.bundle.get("sam.bankick.count"));
    }

    private void rebuildList(){
        if(listTable == null) return;
        int keep = selected;
        listTable.clear();
        for(int i = 0; i < list.size; i++){
            final int idx = i;
            String template = list.get(i);
            String shortPreview = BanKickMessages.preview(template, "…");
            if(shortPreview.length() > 52) shortPreview = shortPreview.substring(0, 52) + "…";

            TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(Styles.flatt);
            listTable.button((idx + 1) + ". " + shortPreview, style, () -> {
                flushFieldsToSelected();
                selected = idx;
                loadSelectedIntoFields();
                rebuildList();
            }).height(42f).growX().update(b -> b.setChecked(selected == idx)).row();
        }
        if(list.isEmpty()){
            listTable.add("[gray]" + Core.bundle.get("sam.bankick.empty")).pad(12f);
        }
        selected = keep;
        updateCount();
    }

    private void loadSelectedIntoFields(){
        suppressSave = true;
        try{
            if(selected < 0 || selected >= list.size){
                if(beforeField != null) beforeField.setText("");
                if(afterField != null) afterField.setText("");
                if(rawArea != null) rawArea.setText("");
                if(previewLabel != null) previewLabel.setText("[gray]—");
                return;
            }
            String t = list.get(selected);
            String[] parts = BanKickMessages.splitParts(t);
            if(beforeField != null) beforeField.setText(parts[0]);
            if(afterField != null) afterField.setText(parts[1]);
            if(rawArea != null) rawArea.setText(t);
            updatePreview("TestPlayer");
        }finally{
            suppressSave = false;
        }
    }

    private void onPartsChanged(){
        if(suppressSave) return;
        flushFieldsToSelected();
        updatePreview("TestPlayer");
        Core.app.post(this::rebuildList);
    }

    private void flushFieldsToSelected(){
        if(selected < 0 || selected >= list.size) return;
        if(beforeField == null || afterField == null) return;
        String before = beforeField.getText();
        String after = afterField.getText();
        if(before == null) before = "";
        if(after == null) after = "";
        String joined = BanKickMessages.joinParts(before, after);
        list.set(selected, joined);
        if(rawArea != null && !suppressSave){
            suppressSave = true;
            rawArea.setText(joined);
            suppressSave = false;
        }
    }

    private void updatePreview(String sample){
        if(previewLabel == null) return;
        if(selected < 0 || selected >= list.size){
            previewLabel.setText("[gray]—");
            return;
        }
        previewLabel.setText(BanKickMessages.preview(list.get(selected), sample));
    }

    private void addNew(){
        flushFieldsToSelected();
        String def = mode == Mode.KICK
            ? "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]новый текст"
            : "[#BC2300][BAN]: [white]{lox} [#B6C1BC]новый текст";
        list.add(def);
        selected = list.size - 1;
        saveCurrentList();
        rebuildList();
        loadSelectedIntoFields();
    }

    private void deleteSelected(){
        if(selected < 0 || selected >= list.size) return;
        list.remove(selected);
        if(selected >= list.size) selected = list.size - 1;
        saveCurrentList();
        rebuildList();
        loadSelectedIntoFields();
    }

    private void resetDefaults(){
        Vars.ui.showConfirm(Core.bundle.get("sam.bankick.reset.confirm"), () -> {
            if(mode == Mode.KICK) BanKickMessages.resetKicks();
            else BanKickMessages.resetBans();
            reload();
            Vars.ui.showInfoFade(Core.bundle.get("sam.bankick.reset.done"));
        });
    }

    private void saveCurrentList(){
        flushFieldsToSelected();
        if(mode == Mode.KICK) BanKickMessages.saveKicks(list);
        else BanKickMessages.saveBans(list);
    }

    @Override
    public void hide(){
        saveCurrentList();
        super.hide();
    }
}
