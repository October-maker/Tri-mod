package triangle.black.TMail;

import arc.Core;
import arc.files.Fi;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Http;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.Jval;
import arc.util.serialization.Jval.Jformat;

/**
 * 远端模组资讯加载器。
 *
 * <p>数据源：
 * <pre>
 * tmail/manifest.json
 * tmail/letters/&lt;letter-id&gt;/&lt;locale&gt;.json
 * </pre>
 *
 * <p>只有整份 manifest 及其中全部信件都成功加载后才替换现有资讯；
 * 网络失败、缺文件或非法 id 时保留旧资讯/内置兜底。
 */
public final class TriModInfo{

    private static final String RAW_BASE =
        "https://raw.githubusercontent.com/October-maker/Tri-mod/main/tmail/";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_LETTERS = 64;

    private static volatile boolean refreshing;
    private static boolean forcedRefresh;
    private static long lastRefresh;
    private static boolean cacheLoaded;
    private static String loadedLocale;

    private TriModInfo(){}

    public static boolean isRefreshing(){
        return refreshing;
    }

    /** 加载当前语言的本地缓存。 */
    public static void init(){
        loadCache();
    }

    /** 资讯页重建时调用；缓存过期才自动刷新。 */
    public static void refreshIfStale(){
        loadCache();
        if(refreshing) return;
        if(lastRefresh > 0 && Time.timeSinceMillis(lastRefresh) < CACHE_TTL_MS) return;
        refresh(false);
    }

    /** @param force 手动点击“获取最新资讯”时为 true，忽略 TTL。 */
    public static void refresh(boolean force){
        if(refreshing) return;
        if(!force && lastRefresh > 0 && Time.timeSinceMillis(lastRefresh) < CACHE_TTL_MS) return;

        refreshing = true;
        forcedRefresh = force;
        TriMailDialog.onModInfoRefreshStarted();

        Http.get(RAW_BASE + "manifest.json", response -> {
            try{
                Jval manifest = Jval.read(response.getResultAsString());
                Core.app.post(() -> fetchLetters(manifest));
            }catch(Throwable t){
                Log.err("Failed to parse Tmail mod info manifest.", t);
                finish(false);
            }
        }, error -> {
            Log.err("Failed to fetch Tmail mod info manifest.", error);
            finish(false);
        });
    }

    private static void fetchLetters(Jval manifest){
        if(manifest == null || !manifest.has("schema") || !"1".equals(manifest.getString("schema"))
            || !manifest.has("letters") || !manifest.get("letters").isArray()){
            finish(false);
            return;
        }

        Jval.JsonArray letters = manifest.get("letters").asArray();
        if(letters.size > MAX_LETTERS){
            Log.warn("Tmail mod info manifest has too many letters (@).", letters.size);
            finish(false);
            return;
        }
        if(letters.size == 0){
            apply(new Seq<>());
            return;
        }

        // 先验证 id/path，避免请求到一半才发现 manifest 非法。
        ObjectSet<String> seen = new ObjectSet<>();
        for(int i = 0; i < letters.size; i++){
            Jval entry = letters.get(i);
            String id = entry == null ? null : entry.getString("id", null);
            String path = entry == null ? null : entry.getString("path", null);
            if(id == null || id.isEmpty() || path == null || !validPath(path)){
                Log.warn("Invalid Tmail mod info letter entry at index @.", i);
                finish(false);
                return;
            }
            String safe = safeId(id);
            if(!seen.add(safe)){
                Log.warn("Duplicate Tmail mod info letter id '@'.", id);
                finish(false);
                return;
            }
        }

        TriMail[] result = new TriMail[letters.size];
        int[] remaining = {letters.size};
        boolean[] failed = {false};

        for(int i = 0; i < letters.size; i++){
            Jval entry = letters.get(i);
            int index = i;
            String id = entry.getString("id");
            String path = entry.getString("path");
            String key = "modinfo-" + safeId(id);

            fetchLetter(path, key, 0, mail -> {
                result[index] = mail;
                done(remaining, failed, result);
            }, () -> {
                // 任意一封失败则整次刷新失败，避免部分替换导致资讯缺失。
                Log.warn("Tmail mod info letter '@' could not be loaded.", id);
                failed[0] = true;
                done(remaining, failed, result);
            });
        }
    }

    private static void done(int[] remaining, boolean[] failed, TriMail[] result){
        if(--remaining[0] > 0) return;
        if(failed[0]){
            finish(false);
        }else{
            apply(compact(result));
        }
    }

    private static Seq<TriMail> compact(TriMail[] mails){
        Seq<TriMail> out = new Seq<>();
        for(TriMail mail : mails){
            if(mail != null) out.add(mail);
        }
        return out;
    }

