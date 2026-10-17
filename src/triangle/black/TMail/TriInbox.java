package triangle.black.TMail;

import arc.Core;
import arc.func.Boolp;
import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Planets;
import mindustry.content.SectorPresets;
import mindustry.ctype.UnlockableContent;
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

    private static final Seq<TriMail> mails = new Seq<>();

    private TriInbox(){}

    /** 注册一封邮件。 */
    public static void add(TriMail mail){
        mails.add(mail);
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
        return Core.settings.getBool(READ_PREFIX + mail.key, false);
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
        return Core.settings.getBool(ATTACH_PREFIX + mail.key, false);
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

    /** 恒真条件（无条件解锁）。 */
    public static Boolp always(){
        return () -> true;
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
     * 注册默认叙事邮件（演示：随科技/占领进度解锁）。
     * 在 Mod.loadContent() 中调用（此时 vanilla 内容已创建，可直接引用 Items 等）。
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
                always(),
                true,
                b("tri.mail.electrolyticCell.attachment"),
                forceUnlock(TriFactory.electrolyticCell)));//sectorCaptured(SectorPresets.biomassFacility)

//        add(new TriMail("silicon", "科技部", "硅基纪元",
//            "微晶硅制备工艺已上线。\n更复杂的自动化和逻辑网络将由此展开。", tech(Items.silicon)));

        // ---- 模组资讯邮件（主菜单 TMail 显示，无条件解锁）----
        add(new TriMail("info_about",
                b("tri.mail.info_about.sender"),
                b("tri.mail.info_about.subject"),
                b("tri.mail.info_about.body"),
                always(), false));

        // 中文示例信件（回信功能演示）：全部文本硬编码，不参与 bundle 本地化。
        // 回信内容同样硬编码（replyText 非空即不读 bundle）；
        // 发送后触发 onReply（演示事件：解锁重建投影仪），发送后内容保留显示、点击框再收起。
        // 打字机速度未设置（-1f）→ 使用默认 24 字/秒；如需演示单独调速，可改最后一个参数。
        add(new TriMail("demoReply", "示例发件人", "回信功能演示",
                "这是一封用于演示回信功能的中文示例信件。\n点击“回信”按钮，等待回信正文播放完毕后点击“发送”，回信内容将保留显示；再次点击回信框即可收起。",
                always(), true, null, null,
                () -> Blocks.mendProjector.unlock(),
                "收到！这是一封由PL发出的中文回信示例。\n回信内容可以直接硬编码在信件中，无需通过bundle进行本地化，适合用于事件触发的演示。\n——PL",
                -1f));

    }

    /** bundle 键取值，缺失时回退到键名本身（便于排查未定义的键）。 */
    private static String b(String key){
        return Core.bundle.get(key, key);
    }
}
