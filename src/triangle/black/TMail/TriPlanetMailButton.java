package triangle.black.TMail;

import arc.Core;
import arc.Events;
import arc.scene.ui.TextButton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.PlanetDialog;

/**
 * 在战役星球界面（PlanetDialog）底栏新增“收件箱”按钮，点击打开 TriMailDialog。
 *
 * 注意：PlanetDialog.rebuildButtons() 会在切换行星/重绘时清空底栏（buttons 表），
 * 故这里在 Trigger.update 中自愈：星球界面显示期间每帧只做 O(1) 的 hasParent()
 * 检查，按钮被清空（hasParent() == false）才重新注入；未读角标仅在未读数变化时
 * 更新文本，避免每帧重建 Label。
 *
 * 按钮用原版同款 button(文本, 图标, 样式, 回调) 构造，chat 图标与文本间距与原版其他
 * 带图标按钮一致；样式用默认瞬时按钮（按下显示、松开复原）。
 */
public class TriPlanetMailButton{

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

        // O(1) 自愈：按钮被 rebuildButtons() 清空/底栏重建后 hasParent() 为 false，重新注入
        if(button == null || !button.hasParent()){
            button = planet.buttons.button(bundle("tri.mail.button"), Icon.chat,
                Styles.defaultt, () -> TriMailDialog.show()).size(150f, 48f).pad(6).get();
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

    /** bundle 键取值，缺失时回退到键名本身。 */
    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