    private static boolean validPath(String path){
        return !path.isEmpty() && !path.startsWith("/") && !path.contains("..")
            && !path.contains("://") && !path.contains("\\");
    }

    private static void fetchLetter(String path, String key, int localeIndex, arc.func.Cons<TriMail> ok, Runnable fail){
        String[] locales = localeCandidates();
        if(localeIndex >= locales.length){
            Core.app.post(fail);
            return;
        }

        String url = RAW_BASE + path + "/" + locales[localeIndex] + ".json";
        Http.get(url, response -> {
            try{
                Jval json = Jval.read(response.getResultAsString());
                String sender = json.getString("sender", "TMail");
                String subject = json.getString("subject", "");
                String body = json.getString("body", "");
                if(subject.isEmpty() && body.isEmpty()) throw new IllegalArgumentException("empty letter");

                TriMail mail = new TriMail(key, sender, subject, body, TriInbox.always(), false);
                Core.app.post(() -> ok.get(mail));
            }catch(Throwable t){
                fetchLetter(path, key, localeIndex + 1, ok, fail);
            }
        }, error -> fetchLetter(path, key, localeIndex + 1, ok, fail));
    }

    private static String[] localeCandidates(){
        String locale = Core.bundle.getLocale().toString();
        String language = locale;
        int underscore = locale.indexOf('_');
        if(underscore != -1) language = locale.substring(0, underscore);

        if(locale.isEmpty() || locale.equals("en")) return new String[]{"en"};
        if(language.equals(locale)) return new String[]{locale, "en"};
        return new String[]{locale, language, "en"};
    }

    private static String safeId(String id){
        String out = id == null ? "letter" : id.replaceAll("[^a-zA-Z0-9_-]", "-");
        return out.isEmpty() ? "letter" : out;
    }

    private static void apply(Seq<TriMail> mails){
        TriInbox.replaceInfoMails(mails);
        lastRefresh = Time.millis();
        refreshing = false;
        forcedRefresh = false;
        writeCache(mails);
        TriMailDialog.onModInfoRefreshFinished();
        TriMailDialog.onModInfoUpdated();
    }

    private static void finish(boolean success){
        Core.app.post(() -> {
            lastRefresh = Time.millis();
            refreshing = false;
            boolean wasForced = forcedRefresh;
            forcedRefresh = false;
            TriMailDialog.onModInfoRefreshFinished();
            if(success){
                TriMailDialog.onModInfoUpdated();
            }else if(wasForced){
                TriMailDialog.onModInfoRefreshFailed();
            }
        });
    }

    private static String currentLocale(){
        return Core.bundle.getLocale().toString();
    }

    private static Fi cacheFile(String locale){
        Fi dir = Core.settings.getDataDirectory().child("tmail");
        dir.mkdirs();
        return dir.child("tmail-modinfo-cache-" + safeId(locale) + ".json");
    }

    private static void writeCache(Seq<TriMail> mails){
        try{
            String locale = currentLocale();
            Jval root = Jval.newObject();
            root.put("locale", locale);
            Jval array = Jval.newArray();
            for(TriMail mail : mails){
                Jval item = Jval.newObject();
                item.put("key", mail.key);
                item.put("sender", mail.sender);
                item.put("subject", mail.subject);
                item.put("body", mail.body);
                array.add(item);
            }
            root.put("letters", array);
            cacheFile(locale).writeString(root.toString(Jformat.plain));
        }catch(Throwable t){
            Log.err("Failed to write Tmail mod info cache.", t);
        }
    }

    private static void loadCache(){
        String locale = currentLocale();
        if(cacheLoaded && locale.equals(loadedLocale)) return;
        cacheLoaded = true;
        loadedLocale = locale;

        Fi file = cacheFile(locale);
        if(!file.exists()) return;

        try{
            Jval root = Jval.read(file.readString());
            if(!locale.equals(root.getString("locale", ""))) return;
            if(!root.has("letters") || !root.get("letters").isArray()) return;

            Seq<TriMail> mails = new Seq<>();
            Jval.JsonArray array = root.get("letters").asArray();
            for(int i = 0; i < array.size; i++){
                Jval item = array.get(i);
                String key = item.getString("key", "");
                if(key.isEmpty()) continue;
                mails.add(new TriMail(key,
                    item.getString("sender", "TMail"),
                    item.getString("subject", ""),
                    item.getString("body", ""),
                    TriInbox.always(), false));
            }
            TriInbox.replaceInfoMails(mails);
            lastRefresh = 0; // 允许资讯页打开时做一次后台更新
        }catch(Throwable t){
            Log.err("Failed to read Tmail mod info cache.", t);
        }
    }
}
