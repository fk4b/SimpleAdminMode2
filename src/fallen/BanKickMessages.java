package fallen;

import arc.Core;
import arc.struct.Seq;
import arc.util.Timer;
import mindustry.gen.Call;

import java.util.Random;

/**
 * Random ban/kick chat lines. {@code {lox}} = offender name placeholder.
 * Templates are editable in mod settings and stored in Core.settings.
 */
public final class BanKickMessages{
    public static final int MAX_CHAT = 140;
    public static final String PLACEHOLDER = "{lox}";

    public static final String KEY_KICKS = "sam-kick-msgs";
    public static final String KEY_BANS = "sam-ban-msgs";
    public static final String KEY_ENABLED = "sam-bankick-msgs";

    private static final Random rand = new Random();

    /** Built-in defaults (also shown in editor "reset"). */
    public static final String[] DEFAULT_KICKS = {
        "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]отошёл покурить",
        "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]решил сделать перерыв",
        "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]захотел подумать над своим поведением",
        "[#FFA13D][KICK]: Morj[#ff] отправляет [white]{lox} [#ff]в накаут",
        "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]задумался над тщетностью бытия",
        "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]занят обниманием Ползунов",
        "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]внезапно захотел перечитать правила",
        "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]драит палубу Омуры",
        "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]украшает гирляндами Токсопида",
        "[#FFA13D][KICK]: [white]{lox} [#008080]отправлен моржевать",
        "[#FFA13D][KICK]: [white]{lox} [#B6C1BC]заблудился в правилах",
    };

    public static final String[] DEFAULT_BANS = {
        "[#BC2300][BAN]: [white]{lox}, [#ff]отправляется чиллить в лобби",
        "[#BC2300][BAN]: [white]{lox} [#FF00FF]отправлен на зону 51",
        "[#BC2300][BAN]: [white]{lox} [#00]проиграл в русскую рулетку...",
        "[#BC2300][BAN]: [white]{lox} [#B6C1BC]прыгал на мине. Недолго.",
        "[#BC2300][BAN]: [white]{lox} [#B6C1BC]отправлен практиковать прыжки с парашютом. Без парашюта.",
        "[#BC2300][BAN]: [white]{lox} [#B6C1BC]брошен на корм акулам.",
        "[#BC2300][BAN]: [white]{lox} [#B6C1BC]выкинул белый флаг.",
        "[#BC2300][BAN]: [white]{lox} [#F2E878]аннигилирован Знамением...",
        "[#BC2300][BAN]: [white]{lox} [#9AEE8D]забран Октом",
        "[#BC2300][BAN]: [white]{lox} [#BF92F8]лёг под Токсопида",
        "[#BC2300][BAN]: [white]{lox} [#B6C1BC]купил билет на бановые острова",
        "[#BC2300][BAN]: [white]{lox} [#B6C1BC]познал боль",
        "[#BC2300][BAN]: [white]{lox} [#B6C1BC]не изменил свою судьбу",
        "[#BC2300][BAN]: [white]{lox} [#FF00FF]go to horny jail",
        "[#BC2300][BAN]: [white]{lox} [#B6C1BC]затянут за горизон событий",
        "[#BC2300][BAN]: [#B6C1BC]Рюмка водки на столе, пропел [white]{lox}",
        "[#BC2300][BAN]: [white]{lox} [#B6C1BC]пытался накормить вампира салатом",
    };

    private BanKickMessages(){}

