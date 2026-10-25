package triangle.black;

import arc.*;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.geom.Vec2;
import arc.scene.actions.Actions;
import arc.scene.event.Touchable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.ui.Styles;
import triangle.black.TMail.TriMainMenuButton;

/**
 * 三角模组启动欢迎界面（入场动画 + 欢迎内容）的唯一入口。
 * 由 {@code triangle} 主类构造调用 {@link #init()} 注册，与 TriSetting（设置页）解耦。
 */
public class TriWelcome{

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
     *  否则组合体在 TriLogo 出现前消失，屏幕中央会出现数帧空白闪动。 */
    private static final float SLIDE_AT = ASSEMBLE_DURATION + STAGGER_SPAN + 0.15f;
    /** TriLogo 从屏幕中央滑动到欢迎页 logo 位置的时长（秒），先慢后快（pow2In）。 */
    private static final float FLY_TO_DIALOG_DURATION = 0.3f;
    /** 组合体放大倍数：各分帧滑入前即处于该放大样式（组合全程放大），
     *  移动到欢迎页停靠位时随移动缩放回 1（“砸入面板”效果）。独立变量，可按观感调整。 */
    private static final float ASSEMBLE_SCALE = 1.25f;

    private TriWelcome(){}

    /**
     * 注册启动欢迎界面（受 STARTUP_DIALOG_KEY 控制，默认显示）。
     * 在 {@code triangle} 主类构造中调用一次即可。
     */
    public static void init(){
        // 启动欢迎界面：受 STARTUP_DIALOG_KEY 控制（默认显示）。
        // 流程（伪骨骼拼合入场）：进入游戏后，5 张 TriLogo 分帧从不同方位（先快后慢、不旋转、起点屏幕外）滑向屏幕中央拼成组合体；
        // TriLogo-1 沿相对水平 60° 斜线滑入，TriLogo-2 与其中心对称（-60°）；
        // 各帧到位时间从左到右依次延后（最始与最末之差 = STAGGER_SPAN，形成从左到右组合效果）；
        // 各分帧滑入前即处于放大样式（ASSEMBLE_SCALE），组合体以放大状态成型展示；
        // 进程进行到滑动时机（SLIDE_AT，≥ 最末到位时刻）时 TriLogo 覆盖并替代组合体，
        // 以放大状态先慢后快滑向停靠位（欢迎界面 logo 槽位），移动过程中缩放回正常大小（砸入面板效果）；
        // TriLogo 到位后保留其所在 table 不消失，在其下方依次添加文本 table（打字机效果）与按钮 table（第一行“下次启动不再显示”开关 + 按钮整组滑入）。
        // 关闭后可从 设置→三角 页重新开启。
        Events.on(ClientLoadEvent.class, e -> {
            if(!Core.settings.getBool(STARTUP_DIALOG_KEY, true)) return;

            String remind = Core.bundle.get("remind");
            String open = Core.bundle.get("began");
            String stop = Core.bundle.get("end");
            String word = Core.bundle.get("login");
            String get = Core.bundle.get("Iknow");

            // 入场动画与欢迎界面共用的 TriLogo 贴图（assets/TriUI/TriLogo/TriLogo.png，独立纹理加载）
            TextureRegion triLogo = TriDraw.tex("TriLogo/TriLogo");

            // ============ 1) 欢迎界面布局：TriLogo 停靠位（动画“对应位置”）+ 下方文本 table + 按钮 table ============
            float w = triLogo.width, h = triLogo.height; // 各帧与 TriLogo 同尺寸 1000x500（arc TextureRegion 用公开字段）
            float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
            float cx = sw / 2f, cy = sh / 2f; // 屏幕中央（拼合终点）

            // 欢迎界面整体垂直居中排布：TriLogo（停靠）→ 文本 → 按钮行；“下次启动不再显示”开关行位于按钮行上方
            float textW = 720f, textH = 110f;         // 文本 table 尺寸
            float btnW = 150f, btnH = 50f, btnGap = 20f; // 按钮尺寸与行间距
            float checkGap = 8f;      // “下次启动不再显示”开关行高、开关行到按钮行的间距
            float stackH = h + btnGap + textH + btnGap + btnH; // 整个内容栈高度（按钮行位置不变，开关行位于其上方，由按钮 table 承载）
            float topY = (sh + stackH) / 2f;                    // 内容栈顶边（垂直居中）
            float logoCy = topY - h / 2f;                        // TriLogo 停靠中心 y
            float textCy = logoCy - h / 2f - btnGap - textH / 2f; // 文本 table 中心 y
            float btnCy = textCy - textH / 2f - btnGap - btnH / 2f; // 按钮行中心 y（按钮 table 以按钮行（第二行）为基准定位）
            Vec2 target = new Vec2(cx, logoCy);                  // 动画终点：TriLogo 中心停靠位

            // ============ 2) 伪骨骼拼合入场：5 张 TriLogo 分帧从不同方位滑入屏幕中央 ============
            // 全屏透明无边框 Table 作容器（layout 关闭、手动定位）；子图片坐标即屏幕坐标
            Table intro = new Table();
            intro.touchable = Touchable.disabled; // 动画期间透明且无边框，不拦截主菜单点击；欢迎内容出现后改为可交互（见第 3 段）
            Core.scene.add(intro);
            intro.setFillParent(true);
            intro.setLayoutEnabled(false);

            // 灰黑色背景：最先添加 → 绘制在最底层，置于所有 table 之下（含分帧组合体、TriLogo、文本、按钮）。
            // 淡黑半透明遮罩，隐约可见主菜单；touchable 关闭避免干扰按钮点击
            Image bg = new Image(Core.atlas.find("white"));
            bg.setSize(sw, sh);
            bg.setColor(0f, 0f, 0f, 0.8f);
            bg.touchable = Touchable.disabled;
            intro.add(bg);

            // 分帧起点（中心坐标）：TriLogo-1 从右上外沿相对水平 60° 斜线滑向中央（滑入路径角 SLIDE_ANGLE）；
            // TriLogo-2 与其中心对称（-60°，左下外镜像）；TriLogo-3/4/5 保持正上/正下/正右原方位。
            // 贴图只平移不旋转（无旋转、无倾斜），沿各自路径由屏幕外滑向中央。
            // 60° 起点沿斜线取在屏幕外足够远处（d），保证整张贴图完全不露出屏幕。
            double rad = Math.toRadians(SLIDE_ANGLE);
            float cos = (float)Math.cos(rad), sin = (float)Math.sin(rad);
            // d 取“整张在屏幕右侧外”与“整张在屏幕上侧外”两者所需距离的较大者（带 START_MARGIN 安全边距）。
            // 各帧滑入前即放大到 ASSEMBLE_SCALE，故按放大后尺寸计算，保证起点完全不露出屏幕
            float as = ASSEMBLE_SCALE;
            float d = Math.max((sw + w * as + 2f * START_MARGIN) / (2f * cos), (sh + h * as + 2f * START_MARGIN) / (2f * sin));
            float[] sx = { cx + d * cos, cx - d * cos, cx, cx, sw + w * as / 2f + START_MARGIN };
            float[] sy = { cy + d * sin, cy - d * sin, sh + h * as / 2f + START_MARGIN, -h * as / 2f - START_MARGIN, cy };
            String[] frames = { "TriLogo/TriLogo-1", "TriLogo/TriLogo-2", "TriLogo/TriLogo-3", "TriLogo/TriLogo-4", "TriLogo/TriLogo-5" };
            Image[] pieces = new Image[frames.length];

            // 各帧飞行时长一致（先快后慢 pow2Out），但出发时刻从左到右依次延后，实现从左到右依次到位：
            // 实测各帧内容中心 x（TriLogo-1..5 = 149/387/637/826/870），frames 数组天然即从左到右顺序；
            // 第 i 帧出发延迟 = i * STAGGER_SPAN / (帧数-1)，到位时刻 = 出发延迟 + ASSEMBLE_DURATION 依次延后，
            // 最开始的到位时间与最末的到位时间之差恰为 STAGGER_SPAN
            for(int i = 0; i < frames.length; i++){
                Image f = new Image(TriDraw.tex(frames[i]));
                f.setSize(w, h);
                f.setOrigin(Align.center); // 缩放围绕图片中心，保证放大/缩回时中心位置不动
                f.setScale(ASSEMBLE_SCALE); // 滑入前即处于放大后的样式，滑入/组合全程保持放大
                intro.add(f);
                f.setPosition(sx[i] - w / 2f, sy[i] - h / 2f); // 左下角定位，中心落在起点
                float startDelay = i * STAGGER_SPAN / (frames.length - 1);
                // 仅平移滑向中央（保持放大），组合体以放大样式成型
                f.actions(
                    Actions.delay(startDelay),
                    Actions.moveTo(cx - w / 2f, cy - h / 2f, ASSEMBLE_DURATION, Interp.pow2Out)
                );
                pieces[i] = f;
            }

            // ============ 3) 组合体全部到位后保持展示，进程进行到 SLIDE_AT 时刻：TriLogo 覆盖替代组合体并滑向停靠位 ============
            // 覆盖、隐藏组合体、开始滑动必须在同一时刻执行（单条定时）：若先移除组合体再等 TriLogo 出现，
            // 期间屏幕中央会空白闪动；SLIDE_AT ≥ 最末到位时刻，保证组合体完整展示后才被替换。
            intro.actions(Actions.delay(SLIDE_AT), Actions.run(() -> {
                // 后加入容器 → 绘制在最上层，盖住组合体
                Image cover = new Image(triLogo);
                cover.setSize(w, h);
                cover.setOrigin(Align.center);
                cover.setScale(ASSEMBLE_SCALE); // 与组合体放大状态一致，砸入停靠位时随移动缩回 1
                intro.add(cover);
                cover.setPosition(cx - w / 2f, cy - h / 2f);
                // 隐藏组合体（移除 5 帧），由 TriLogo 替代时序
                for(Image p : pieces) p.remove();

                // 先慢后快（pow2In 加速）滑动到停靠位（欢迎界面 TriLogo 槽位）；到达后（动画进行到合适的时候）
                // 触发 buildWelcomeContent：展示欢迎内容（文本打字机 + 按钮整组滑入）
                // moveTo 的 x/y 是左下角坐标，故减去半宽/半高使中心落在停靠位中心
                cover.actions(
                    Actions.moveTo(target.x - w / 2f, target.y - h / 2f, FLY_TO_DIALOG_DURATION, Interp.pow2In),
                    Actions.run(() -> buildWelcomeContent(intro, cx, textW, textH, textCy, btnW, btnH, checkGap, btnCy, open, word, remind, stop, get))
                );
                // 与移动并行：缩放回正常大小（先慢后快，落地瞬间完成 → “砸入面板”效果）
                cover.actions(Actions.scaleTo(1f, 1f, FLY_TO_DIALOG_DURATION, Interp.pow2In));
            }));
        });
    }

