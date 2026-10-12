package triangle.black.TMail;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Dialog;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

/**
 * 在设置的数据页（“清除数据/重置存档”按钮所在的对话框）注入“重置战役信件”按钮：
 * 将所有 TMail 邮件的已读状态与附件收取状态清除，恢复初始（全部未读、附件未收取）状态。
 *
 * 数据对话框（标题为 settings.data）由原版在 SettingsMenuDialog 构造时一次性创建且字段私有，
 * 无法直接引用，故这里逐帧自愈：设置界面打开时，在场景树中按标题找到数据对话框，
 * 再定位包含“清除数据”按钮的按钮表，把新按钮按同款样式（280×60、Styles.flatt）注入其末尾。
 */
public class TriSettingsDataButton{

    private static TextButton injected;
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

        Dialog data = findDataDialog(Core.scene.root);
        if(data == null) return;

        Table table = findButtonTable(data);
        if(table == null) return;

        // 自愈：按钮缺失（对话框重建等）则重新注入
        if(injected == null || !table.getChildren().contains(injected)){
            table.row();
            injected = table.button(bundle("tri.settings.resetmail"), Icon.trash,
                Styles.flatt, TriSettingsDataButton::confirmReset).marginLeft(4).get();
        }
    }

    /** 弹确认框；确认后清除全部邮件的已读/已收取状态并落盘。 */
    private static void confirmReset(){
        Vars.ui.showConfirm(bundle("tri.settings.resetmail"), bundle("tri.settings.resetmail.confirm"),
            () -> {
                for(TriMail m : TriInbox.all()){
                    Core.settings.remove(TriInbox.READ_PREFIX + m.key);
                    Core.settings.remove(TriInbox.READ_PREFIX + m.key + "-time");
                    Core.settings.remove(TriInbox.ATTACH_PREFIX + m.key);
                }
                Core.settings.forceSave();
                Vars.ui.showInfoFade(bundle("tri.settings.resetmail.done"));
            });
    }

    /** 在场景树中递归查找标题为 settings.data（“数据”）且处于显示状态的对话框。 */
    private static Dialog findDataDialog(Group group){
        for(Element e : group.getChildren()){
            if(e instanceof Dialog){
                Dialog d = (Dialog)e;
                if(d.isShown() && bundle("settings.data").equals(d.title.getText().toString())){
                    return d;
                }
            }
            if(e instanceof Group){
                Dialog d = findDataDialog((Group)e);
                if(d != null) return d;
            }
        }
        return null;
    }

    /** 在数据对话框内容表中，查找包含“清除数据”按钮的那个按钮表。 */
    private static Table findButtonTable(Dialog dialog){
        for(Element e : dialog.cont.getChildren()){
            if(e instanceof Table){
                Table t = (Table)e;
                for(Element c : t.getChildren()){
                    if(c instanceof TextButton && bundle("settings.cleardata").equals(((TextButton)c).getText().toString())){
                        return t;
                    }
                }
            }
        }
        return null;
    }

    /** bundle 键取值，缺失时回退到键名本身。 */
    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
