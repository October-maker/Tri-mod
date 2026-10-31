package triangle.black.TMail;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.content.SectorPresets;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.gen.Sounds;
import mindustry.ui.Menus;
import mindustry.type.Sector;
import mindustry.type.SectorPreset;
import triangle.content.TriFactory;

/**
 * 三角通讯收件箱逻辑：邮件注册、战役进度解锁、已读状态持久化。
 */
public class TriInbox{

    /** 已读状态在 Core.settings 中的键前缀。 */
    public static final String READ_PREFIX = "tri-mail-read-";
    /** 附件收取状态在 Core.settings 中的键前缀。 */
    public static final String ATTACH_PREFIX = "tri-mail-attach-";
    /** 回信状态在 Core.settings 中的键前缀（发送后记忆，可被“重置战役信件”按钮清除）。 */
    public static final String REPLY_PREFIX = "tri-mail-reply-";
    /** 详情页“已读”按钮置灰状态在 Core.settings 中的键前缀（点击后记忆）。 */
    public static final String READBTN_PREFIX = "tri-mail-readbtn-";
    /** “收到新邮件”提示已显示状态在 Core.settings 中的键前缀（每封邮件只提示一次）。 */
    public static final String NOTIFIED_PREFIX = "tri-mail-notified-";

    private static final Seq<TriMail> mails = new Seq<>();
    /** 会话内已提示过的邮件键（避免每帧读 settings；跨会话由 NOTIFIED_PREFIX 持久化）。 */
    private static final ObjectSet<String> notifiedSession = new ObjectSet<>();
    private static boolean newMailHooked = false;
    /** 新邮件解锁检查间隔（秒），避免每帧遍历全部邮件。 */
    private static float mailCheckTimer = 0f;

    private TriInbox(){}

    /** 注册一封邮件。 */
    public static void add(TriMail mail){
        mails.add(mail);
    }

    /** 用远端资讯邮件替换现有资讯信件；战役剧情邮件及其他来源邮件保持不变。 */
    public static void replaceInfoMails(Seq<TriMail> replacement){
        for(int i = mails.size - 1; i >= 0; i--){
            TriMail mail = mails.get(i);
            if(!mail.story && (mail.key.startsWith("modinfo-") || mail.key.equals("info_about"))){
                mails.remove(i);
            }
        }
        mails.addAll(replacement);
    }

    /** 全部邮件（含未解锁）。 */
    public static Seq<TriMail> all(){
        return mails;
    }

    /** 当前战役进度已解锁的战役剧情邮件（story=true）。 */
    public static Seq<TriMail> unlocked(){
        return unlocked(true);
    }

    /** 当前战役进度已解锁的指定类型邮件（story=true 剧情 / story=false 资讯）。 */
    public static Seq<TriMail> unlocked(boolean story){
        Seq<TriMail> out = new Seq<>();
        for(TriMail m : mails) if(m.story == story && m.unlocked.get()) out.add(m);
        return out;
    }

    public static boolean isRead(TriMail mail){
        return isRead(mail.key);
    }

    /** 按稳定 key 查询已读状态（供配方解锁条件等外部逻辑使用）。 */
    public static boolean isRead(String mailKey){
        return Core.settings.getBool(READ_PREFIX + mailKey, false);
    }

    public static void markRead(TriMail mail){
        Core.settings.put(READ_PREFIX + mail.key, true);
        // 记录最近阅读时间戳，供“历史”分类按最后阅读顺序排列
        Core.settings.put(READ_PREFIX + mail.key + "-time", System.currentTimeMillis());
    }

    /** 最近一次阅读的时间戳（毫秒）；从未读过返回 0。 */
    public static long readTime(TriMail mail){
        return Core.settings.getLong(READ_PREFIX + mail.key + "-time", 0);
    }

    // ---------- 附件 ----------

    /** 该邮件是否有附件。 */
    public static boolean hasAttachment(TriMail mail){
        return mail.hasAttachment;
    }

    /** 附件是否已被收取。 */
    public static boolean attachmentCollected(TriMail mail){
        return attachmentCollected(mail.key);
    }

    /** 按稳定 key 查询附件是否已收取。 */
    public static boolean attachmentCollected(String mailKey){
        return Core.settings.getBool(ATTACH_PREFIX + mailKey, false);
    }

