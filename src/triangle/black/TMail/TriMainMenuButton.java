package triangle.black.TMail;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Image;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.ui.Styles;
import triangle.black.TriDraw;

/**
 * 在主菜单（MenuFragment）添加 TMail 按钮入口，点击显示固定的模组资讯。
 *
 * 原版主菜单在 UI.init() 时一次性 build（读取 desktopButtons/customButtons 渲染），
 * mod 加载早于 UI，无法在其 build 前注入；因此这里自愈注入：
 * 常态下每帧只做 O(1) 的 hasParent() 检查（按钮仍在场景树中则无事可做）；
 * 仅当按钮被菜单重建移除（hasParent() == false）或缓存表失效时，
 * 才在场景树中递归查找名为 "buttons" 的按钮表并重新注入。
 * 递归遍历由此从"每帧"降为"按钮丢失时"。
 */
public class TriMainMenuButton{

    /** 当前注入的按钮实例；用于判断是否仍需注入。 */
    private static TextButton injected;
    /** 缓存已注入的按钮表；仅在表被移除（菜单重建）时才重新扫描场景树。 */
    private static Table buttons;

    private TriMainMenuButton(){}

    /** 注册（在 triangle 主类构造中调用一次）。 */
    public static void init(){
        Events.run(EventType.Trigger.update, TriMainMenuButton::update);
    }

    private static void update(){
        if(Vars.ui == null || Vars.ui.menuGroup == null) return;

        // O(深度) 自愈：按钮仍可达于主菜单场景树则无需任何操作。
        // 注意不能用 hasParent()：buildDesktop() 的 container.clear() 会把整个 buttons 表
        // 移出场景树，此时按钮的 parent 仍指向该孤儿表（非 null），hasParent() 会误判按钮
        // 仍有效而永不重注入，导致按钮消失。
        if(injected != null && attachedToMenu(injected)) return;

        // 缓存表失效（表已被移出菜单树）才递归扫描
        if(buttons == null || !attachedToMenu(buttons)){
            buttons = findButtons(Vars.ui.menuGroup);
            if(buttons == null) return;
        }

        // 用 cleart：透明背景（融入其他半透明菜单按钮，不显突兀）+ 瞬时按下（非 toggle）
        buttons.row(); // 独占新一行

        TextButton b = new TextButton(bundle("tri.mail.button"), Styles.cleart);
        b.clearChildren();
        // 图标与文字之间留出恰当间距（arc 的 button(text,icon,...) 重载内部无右间距）
        b.add(new Image(TriDraw.tex("Tmail"))).size(32f).padRight(8);
        b.add(b.getLabel());
        b.clicked(TriMainMenuButton::showInfo);

        buttons.add(b);
        injected = b;
    }

    /** 沿 parent 链检查元素是否仍可达于主菜单场景树（menuGroup 锚点）。 */
    private static boolean attachedToMenu(Element e){
        for(Element p = e; p != null; p = p.parent){
            if(p == Vars.ui.menuGroup) return true;
        }
        return false;
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

    /** 打开主菜单 TMail：立即进入收件箱页面并播放 TmailLogo 入场动画，动画结束后卡片滑入。 */
    public static void showInfo(){
        TriMailDialog.showWithIntro(false);
    }

    /** bundle 键取值，缺失时回退到键名本身。 */
    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
