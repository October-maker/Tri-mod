package triangle.black.TMail;

import arc.Core;
import arc.func.Boolp;
import arc.struct.Seq;
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

    /** 当前战役进度已解锁的邮件。 */
    public static Seq<TriMail> unlocked(){
        Seq<TriMail> out = new Seq<>();
        for(TriMail m : mails) if(m.unlocked.get()) out.add(m);
        return out;
    }

    public static boolean isRead(TriMail mail){
        return Core.settings.getBool(READ_PREFIX + mail.key, false);
    }

    public static void markRead(TriMail mail){
        Core.settings.put(READ_PREFIX + mail.key, true);
    }

    /** 未读（已解锁且未读）数量。 */
    public static int unreadCount(){
        int n = 0;
        for(TriMail m : mails) if(m.unlocked.get() && !isRead(m)) n++;
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
        add(new TriMail("intro", "指挥部", "欢迎，指挥官",
            "欢迎来到三角前线。\n本通讯系统将随你的战役进度推送战地情报与故事片段。", always()));

        add(new TriMail("silicon", "科技部", "硅基纪元",
            "微晶硅制备工艺已上线。\n更复杂的自动化和逻辑网络将由此展开。", tech(Items.silicon)));

        add(new TriMail("titanium", "后勤部", "钛金属就绪",
            "钛矿已可开采。\n轻质高强的钛结构将为你的防线提供新的可能。", tech(Items.titanium)));

        add(new TriMail("groundzero", "前线指挥部", "滩头战报",
            "滩头阵地已肃清，第一批补给线打通。\n后续区域的情报将随占领进度陆续送达。", sectorCaptured(SectorPresets.groundZero)));

        add(new TriMail("plastanium", "材料所", "塑料合金",
            "复合塑钢已可量产。\n……总部对你在多条战线上的持续推进表示关切。", tech(Items.plastanium)));

        add(new TriMail("surgealloy", "总司令", "决战在即",
            "冲击合金已列入装备序列。\n能走到这一步，说明你的攻势足以动摇整片大陆。", tech(Items.surgeAlloy)));
    }
}
