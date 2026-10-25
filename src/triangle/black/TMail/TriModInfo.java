package triangle.black.TMail;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Http;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.Jval;
import arc.util.serialization.Jval.Jformat;

/**
 * Remote-driven mod info letters for Tmail.
 *
 * Source layout:
 * tmail/manifest.json
 * tmail/letters/<letter>/<locale>.json
 *
 * The raw base points at the main branch of October-maker/Tri-mod.
 * Network failures never remove the built-in fallback info mail.
 */
public final class TriModInfo{

    private static final String RAW_BASE =
        "https://raw.githubusercontent.com/October-maker/Tri-mod/main/tmail/";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final String CACHE_FILE = "tmail-modinfo-cache.json";

    private static volatile boolean refreshing;
    private static boolean cacheLoaded;
    private static long lastRefresh;

    private TriModInfo(){}

    public static boolean isRefreshing(){
        return refreshing;
    }

    /** Load the last successful localized cache, if it matches the current locale. */
    public static void init(){
        loadCache();
    }

    /** Called when the info page is rebuilt. */
    public static void refreshIfStale(){
        if(refreshing) return;
        if(lastRefresh > 0 && Time.timeSinceMillis(lastRefresh) < CACHE_TTL_MS) return;
        refresh(false);
    }

    /** Force a remote refresh. Used by the "latest info" button. */
    public static void refresh(boolean force){
        if(refreshing) return;
        refreshing = true;
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
        if(manifest == null || !manifest.has("letters") || !manifest.get("letters").isArray()){
            finish(false);
            return;
        }

        Jval.JsonArray letters = manifest.get("letters").asArray();
        if(letters.size == 0){
            apply(new Seq<>());
            return;
        }

        TriMail[] result = new TriMail[letters.size];
        int[] remaining = {letters.size};
        boolean[] any = {false};

        for(int i = 0; i < letters.size; i++){
            Jval entry = letters.get(i);
            int index = i;
            String id = entry.getString("id", "letter-" + i);
            String path = entry.getString("path", "letters/" + id);
            String key = "modinfo-" + safeId(id);

            fetchLetter(path, key, 0, mail -> {
                result[index] = mail;
                any[0] = true;
                if(--remaining[0] == 0){
                    apply(compact(result, any[0]));
                }
            }, () -> {
                Log.warn("Tmail mod info letter '@' could not be loaded.", id);
                if(--remaining[0] == 0){
                    if(any[0]){
                        apply(compact(result, true));
                    }else{
                        finish(false);
                    }
                }
            });
        }
    }

    private static Seq<TriMail> compact(TriMail[] mails, boolean any){
        Seq<TriMail> out = new Seq<>();
        for(TriMail mail : mails){
            if(mail != null) out.add(mail);
        }
        return out;
    }

    private static void fetchLetter(String path, String key, int localeIndex, arc.func.Cons<TriMail> ok, Runnable fail){
        if(path == null || path.isEmpty() || path.startsWith("/") || path.contains("..")){
            Core.app.post(fail);
            return;
        }

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
                if(subject.isEmpty() && body.isEmpty()){
                    throw new IllegalArgumentException("empty letter");
                }
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
        writeCache(mails);
        TriMailDialog.onModInfoUpdated();
    }

    private static void finish(boolean success){
        Core.app.post(() -> {
            lastRefresh = Time.millis();
            refreshing = false;
            TriMailDialog.onModInfoRefreshFinished();
            if(success) TriMailDialog.onModInfoUpdated();
        });
    }

    private static Fi cacheFile(){
        Fi dir = Core.settings.getDataDirectory().child("tmail");
        dir.mkdirs();
        return dir.child(CACHE_FILE);
    }

    private static void writeCache(Seq<TriMail> mails){
        try{
            Jval root = Jval.newObject();
            root.put("locale", Core.bundle.getLocale().toString());
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
            cacheFile().writeString(root.toString(Jformat.plain));
        }catch(Throwable t){
            Log.err("Failed to write Tmail mod info cache.", t);
        }
    }

    private static void loadCache(){
        if(cacheLoaded) return;
        cacheLoaded = true;

        Fi file = cacheFile();
        if(!file.exists()) return;

        try{
            Jval root = Jval.read(file.readString());
            if(!Core.bundle.getLocale().toString().equals(root.getString("locale", ""))) return;
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
            lastRefresh = 0; // allow one background update when the info page is opened
        }catch(Throwable t){
            Log.err("Failed to read Tmail mod info cache.", t);
        }
    }
}
