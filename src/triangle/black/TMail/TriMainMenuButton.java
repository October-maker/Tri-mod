package triangle.black.TMail;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

/**
 * 在主菜单（MenuFragment）添加 TMail 按钮入口，点击显示固定的模组资讯。
 *
 * v150 的主菜单在 UI.init() 时一次性 build（读取 desktopButtons/customButtons 渲染），
 * mod 加载早于 UI，无法在其 build 前注入；因此这里逐帧自愈：
 * 在已渲染的主菜单场景树（Vars.ui.menuGroup）中递归查找名为 "buttons" 的按钮表，
 * 用原版同款风格（Styles.flatToggleMenut + Icon.chat）把按钮直接注入该表。
 */
public class TriMainMenuButton{

    private static TextButton injected;

    private TriMainMenuButton(){}

    /** 注册（在 triangle 主类构造中调用一次）。 */
    public static void init(){
        Events.run(EventType.Trigger.update, TriMainMenuButton::update);
    }

    private static void update(){
        if(Vars.ui == null || Vars.ui.menuGroup == null) return;

        Table buttons = findButtons(Vars.ui.menuGroup);
        if(buttons == null) return;

        // 自愈：若按钮已被清空（菜单重建等），重新注入
        // 用 cleart：透明背景（融入其他半透明菜单按钮，不显突兀）+ 瞬时按下（非 toggle）
        if(injected == null || !buttons.getChildren().contains(injected)){
            buttons.row(); // 独占新一行
            injected = (TextButton)buttons.button(bundle("tri.mail.button"), Icon.chat,
                Styles.cleart, TriMainMenuButton::showInfo).get();
        }
    }

    /** 在主菜单场景树中递归查找名为 "buttons" 的按钮表。 */
    private static Table findButtons(Group group){
        for(Element e : group.getChildren()){
            if(e instanceof Table){
                Table t = (Table)e;
                if("buttons".equals(t.name)) return t;
            }
            if(e instanceof Group){
                Table t = findButtons((Group)e);
                if(t != null) return t;
            }
        }
        return null;
    }

    /** 打开主菜单 TMail：显示模组资讯邮件（与战役星球收件箱同款邮件式界面）。 */
    public static void showInfo(){
        TriMailDialog.show(false);
    }

    /** bundle 键取值，缺失时回退到键名本身。 */
    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
