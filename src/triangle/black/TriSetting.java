package triangle.black;

import arc.*;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.TextButton;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.*;
import triangle.black.TMail.TriMainMenuButton;

/**
 * 三角模组的设置逻辑集中入口。
 * 目前在游戏启动时控制“欢迎弹窗”是否显示，并提供设置界面开关。
 */
public class TriSetting{

    /**
     * 欢迎弹窗开关的设置存储键。
     * 设置页 checkPref 会按原版习惯以 "setting.&lt;name&gt;.name" 为 bundle 显示键查文本
     * （缺失则回落为原始键名），故需在 bundle 中提供 setting.tri.startupDialogToggle.name。
     * 该键同时作为设置页 checkbox 与启动弹窗内勾选框共用的设置存储键，状态保持同步。
     * 注意：不要用 Core.bundle.get(...) 在此处静态初始化取文本——此时模组 bundle 尚未并入。
     */
    public static final String STARTUP_DIALOG_KEY = "tri.startupDialogToggle";

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

                // 与“知道了”按钮样式一致（都用标准按钮样式 Styles.defaultt，带按钮背景），
                // 置于其上方，两按钮尺寸统一
                TextButton mail = new TextButton(Core.bundle.get("tri.mail.button"), Styles.defaultt);
                mail.clearChildren();
                // 图标与文字之间留出恰当间距（与 TriMainMenuButton 构造一致）
                mail.add(new Image(Icon.chat)).size(Icon.chat.imageSize()).padRight(8);
                mail.add(mail.getLabel());
                mail.clicked(TriMainMenuButton::showInfo);
                dialog.cont.add(mail).size(150f, 50f).pad(4).row();
                dialog.cont.button(get, dialog::hide).size(150f, 50f);

                dialog.show();
            });
        });

        // 在游戏设置界面新增“三角”设置页，含“启动时显示欢迎界面”开关，可随时重新打开弹窗
        Events.on(ClientLoadEvent.class, e -> {
            if(settingsAdded) return;
            settingsAdded = true;
            Vars.ui.settings.addCategory(Core.bundle.get("triSettings"), t -> {
                // checkPref 的第一参既是“设置存储键”又是“bundle 显示文案键”。
                // STARTUP_DIALOG_KEY(=tri.startupDialogToggle) 即 bundle 键，故显示文本自动本地化；
                // 且与弹窗内勾选框读写同一个键，状态保持同步（键为跨语言稳定的标识符）。
                t.checkPref(STARTUP_DIALOG_KEY, true);
            });
        });
    }
}
