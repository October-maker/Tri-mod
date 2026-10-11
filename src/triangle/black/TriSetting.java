package triangle.black;

import arc.*;
import arc.scene.ui.CheckBox;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.ui.dialogs.*;

/**
 * 三角模组的设置逻辑集中入口。
 * 目前在游戏启动时控制“欢迎弹窗”是否显示，并提供设置界面开关。
 */
public class TriSetting{

    /** 启动欢迎弹窗的持久化开关（存于 Core.settings），默认显示。 */
    public static final String STARTUP_DIALOG_KEY = "tri-startup-dialog";

    /** 是否已向设置界面添加“三角”设置页（防止 ClientLoadEvent 重复触发导致重复添加）。 */
    private static boolean settingsAdded = false;

    private TriSetting(){}

    /**
     * 注册启动弹窗与设置页。
     * 在 {@code triangle} 主类构造中调用一次即可。
     */
    public static void init(){
        // 启动欢迎弹窗：受 STARTUP_DIALOG_KEY 控制（默认显示）。
        // 关闭后可从 设置→三角 页重新开启，或在弹窗内勾选“下次启动不再显示”。
        Events.on(ClientLoadEvent.class, e -> {
            if(!Core.settings.getBool(STARTUP_DIALOG_KEY, true)) return;

            String remind = Core.bundle.get("remind");
            String open = Core.bundle.get("began");
            String stop = Core.bundle.get("end");
            String word = Core.bundle.get("login");
            String get = Core.bundle.get("Iknow");
            //读取bundle里面的字符串
            Time.runTask(1f, () -> {
                BaseDialog dialog = new BaseDialog("Triangle");
                dialog.cont.image(Core.atlas.find("logo")).pad(20f).row();
                dialog.cont.add(open +"\n" + word + "\n" + remind + "\n" + stop).growX().wrap().width(720).maxWidth(730).pad(4).row();

                // “下次启动不再显示”开关：写入设置 STARTUP_DIALOG_KEY 并持久化
                dialog.cont.row();
                CheckBox check = new CheckBox(Core.bundle.get("tri.startup.dialog.hide"));
                check.update(() -> check.setChecked(!Core.settings.getBool(STARTUP_DIALOG_KEY, true)));
                check.changed(() -> Core.settings.put(STARTUP_DIALOG_KEY, !check.isChecked()));
                dialog.cont.add(check).pad(4).row();

                dialog.cont.button(get, dialog::hide).size(100f, 50f);
                dialog.show();
            });
        });

        // 在游戏设置界面新增“三角”设置页，含“启动时显示欢迎界面”开关，可随时重新打开弹窗
        Events.on(ClientLoadEvent.class, e -> {
            if(settingsAdded) return;
            settingsAdded = true;
            Vars.ui.settings.addCategory(Core.bundle.get("triSettings"), t -> {
                t.checkPref(STARTUP_DIALOG_KEY, true);
            });
        });
    }
}
