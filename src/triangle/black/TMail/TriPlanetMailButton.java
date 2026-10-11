package triangle.black.TMail;

import arc.Core;
import arc.Events;
import arc.scene.ui.TextButton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.ui.dialogs.PlanetDialog;

/**
 * 在战役星球界面（PlanetDialog）底栏新增“收件箱”按钮，点击打开 TriMailDialog。
 *
 * 注意：PlanetDialog.rebuildButtons() 会在切换行星/重绘时清空底栏（buttons 表），
 * 故这里用逐帧 Trigger.update 检测：只要星球界面处于显示状态且按钮缺失，就重新注入，
 * 并在每帧刷新未读角标。
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
            button = new TextButton(bundle("tri.mail.button"));
            button.clicked(() -> TriMailDialog.show());
            planet.buttons.add(button).pad(6);
        }

        // 每帧刷新未读角标
        int unread = TriInbox.unreadCount();
        String label = bundle("tri.mail.button");
        button.setText(unread > 0 ? label + " [accent](" + unread + ")" : label);
    }

    /** bundle 键取值，缺失时回退到键名本身。 */
    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
