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

/**
 * 三角通讯收件箱逻辑：邮件注册、战役进度解锁、已读状态持久化。
 */
public class TriInbox{

    /** 已读状态在 Core.settings 中的键前缀。 */
    public static final String READ_PREFIX = "tri-mail-read-";
    /** 附件收取状态在 Core.settings 中的键前缀。 */
    public static final String ATTACH_PREFIX = "tri-mail-attach-";

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
     */
    public static void registerDefaults(){
        add(new TriMail("intro", "TMail制作组", "欢迎使用","您好： \n     您订阅的TMail邮件系统现已完成部署，祝您使用愉快！", always()));

        // 带附件的剧情邮件：收取附件可强制解锁一项科技
        add(new TriMail("mend", "军需部", "紧急物资",
            "有一批技术档案随此信附上。\n签收后可立即解锁该技术的生产权限，不受当前研发进度限制。", always(),
            true, "技术档案：医疗投影仪",
            forceUnlock(Blocks.mendProjector)));

//        add(new TriMail("silicon", "科技部", "硅基纪元",
//            "微晶硅制备工艺已上线。\n更复杂的自动化和逻辑网络将由此展开。", tech(Items.silicon)));
//
//        add(new TriMail("groundzero", "前线指挥部", "滩头战报",
//            "滩头阵地已肃清，第一批补给线打通。\n后续区域的情报将随占领进度陆续送达。", sectorCaptured(SectorPresets.groundZero)));

        // ---- 模组资讯邮件（主菜单 TMail 显示，无条件解锁）----
        add(new TriMail("info_about", "October-maker", "关于TMail", "    TMail,三角模组的邮箱系统，将被用于发布模组资讯 \n     点击位于星球界面的右下角TMail按钮可以进入剧情版本TMail，其将作为阐述模组剧情的一员存在 \n     如有相关建议可以进入模组QQ群（469358412）或进入模组GitHub页面提交issue \n     祝游玩愉快", always(), false));

    }
}
