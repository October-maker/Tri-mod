package triangle.black.TMail;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.TextButton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.PlanetDialog;
import triangle.black.TriDraw;

/**
 * 在战役星球界面（PlanetDialog）底栏“科技树”按钮右侧并排添加“收件箱”按钮，点击打开 TriMailDialog。
 *
 * 注入原版底栏表（planet.buttons，rebuildButtons() 的布局为
 * [返回] … [sectorTop] … [科技树]，追加的 cell 落在最右，即科技树右侧）。
 * cell 必须带 .bottom()（与原版 addBack/addTech 一致）：选中区块后中间 sectorTop 表
 * 变高、底栏行高随之变化，bottom 对齐保证按钮始终贴住行底，避免无 bottom
 * 对齐、cell 默认居中时随行高变化而“向上移动”。
 *
 * 自愈：rebuildButtons() 用 buttons.clearChildren() 清空重建、PlanetDialog.show()/
 * viewPlanet() 用 clearChildren() 重建整树——被移除按钮的 parent 链断裂。这里每帧沿
 * parent 链上溯到 PlanetDialog 做可达性检查（不能用 hasParent()：按钮被清时其 parent
 * 仍指向孤儿表，会误判恒真），丢失即重新注入；未读角标仅在未读数变化时更新文本。
 */
public class TriPlanetMailButton{

    /** 当前注入的底栏按钮；被原版重建清掉后重新创建。 */
    private static TextButton button;
    /** 上次展示的未读数，用于仅在数值变化时更新角标文本。 */
    private static int lastUnread = -1;
    private static boolean registered = false;

    private TriPlanetMailButton(){}

    /** 注册（在 triangle 主类构造中调用一次）。 */
    public static void init(){
        if(registered) return;
        registered = true;
        Events.run(EventType.Trigger.update, TriPlanetMailButton::update);
    }

    private static void update(){
        PlanetDialog planet = Vars.ui.planet;
        if(!planet.isShown()) return;

        // 自愈：按钮被 rebuildButtons()/setup() 清掉（parent 链断裂）后，重新注入底栏最右
        if(button == null || !attachedTo(button, planet)){
            // 追加为底栏最后一个 cell（科技树右侧），与原版底栏按钮同款底部对齐，
            // 行高随 sectorTop 变化时按钮贴底不动
            button = planet.buttons.button(bundle("tri.mail.button"),
                new TextureRegionDrawable(TriDraw.tex("Tmail")),
                Styles.defaultt, () -> TriMailDialog.show()).size(150f, 48f).pad(2).bottom().get();
            lastUnread = -1; // 强制刷新角标
        }

        // 角标：仅未读数变化时更新文本，避免每帧重建 Label
        int unread = TriInbox.unreadCount();
        if(unread != lastUnread){
            lastUnread = unread;
            String base = bundle("tri.mail.button");
            button.getLabel().setText(unread > 0 ? base + " [accent](" + unread + ")" : base);
        }
    }

    /** 沿 parent 链检查元素是否仍是 planet 的后代（PlanetDialog 重建后失效）。 */
    private static boolean attachedTo(Element e, PlanetDialog planet){
        for(Element p = e; p != null; p = p.parent){
            if(p == planet) return true;
        }
        return false;
    }

    /** bundle 键取值，缺失时回退到键名本身。 */
    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
