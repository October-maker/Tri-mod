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

    /** 拼合入场：各分帧从屏幕外滑入屏幕中央的飞行时长（秒），先快后慢（pow2Out）。 */
    private static final float ASSEMBLE_DURATION = 0.5f;
    /** 从左到右依次到位的总延后量（秒）：最开始的到位时间与最末的到位时间之差（相邻帧均分该差值）。
     *  各帧到位时刻 = 出发延迟 + ASSEMBLE_DURATION，故最末帧在 ASSEMBLE_DURATION + STAGGER_SPAN 时刻到位。 */
    private static final float STAGGER_SPAN = 0.8f;
    /** 分帧起始位置超出屏幕边缘的距离（像素），保证贴图出现时完全在屏幕外（不露出）。 */
    private static final float START_MARGIN = 40f;
    /** TriLogo-1 滑入路径相对水平的角度（度）；TriLogo-2 与其中心对称（取负），其余分帧保持原方位。 */
    private static final float SLIDE_ANGLE = 60f;
    /** 滑动发生的时机（秒）：进程进行到该时刻时，TriLogo 盖住组合体并开始滑向欢迎页 logo 位置。
     *  注意：该时刻必须 ≥ 组合体最末到位时刻（ASSEMBLE_DURATION + STAGGER_SPAN），
     *  否则组合体在 TriLogo 出现前消失，屏幕中央会出现几帧空白闪动（历史 bug）。 */
    private static final float SLIDE_AT = ASSEMBLE_DURATION + STAGGER_SPAN + 0.25f;
    /** TriLogo 从屏幕中央滑动到欢迎页 logo 位置的时长（秒），先慢后快（pow2In）。 */
    private static final float FLY_TO_DIALOG_DURATION = 0.3f;

    /** 是否已向设置界面添加“三角”设置页（防止 ClientLoadEvent 重复触发导致重复添加）。 */
    private static boolean settingsAdded = false;

    private TriSetting(){}

    /**
     * 注册启动弹窗与设置页。
     * 在 {@code triangle} 主类构造中调用一次即可。
     */
    public static void init(){
        // 启动欢迎弹窗：受 STARTUP_DIALOG_KEY 控制（默认显示）。
        // 流程（伪骨骼拼合入场）：进入游戏后，5 张 TriLogo 分帧从不同方位（先快后慢、不旋转、起点屏幕外）滑向屏幕中央拼成组合体；
        // TriLogo-1 沿相对水平 60° 斜线滑入，TriLogo-2 与其中心对称（-60°）；
        // 各帧到位时间从左到右依次延后（最始与最末之差 = STAGGER_SPAN，形成从左到右组合效果）；组合体全部到位后保持展示；
        // 进程进行到滑动时机（SLIDE_AT，≥ 最末到位时刻）时 TriLogo 覆盖并替代组合体，先慢后快滑向欢迎页位置；
        // TriLogo 到达欢迎页位置后显示欢迎界面内容。关闭后可从 设置→三角 页重新开启，或在弹窗内勾选“下次启动不再显示”。
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

            // 分帧起点（中心坐标）：TriLogo-1 从右上外沿相对水平 60° 斜线滑向中央（滑入路径角 SLIDE_ANGLE）；
            // TriLogo-2 与其中心对称（-60°，左下外镜像）；TriLogo-3/4/5 保持正上/正下/正右原方位。
            // 贴图只平移不旋转（无旋转、无倾斜），沿各自路径由屏幕外滑向中央。
            // 60° 起点沿斜线取在屏幕外足够远处（d），保证整张贴图完全不露出屏幕。
            double rad = Math.toRadians(SLIDE_ANGLE);
            float cos = (float)Math.cos(rad), sin = (float)Math.sin(rad);
            // d 取“整张在屏幕右侧外”与“整张在屏幕上侧外”两者所需距离的较大者（带 START_MARGIN 安全边距）
            float d = Math.max((sw + w + 2f * START_MARGIN) / (2f * cos), (sh + h + 2f * START_MARGIN) / (2f * sin));
            float[] sx = { cx + d * cos, cx - d * cos, cx, cx, sw + w / 2f + START_MARGIN };
            float[] sy = { cy + d * sin, cy - d * sin, sh + h / 2f + START_MARGIN, -h / 2f - START_MARGIN, cy };
            String[] frames = { "TriLogo-1", "TriLogo-2", "TriLogo-3", "TriLogo-4", "TriLogo-5" };
            Image[] pieces = new Image[frames.length];

            // 各帧飞行时长一致（先快后慢 pow2Out），但出发时刻从左到右依次延后，实现从左到右依次到位：
            // 实测各帧内容中心 x（TriLogo-1..5 = 149/387/637/826/870），frames 数组天然即从左到右顺序；
            // 第 i 帧出发延迟 = i * STAGGER_SPAN / (帧数-1)，到位时刻 = 出发延迟 + ASSEMBLE_DURATION 依次延后，
            // 最开始的到位时间与最末的到位时间之差恰为 STAGGER_SPAN
            for(int i = 0; i < frames.length; i++){
                Image f = new Image(TriIcon.tex(frames[i]));
                f.setSize(w, h);
                intro.add(f);
                f.setPosition(sx[i] - w / 2f, sy[i] - h / 2f); // 左下角定位，中心落在起点
                float startDelay = i * STAGGER_SPAN / (frames.length - 1);
                f.actions(
                    Actions.delay(startDelay),
                    Actions.moveTo(cx - w / 2f, cy - h / 2f, ASSEMBLE_DURATION, Interp.pow2Out)
                );
                pieces[i] = f;
            }

            // ============ 3) 组合体全部到位后保持展示，进程进行到 SLIDE_AT 时刻：TriLogo 覆盖替代组合体并滑向欢迎页 ============
            // 覆盖、隐藏组合体、开始滑动必须在同一时刻执行（单条定时）：若先移除组合体再等 TriLogo 出现，
            // 期间屏幕中央会空白闪动（历史 bug）；SLIDE_AT ≥ 最末到位时刻，组合体完整展示后才被替换。
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
