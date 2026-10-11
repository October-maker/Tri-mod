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
 * 故这里用逐帧 Trigger.update 检测：只要星球界面处于显示状态且按钮缺失，就重新注入，
 * 并在每帧刷新未读角标。
 *
 * 按钮用原版同款 button(文本, 图标, 样式, 回调) 构造，chat 图标与文本间距与原版其他
 * 带图标按钮一致；样式用默认瞬时按钮（按下显示、松开复原）。
 */
public class TriPlanetMailButton{

    private static TextButton button;
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

        // 确保按钮在底栏中（rebuildButtons() 可能已将其清除）
        if(button == null || !planet.buttons.getChildren().contains(button)){
            button = planet.buttons.button(bundle("tri.mail.button"), Icon.chat,
                Styles.defaultt, () -> TriMailDialog.show()).size(150f, 48f).pad(6).get();
        }

        // 每帧刷新未读角标
        int unread = TriInbox.unreadCount();
        String base = bundle("tri.mail.button");
        button.getLabel().setText(unread > 0 ? base + " [accent](" + unread + ")" : base);
    }

    /** bundle 键取值，缺失时回退到键名本身。 */
    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
