package triangle.black;

import arc.*;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.geom.Vec2;
import arc.scene.actions.Actions;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
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

    /** 拼合入场：各分帧从屏幕外滑入屏幕中央的时长（秒），先快后慢（pow2Out）。 */
    private static final float ASSEMBLE_DURATION = 1.0f;
    /** 分帧起始位置超出屏幕边缘的距离（像素），保证贴图出现时完全在屏幕外（不露出）。 */
    private static final float START_MARGIN = 40f;
    /** 滑动发生的时机（秒）：进程进行到该时刻时，TriLogo 覆盖并替代组合体，同时滑向欢迎页 logo 位置。 */
    private static final float SLIDE_AT = 1.5f;
    /** TriLogo 从屏幕中央滑动到欢迎页 logo 位置的时长（秒），先慢后快（pow2In）。 */
    private static final float FLY_TO_DIALOG_DURATION = 0.6f;

    /** 是否已向设置界面添加“三角”设置页（防止 ClientLoadEvent 重复触发导致重复添加）。 */
    private static boolean settingsAdded = false;

    private TriSetting(){}

    /**
     * 注册启动弹窗与设置页。
     * 在 {@code triangle} 主类构造中调用一次即可。
     */
    public static void init(){
        // 启动欢迎弹窗：受 STARTUP_DIALOG_KEY 控制（默认显示）。
        // 流程（伪骨骼拼合入场）：进入游戏后，5 张 TriLogo 分帧从不同方位（先快后慢、不旋转、起点屏幕外）用 0.5 秒滑向屏幕中央拼成组合体；
        // 组合体保持展示，进程进行到 1.5 秒（滑动时机）时 TriLogo 覆盖并替代组合体，先慢后快滑向欢迎页 TriLogo 位置；
        // 到达后显示欢迎界面内容。关闭后可从 设置→三角 页重新开启，或在弹窗内勾选“下次启动不再显示”。
        Events.on(ClientLoadEvent.class, e -> {
            if(!Core.settings.getBool(STARTUP_DIALOG_KEY, true)) return;

            String remind = Core.bundle.get("remind");
            String open = Core.bundle.get("began");
            String stop = Core.bundle.get("end");
            String word = Core.bundle.get("login");
            String get = Core.bundle.get("Iknow");

            // 入场动画与欢迎页共用的 TriLogo 贴图（sprites/ui/TriLogo/TriLogo.png，atlas 键 triangle-TriLogo）
            TextureRegion triLogo = TriIcon.tex("TriLogo");

            // ============ 1) 先构建欢迎弹窗（暂不 show），预布局求出弹窗内 TriLogo 的目标位置 ============
            // 注意：logo 单元格必须带 .row()，与下方文案、开关、按钮各自独占一行，竖直排列（否则 logo 与文案左右并排）
            BaseDialog dialog = new BaseDialog("Triangle");
            Image logoImage = new Image(triLogo);
            dialog.cont.add(logoImage).pad(20f).row();
            dialog.cont.add(open +"\n" + word + "\n" + remind + "\n" + stop).growX().wrap().width(720).maxWidth(730).pad(4).row();

            // “下次启动不再显示”开关：写入设置 STARTUP_DIALOG_KEY 并持久化
            dialog.cont.row();
            CheckBox check = new CheckBox(Core.bundle.get("tri.startup.dialog.hide"));
            check.update(() -> check.setChecked(!Core.settings.getBool(STARTUP_DIALOG_KEY, true)));
            check.changed(() -> Core.settings.put(STARTUP_DIALOG_KEY, !check.isChecked()));
            dialog.cont.add(check).pad(4).row();

            // 与“知道了”按钮样式一致（都用标准按钮样式 Styles.defaultt，带按钮背景），
            // 置于其上方，两按钮尺寸统一；图标使用模组自带 Tmail.png（m 小写规范）
            TextButton mail = new TextButton(Core.bundle.get("tri.mail.button"), Styles.defaultt);
            mail.clearChildren();
            // 图标与文字之间留出恰当间距（与 TriMainMenuButton 构造一致）
            mail.add(new Image(TriIcon.tex("Tmail"))).size(32f).padRight(8);
            mail.add(mail.getLabel());
            mail.clicked(TriMainMenuButton::showInfo);
            dialog.cont.add(mail).size(150f, 50f).pad(4).row();
            dialog.cont.button(get, dialog::hide).size(150f, 50f);

            // 临时挂入场景完成布局后读取 logo 中心的世界坐标，再移除（同一帧内完成，不会闪屏）
            Core.scene.add(dialog);
            dialog.pack();
            dialog.setPosition((Core.graphics.getWidth() - dialog.getWidth()) / 2f, (Core.graphics.getHeight() - dialog.getHeight()) / 2f);
            dialog.validate();
            Vec2 target = logoImage.localToStageCoordinates(new Vec2(logoImage.getWidth() / 2f, logoImage.getHeight() / 2f));
            dialog.remove();

            // ============ 2) 伪骨骼拼合入场：5 张 TriLogo 分帧从不同方位滑入屏幕中央 ============
            // 全屏透明无边框 Table 作容器（layout 关闭、手动定位）；子图片坐标即屏幕坐标
            Table intro = new Table();
            intro.touchable = Touchable.disabled; // 透明且无边框，不拦截主菜单点击
            Core.scene.add(intro);
            intro.setFillParent(true);
            intro.setLayoutEnabled(false);

            float w = triLogo.width, h = triLogo.height; // 各帧与 TriLogo 同尺寸 1000x500（arc TextureRegion 用公开字段）
            float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
            float cx = sw / 2f, cy = sh / 2f; // 屏幕中央（拼合终点）

            // 分帧起点（中心坐标：整体位于屏幕外，带 START_MARGIN 边距保证完全不露出）：
            // TriLogo-1：右上角外；TriLogo-2：左下角外；TriLogo-3：正上方外；TriLogo-4：正下方外；TriLogo-5：正右外
            // 贴图只平移不旋转（无旋转、无倾斜），直接由屏幕外滑向中央
            float[] sx = { sw + w / 2f + START_MARGIN, -w / 2f - START_MARGIN, cx, cx, sw + w / 2f + START_MARGIN };
            float[] sy = { sh + h / 2f + START_MARGIN, -h / 2f - START_MARGIN, sh + h / 2f + START_MARGIN, -h / 2f - START_MARGIN, cy };
            String[] frames = { "TriLogo-1", "TriLogo-2", "TriLogo-3", "TriLogo-4", "TriLogo-5" };
            Image[] pieces = new Image[frames.length];

            // 所有帧同时出发、同时到位（同一时长），先快后慢（pow2Out）滑入屏幕中央；不旋转只移动
            for(int i = 0; i < frames.length; i++){
                Image f = new Image(TriIcon.tex(frames[i]));
                f.setSize(w, h);
                intro.add(f);
                f.setPosition(sx[i] - w / 2f, sy[i] - h / 2f); // 左下角定位，中心落在起点
                f.actions(Actions.moveTo(cx - w / 2f, cy - h / 2f, ASSEMBLE_DURATION, Interp.pow2Out));
                pieces[i] = f;
            }

            // ============ 3) 组合体拼合完成后保持展示，进程进行到 SLIDE_AT 时刻：TriLogo 覆盖替代组合体并滑向欢迎页 ============
            // 单条容器级定时：等 5 帧快速拼合（0.5s）到位后组合体停留展示，到 1.5s 执行覆盖+滑动（替代时序）
            intro.actions(Actions.delay(SLIDE_AT), Actions.run(() -> {
                // 后加入容器 → 绘制在最上层，盖住组合体
                Image cover = new Image(triLogo);
                cover.setSize(w, h);
                intro.add(cover);
                cover.setPosition(cx - w / 2f, cy - h / 2f);
                // 隐藏组合体（移除 5 帧），由 TriLogo 替代时序
                for(Image p : pieces) p.remove();

                // 先慢后快（pow2In 加速）滑动到欢迎页 TriLogo 位置，到达后显示欢迎内容
                // moveTo 的 x/y 是左下角坐标，故减去半宽/半高使中心落在弹窗 logo 中心
                cover.actions(
                    Actions.moveTo(target.x - w / 2f, target.y - h / 2f, FLY_TO_DIALOG_DURATION, Interp.pow2In),
                    Actions.run(() -> {
                        intro.remove(); // 入场动画结束，移除开场容器
                        dialog.show();  // 显示欢迎界面（弹窗内 TriLogo 与动画终点同位置，视觉无缝衔接）
                    })
                );
            }));
        });

        // 在游戏设置界面新增“三角”设置页，含“启动时显示欢迎界面”开关，可随时重新打开弹窗
        Events.on(ClientLoadEvent.class, e -> {
            if(settingsAdded) return;
            settingsAdded = true;
            // 设置→三角 分类：图标置于分类标题前（与原版“游戏”等分类前的齿轮图标排版一致）
            Vars.ui.settings.addCategory(Core.bundle.get("triSettings"),
                new TextureRegionDrawable(TriIcon.tex("TriSetting")), t -> {
                // checkPref 的第一参既是“设置存储键”又是“bundle 显示文案键”。
                // STARTUP_DIALOG_KEY(=tri.startupDialogToggle) 即 bundle 键，故显示文本自动本地化；
                // 且与弹窗内勾选框读写同一个键，状态保持同步（键为跨语言稳定的标识符）。
                t.checkPref(STARTUP_DIALOG_KEY, true);
            });
        });
    }
}