    /** 收取附件：标记为已收取并执行附件动作（如强制解锁科技）。 */
    public static void collectAttachment(TriMail mail){
        if(attachmentCollected(mail)) return; // 已收取则不重复执行
        Core.settings.put(ATTACH_PREFIX + mail.key, true);
        if(mail.onCollect != null) mail.onCollect.run();
    }

    /** 附件动作：强制解锁指定内容（科技）。剧情模式信件可用。 */
    public static Runnable forceUnlock(UnlockableContent content){
        return () -> content.unlock();
    }

    // ---------- 回信 ----------

    /** 该邮件是否已回信（发送过回信，状态已持久化记忆）。 */
    public static boolean isReplied(TriMail mail){
        return Core.settings.getBool(REPLY_PREFIX + mail.key, false);
    }

    /** 标记为已回信（发送按钮点击时调用；“重置战役信件”按钮会清除该状态）。 */
    public static void markReplied(TriMail mail){
        Core.settings.put(REPLY_PREFIX + mail.key, true);
    }

    // ---------- 已读按钮 ----------

    /** 详情页“已读”按钮是否已被点击置灰（状态持久化记忆）。 */
    public static boolean isReadBtnClicked(TriMail mail){
        return Core.settings.getBool(READBTN_PREFIX + mail.key, false);
    }

    /** 标记“已读”按钮已点击（置灰后记忆；“重置战役信件”按钮会清除该状态）。 */
    public static void markReadBtnClicked(TriMail mail){
        Core.settings.put(READBTN_PREFIX + mail.key, true);
    }

    // ---------- 收到新邮件提示 ----------

    /** 该邮件是否已显示过“收到新邮件”提示。 */
    public static boolean isNotified(TriMail mail){
        return Core.settings.getBool(NOTIFIED_PREFIX + mail.key, false);
    }

    /** 标记已显示提示（持久化，跨会话不重复提示）。 */
    public static void markNotified(TriMail mail){
        Core.settings.put(NOTIFIED_PREFIX + mail.key, true);
    }

    /**
     * 注册“收到新邮件”的地图内提示（在 triangle 主类构造中调用一次）。
     *
     * 解锁条件为被动 Boolp（仅在评估时才知道是否满足），故采用轮询：
     * 每帧仅在玩家处于地图内（Vars.state.isGame()）时检查战役剧情邮件，
     * 某封邮件首次满足解锁条件（且该封从未提示过）时弹出黑色信息条
     * （Menus.infoToast，与原版世界处理器“显示信息”的弹黑框模式一致，
     * 也是 InfoToastCallPacket 客户端渲染所用的本地 API），显示 3 秒，
     * 并播放原版通知音效（Sounds.uiNotify）作为接收提示。
     * 无条件邮件（always()）不触发提示；已提示状态持久化，避免每封信重复打扰；
     * 会话内用内存集合做快速跳过。
     */
    public static void hookNewMailToast(){
        if(newMailHooked) return;
        newMailHooked = true;
        Events.run(EventType.Trigger.update, () -> {
            if(!Vars.state.isGame()) return;

            mailCheckTimer -= Time.delta;
            if(mailCheckTimer > 0f) return;
            mailCheckTimer = 1f;

            for(TriMail m : mails){
                // 只提示战役剧情邮件；会话内已提示过的直接跳过（快路径）
                if(!m.story || notifiedSession.contains(m.key)) continue;
                // 无条件邮件（always()）收到时不触发提示
                if(m.unlocked == TriInbox.always()) continue;
                if(!m.unlocked.get()) continue;
                notifiedSession.add(m.key);
                // 跨会话只提示一次：之前会话已提示过则不再重复
                if(isNotified(m)) continue;
                markNotified(m);
                Menus.infoToast(Core.bundle.get("tri.mail.newmail", "tri.mail.newmail"), 3f);
                Sounds.uiNotify.play();
            }
        });
    }

    /** 未读（已解锁且未读）数量：默认统计战役剧情邮件。 */
    public static int unreadCount(){
        return unreadCount(true);
    }

    /** 未读（已解锁且未读）数量：按类型统计（story=true 剧情 / story=false 资讯）。 */
    public static int unreadCount(boolean story){
        int n = 0;
        for(TriMail m : mails) if(m.story == story && m.unlocked.get() && !isRead(m)) n++;
        return n;
    }

    // ---------- 常用战役进度条件 ----------