    public static boolean enabled(){
        return Core.settings.getBool(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean v){
        Core.settings.put(KEY_ENABLED, v);
    }

    public static void kick(String lox){
        if(!enabled()) return;
        sendRandom(loadList(KEY_KICKS, DEFAULT_KICKS), lox);
    }

    public static void ban(String lox){
        if(!enabled()) return;
        sendRandom(loadList(KEY_BANS, DEFAULT_BANS), lox);
    }

    public static Seq<String> getKicks(){
        return loadList(KEY_KICKS, DEFAULT_KICKS);
    }

    public static Seq<String> getBans(){
        return loadList(KEY_BANS, DEFAULT_BANS);
    }

    public static void saveKicks(Seq<String> list){
        saveList(KEY_KICKS, list);
    }

    public static void saveBans(Seq<String> list){
        saveList(KEY_BANS, list);
    }

    public static void resetKicks(){
        Core.settings.remove(KEY_KICKS);
    }

    public static void resetBans(){
        Core.settings.remove(KEY_BANS);
    }

    /** Join before + {lox} + after into a template. */
    public static String joinParts(String before, String after){
        if(before == null) before = "";
        if(after == null) after = "";
        return before + PLACEHOLDER + after;
    }

    /** Split template at first {lox}. If missing, whole text is "before", after empty. */
    public static String[] splitParts(String template){
        if(template == null) template = "";
        int at = template.indexOf(PLACEHOLDER);
        if(at < 0){
            return new String[]{template, ""};
        }
        return new String[]{
            template.substring(0, at),
            template.substring(at + PLACEHOLDER.length())
        };
    }

    public static String preview(String template, String sampleName){
        if(template == null) return "";
        String name = sampleName == null ? "Player" : sampleName;
        if(!template.contains(PLACEHOLDER)){
            return template + " " + name;
        }
        return template.replace(PLACEHOLDER, name);
    }

    private static Seq<String> loadList(String key, String[] defaults){
        String raw = Core.settings.getString(key, "");
        Seq<String> out = new Seq<>();
        if(raw == null || raw.isEmpty()){
            for(String s : defaults) out.add(s);
            return out;
        }
        // one template per line; empty lines skipped
        for(String line : raw.split("\n", -1)){
            String t = line;
            // allow literal \n escape if ever needed — keep raw line as stored
            if(t.isEmpty()) continue;
            out.add(t);
        }
        if(out.isEmpty()){
            for(String s : defaults) out.add(s);
        }
        return out;
    }

    private static void saveList(String key, Seq<String> list){
        if(list == null || list.isEmpty()){
            Core.settings.put(key, "");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < list.size; i++){
            String s = list.get(i);
            if(s == null) continue;
            // strip newlines so each template is one line
            s = s.replace("\r", "").replace("\n", " ");
            if(s.isEmpty()) continue;
            if(sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        Core.settings.put(key, sb.toString());
    }

    private static void sendRandom(Seq<String> pool, String lox){
        if(pool == null || pool.isEmpty()) return;
        String name = lox == null ? "?" : lox;
        String msg = pool.get(rand.nextInt(pool.size)).replace(PLACEHOLDER, name);
        sendChatSplit(msg);
    }

    public static void sendChatSplit(String message){
        if(message == null || message.isEmpty()) return;

        Seq<String> parts = splitByWords(message, MAX_CHAT);
        if(parts.size == 1){
            Call.sendChatMessage(parts.first());
            return;
        }

        for(int i = 0; i < parts.size; i++){
            final String part = parts.get(i);
            float delay = i * 0.35f;
            if(delay <= 0f){
                Call.sendChatMessage(part);
            }else{
                Timer.schedule(() -> Call.sendChatMessage(part), delay);
            }
        }
    }

    public static Seq<String> splitByWords(String text, int maxLen){
        Seq<String> out = new Seq<>();
        if(text == null || text.isEmpty()) return out;
        if(maxLen < 8) maxLen = 8;
        if(text.length() <= maxLen){
            out.add(text);
            return out;
        }

        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();

        for(String word : words){
            if(word.isEmpty()) continue;

            if(word.length() > maxLen){
                if(cur.length() > 0){
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                int pos = 0;
                while(pos < word.length()){
                    int end = Math.min(pos + maxLen, word.length());
                    out.add(word.substring(pos, end));
                    pos = end;
                }
                continue;
            }

            if(cur.length() == 0){
                cur.append(word);
            }else if(cur.length() + 1 + word.length() <= maxLen){
                cur.append(' ').append(word);
            }else{
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }

        if(cur.length() > 0){
            out.add(cur.toString());
        }
        return out;
    }
}