    /**
     * 在 TriLogo 滑到停靠位后构建欢迎内容：先把 intro 切为可交互（模态），
     * 再在其下方依次添加文本 table（打字机效果）与按钮 table（第一行“下次启动不再显示”开关 + 按钮整组滑入）。
     * 作为独立方法，便于在动画进行到合适的时候（TriLogo 到位时）统一触发。
     *
     * @param intro 欢迎界面容器（TriLogo 所在 table，保留不消失）
     * @param cx 屏幕中心 x（欢迎界面水平居中基准）
     * @param textW 文本 table 宽
     * @param textH 文本 table 高
     * @param textCy 文本 table 中心 y
     * @param btnW 按钮宽
     * @param btnH 按钮高
     * @param checkGap 开关行到按钮行的间距
     * @param btnCy 按钮行中心 y（按钮 table 以第二行（按钮行）为基准定位）
     * @param open 欢迎文案第一段（bundle）
     * @param word 欢迎文案第二段（bundle）
     * @param remind 欢迎文案第三段（bundle）
     * @param stop 欢迎文案第四段（bundle）
     * @param get “知道了”按钮文案（bundle）
     */
    private static void buildWelcomeContent(Table intro, float cx, float textW, float textH, float textCy, float btnW, float btnH, float checkGap, float btnCy, String open, String word, String remind, String stop, String get){
        // TriLogo 到位：intro（TriLogo 所在 table）保留不消失，改为可交互模态（按钮可点击）
        intro.touchable = Touchable.enabled;

        // ---------- 半透明 Triangle 图标：从透明渐变到 0.3 ----------
        float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        TextureRegion triangleTex = TriDraw.tex("TriIcon/Triangle");
        Image triangle = new Image(triangleTex);
        // 设置 Triangle 尺寸（保持原始比例，放大 1.5 倍）
        float triScale = 1.5f;
        triangle.setSize(triangleTex.width * triScale, triangleTex.height * triScale);
        // 居中放置
        triangle.setPosition((sw - triangle.getWidth()) / 2f, (sh - triangle.getHeight()) / 2f);
        // 初始完全透明
        triangle.setColor(1f, 1f, 1f, 0f);
        triangle.touchable = Touchable.disabled;
        intro.add(triangle);
        // 渐变动画：从透明到 0.3 不透明度，时长 0.5 秒
        triangle.actions(Actions.alpha(0.15f, 0.5f, Interp.fade));

        // ---------- 文本 table：显示文本内容并实现打字机效果 ----------
        Table textBox = new Table();
        textBox.setSize(textW, textH);
        textBox.setPosition(cx - textW / 2f, textCy - textH / 2f + 50f);
        // 打字机：复用 TriDraw.typewriter（Time.time + 字符/秒 + update 刷新 substring）
        String full = open + "\n" + word + "\n" + remind + "\n" + stop;
        float typeSpeed = 6f * 60f; // 字符/秒
        Label typed = TriDraw.typewriter(textBox, full, typeSpeed, Align.center, null, null);
        textBox.add(typed).grow().pad(4);
        intro.add(textBox);

        // ---------- 按钮 table：控制按钮们滑入 ----------
        // 两行结构：第一行“下次启动不再显示”开关，第二行两按钮（邮件、知道了）；
        // 开关样式与写入逻辑与设置页共用 STARTUP_DIALOG_KEY（勾选状态 = 设置值的反相）；
        // 两按钮固定在 table 内（不各自移动），按钮 table 整体从屏幕下方外先快后慢滑入正确位置
        Table buttons = new Table();
        intro.add(buttons);

        // 第一行：开关（勾选 = “下次启动不再显示”，与设置值相反，写入 STARTUP_DIALOG_KEY 并持久化）
        CheckBox check = new CheckBox(Core.bundle.get("tri.startup.dialog.hide"));
        check.update(() -> check.setChecked(!Core.settings.getBool(STARTUP_DIALOG_KEY, true)));
        check.changed(() -> Core.settings.put(STARTUP_DIALOG_KEY, !check.isChecked()));
        buttons.add(check).colspan(2).center().padBottom(checkGap);
        buttons.row();

        // 第二行：邮件按钮（Tmail 图标 + 文本，样式与原一致）
        TextButton mail = new TextButton(Core.bundle.get("tri.mail.button"), Styles.defaultt);
        mail.clearChildren();
        // 图标与文字之间留出恰当间距（与 TriMainMenuButton 构造一致）
        mail.add(new Image(TriDraw.tex("Tmail"))).size(32f).padRight(8);
        mail.add(mail.getLabel());
        mail.clicked(TriMainMenuButton::showInfo);
        buttons.add(mail).size(btnW, btnH);

        // 知道了（关闭欢迎界面）
        TextButton close = new TextButton(get, Styles.defaultt);
        close.clicked(intro::remove);
        buttons.add(close).size(btnW, btnH);

        // 先按内容布局（pack），再手动定位：水平居中于屏幕；垂直以第二行（按钮行）中心对齐 btnCy
        buttons.pack();
        float bw = buttons.getWidth(), bh = buttons.getHeight();
        buttons.setPosition(cx - bw / 2f, -bh - START_MARGIN); // 起始：整张 table 位于屏幕下方外
        // 稍等文本开始打字后起滑（先快后慢 pow2Out），终点按钮行中心对齐 btnCy
        buttons.actions(Actions.delay(0.2f), Actions.moveTo(cx - bw / 2f, btnCy - bh + btnH / 2f, 0.3f, Interp.pow2Out));
    }
}
