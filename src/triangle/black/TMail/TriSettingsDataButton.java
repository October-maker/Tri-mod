package triangle.black.TMail;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

/**
 * 在设置的数据页（“清除数据/重置存档”按钮所在的对话框）注入“重置战役信件”按钮：
 * 将所有 TMail 邮件的已读状态、附件收取状态与回信状态清除，恢复初始（全部未读、附件未收取、未回信）状态。
 *
 * 数据页对话框字段私有且原版不提供引用，故这里自愈注入：设置界面显示期间每帧只做
 * O(深度) 的可达性检查，仅当缓存失效（按钮表被移除/关闭、或尚未找到）才遍历场景树
 * 定位包含“清除数据”按钮的按钮表（按按钮文本反查，不依赖对话框标题，语言无关），
 * 把新按钮按同款样式（280×60、Styles.flatt）注入其末尾。
 * 场景树递归遍历带扫描冷却（每 0.25 秒至多一次），避免数据页未打开时每帧遍历全树。
 */
public class TriSettingsDataButton{

    private static TextButton injected;
    /** 缓存：已定位的按钮表；仅失效（被移除/关闭）时才重新扫描。 */
    private static Table table;
    /** 上次扫描时刻（秒），用于抑制数据页未打开时的每帧递归遍历。 */
    private static float lastScan = -10f;
    /** 扫描冷却时长（秒）：失败后至少间隔这么久才再次遍历场景树。 */
    private static final float SCAN_COOLDOWN = 0.25f;
    private static boolean registered = false;

    private TriSettingsDataButton(){}

    /** 注册（在 triangle 主类构造中调用一次）。 */
    public static void init(){
        if(registered) return;
        registered = true;
        Events.run(EventType.Trigger.update, TriSettingsDataButton::update);
    }

    private static void update(){
        if(Vars.ui == null || Vars.ui.settings == null || !Vars.ui.settings.isShown()) return;

        // O(深度) 自愈：按钮仍可达于场景树根则无需任何操作。
        // 注意不能用 hasParent()：数据对话框关闭（hide→remove）时被移除的是对话框自身，
        // 按钮的 parent 仍指向其内部按钮表（非 null），hasParent() 会误判按钮仍有效
        // 而永不重注入，导致下次打开数据页时按钮消失。
        if(injected != null && attachedToScene(injected)) return;

        // 缓存失效（表已被移出场景树、或尚未找到）才需要扫描；
        // 扫描带冷却，避免数据页未打开时每帧递归遍历场景树
        if(table == null || !attachedToScene(table)){
            if(Time.time - lastScan < SCAN_COOLDOWN) return;
            lastScan = Time.time;
            table = findDataTable(Core.scene.root);
            if(table == null) return;
        }

        // 自愈：按钮缺失（对话框重建等）则重新注入
        table.row();
        injected = table.button(bundle("tri.settings.resetmail"), Icon.trash,
            Styles.flatt, TriSettingsDataButton::confirmReset).marginLeft(4).get();
    }

    /** 沿 parent 链检查元素是否仍可达于场景树根（即处于显示中的对话框内）。 */
    private static boolean attachedToScene(Element e){
        if(Core.scene == null || Core.scene.root == null) return false;
        for(Element p = e; p != null; p = p.parent){
            if(p == Core.scene.root) return true;
        }
        return false;
    }

    /** 弹确认框；确认后清除全部邮件的已读/已收取状态并落盘。 */
    private static void confirmReset(){
        Vars.ui.showConfirm(bundle("tri.settings.resetmail"), bundle("tri.settings.resetmail.confirm"),
            () -> {
                for(TriMail m : TriInbox.all()){
                    Core.settings.remove(TriInbox.READ_PREFIX + m.key);
                    Core.settings.remove(TriInbox.READ_PREFIX + m.key + "-time");
                    Core.settings.remove(TriInbox.ATTACH_PREFIX + m.key);
                    Core.settings.remove(TriInbox.REPLY_PREFIX + m.key);
                    Core.settings.remove(TriInbox.READBTN_PREFIX + m.key);
                }
                Core.settings.forceSave();
                Vars.ui.showInfoFade(bundle("tri.settings.resetmail.done"));
            });
    }

    /**
     * 在场景树中递归查找包含“清除数据”按钮的按钮表。
     * 按按钮文本（同一 bundle 键取值的自洽匹配）反查，不依赖对话框标题，语言无关。
     */
    private static Table findDataTable(Group group){
        for(Element e : group.getChildren()){
            if(e instanceof Table){
                Table t = (Table)e;
                for(Element c : t.getChildren()){
                    if(c instanceof TextButton && bundle("settings.cleardata").equals(((TextButton)c).getText().toString())){
                        return t;
                    }
                }
            }
            if(e instanceof Group){
                Table t = findDataTable((Group)e);
                if(t != null) return t;
            }
        }
        return null;
    }

    /** bundle 键取值，缺失时回退到键名本身。 */
    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