    /** 科技树已解锁指定内容（Block / Item / Unit 等）。 */
    public static Boolp tech(UnlockableContent c){
        return () -> c.unlocked();
    }

    /** 已占领指定预设区域。 */
    public static Boolp sectorCaptured(SectorPreset preset){
        return () -> {
            Sector s = sectorOf(preset);
            return s != null && s.info != null && s.info.wasCaptured;
        };
    }

    /** 恒真条件（无条件解锁）。共享单例：新邮件提示据此跳过无条件邮件。 */
    public static final Boolp ALWAYS = () -> true;

    /** 恒真条件（无条件解锁）。 */
    public static Boolp always(){
        return ALWAYS;
    }

    /** 全部条件同时满足。 */
    public static Boolp all(Boolp... conds){
        return () -> {
            for(Boolp b : conds) if(!b.get()) return false;
            return true;
        };
    }

    /** 在赛普罗（Serpulo）行星上查找指定预设对应的区域。 */
    public static Sector sectorOf(SectorPreset preset){
        for(Sector s : Planets.serpulo.sectors){
            if(s.preset == preset) return s;
        }
        return null;
    }

    /**
     * 注册默认叙事邮件（随科技/占领进度解锁）。
     * 在 Mod.loadContent() 中调用（此时原版内容已创建，可直接引用 Items 等）。
     *
     * 邮件文本统一走 bundle（键 tri.mail.&lt;key&gt;.&lt;field&gt;，中英双份），支持本地化；
     * 此处处于 Mod.loadContent()，模组 bundle 已在 Mods.load() 的 buildFiles() 中并入
     * Core.bundle，可直接读取；缺失时回退键名便于排查。
     */
    public static void registerDefaults(){
        add(new TriMail("intro",
                b("tri.mail.intro.sender"),
                b("tri.mail.intro.subject"),
                b("tri.mail.intro.body"),
                always()));

        // 带附件的剧情邮件：收取附件可强制解锁一项科技
        add(new TriMail("electrolyticCell",
                b("tri.mail.electrolyticCell.sender"),
                b("tri.mail.electrolyticCell.subject"),
                b("tri.mail.electrolyticCell.body"),
                sectorCaptured(SectorPresets.biomassFacility),
                true,
                b("tri.mail.electrolyticCell.attachment"),
                forceUnlock(TriFactory.electrolyticCell)));

//        add(new TriMail("silicon", "科技部", "硅基纪元",
//            "微晶硅制备工艺已上线。\n更复杂的自动化和逻辑网络将由此展开。", tech(Items.silicon)));

         //示例剧情邮件：解锁“硅”科技时收到（文本硬编码，不参与 bundle 本地化）
//        add(new TriMail("silicon",
//                "科技部",
//                "传输",
//                "测试邮件。\n当灰带被解锁时被发送至玩家邮件箱。\n——示例剧情邮件",
//                tech(Blocks.conveyor)));

        // ---- 模组资讯邮件（主菜单 TMail 显示，无条件解锁）----
//        add(new TriMail("info_about",
//                b("tri.mail.info_about.sender"),
//                b("tri.mail.info_about.subject"),
//                b("tri.mail.info_about.body"),
//                always(), false));

        // 中文示例信件（回信功能演示）：全部文本硬编码，不参与 bundle 本地化。
        // 回信内容同样硬编码（replyText 非空即不读 bundle）；
        // 发送后触发 onReply（演示事件：解锁重建投影仪），发送后内容保留显示、点击框再收起。
        // 打字机速度未设置（-1f）→ 使用默认 24 字/秒；如需演示单独调速，可改最后一个参数。
//        add(new TriMail("demoReply", "示例发件人", "回信功能演示",
//                "这是一封用于演示回信功能的中文示例信件。\n点击“回信”按钮，等待回信正文播放完毕后点击“发送”，回信内容将保留显示；再次点击回信框即可收起。",
//                always(), true, null, null,
//                () -> Blocks.mendProjector.unlock(),
//                "收到！这是一封由PL发出的中文回信示例。\n回信内容可以直接硬编码在信件中，无需通过bundle进行本地化，适合用于事件触发的演示。\n——PL",
//                -1f));

    }

    /** bundle 键取值，缺失时回退到键名本身（便于排查未定义的键）。 */
    private static String b(String key){
        return Core.bundle.get(key, key);
    }
}
