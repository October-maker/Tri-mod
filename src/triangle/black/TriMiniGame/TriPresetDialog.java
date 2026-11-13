package triangle.black.TriMiniGame;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.actions.Actions;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Scaling;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.maps.Map;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import triangle.black.TriDraw;

/**
 * 主菜单“特殊模式”入口：进入游戏前选择词条，再点击开始游戏。
 *
 * <p>界面拆成左右两块独立排版的 table，高均为 0.6 屏高：
 * <ul>
 *   <li>左侧：词条按钮组，约占屏宽 1/2，按类别分列、列内自上而下、正方形、同类互斥，
 *       组之间有竖向分割线；列表由上下两条固定的横向分割线夹住（不随滑动移动），
 *       内容整体可横向滑动（滚轮或拖拽），超出视口的部分由 ScrollPane 裁掉；</li>
 *   <li>竖分割线：单独一条，画在右侧详情区左边框处，两侧各留 {@link #DIVIDER_PAD} 空隙；</li>
 *   <li>右侧“详情 table”：顶部右上角为“词条价值总数”统计，中部为地图缩略图 / 词条详情共用区，
 *       其间一条横线（选中时自右向左延长），其下是“地图”按钮（选中后由缩略图 morph 而来）
 *       与“开始游戏”按钮。</li>
 * </ul>
 *
 * <p>动效（全部基于 {@link Element#translation} 做位移，因此不会被 Table 重新布局打回原位）：
 * <ul>
 *   <li>打开：左侧词条组整体自左短距渐入、按钮从左到右从上到下依次显示、上下两条横线自左向右延长、
 *       右侧详情框自右滑入、缩略图渐显、开始按钮自下方屏幕外滑入；</li>
 *   <li>选中：词条框逐个自右侧屏幕外滑入、缩略图缩放并移动到“开始按钮上方”变形成“地图”按钮、
 *       详情横线自右向左延长；取消全部时反向还原；点击“地图”按钮切回地图缩略图（保留选择）。</li>
 * </ul>
 *
 * <p>词条框排版：首行左为名称、右为价值数字（默认按选项档位 1/2/3，可自定义），
 * 次行一条水平分割线隔开首行与该词条描述。
 */
public class TriPresetDialog extends BaseDialog{

    /** 词条按钮边长（正方形）。 */
    private static final float SQUARE = 84f;
    /** 右侧详情组宽 = 屏宽 × 该值，高 = 屏高 × 该值（右组中心仍近似屏幕 3/4 处）。 */
    private static final float GROUP_W_RATIO = 0.36f, GROUP_H_RATIO = 0.6f;
    /** 左侧词条区宽 = 屏宽 × 该值：从最左延伸到竖分割线之前，能同时看到更多列。 */
    private static final float LEFT_W_RATIO = 0.5f;
    /** 竖分割线两侧留白：横线末端↔竖线、竖线↔详情区各留这么多像素。 */
    private static final float DIVIDER_PAD = 8f;
    /** 相邻词条组之间竖分割线的宽度与不透明度。 */
    private static final float SEP_WIDTH = 2f, SEP_ALPHA = 0.22f;
    /** 框住词条列表的上下两条分割线、以及详情区横线的不透明度（比组间线更实一些）。 */
    private static final float RULE_ALPHA = 0.35f;
    /** 同组两个词条之间向下箭头的边长与不透明度。 */
    private static final float ARROW_SIZE = 32f, ARROW_ALPHA = 0.75f;
    /** 地图缩略图边长 = min(组宽, 组高) × 该值；调小即缩小缩略图（1 表示铺满信息区）。 */
    private static final float THUMB_SIZE_RATIO = 0.65f;
    /** 每列最多容纳的单选项词条按钮数；网格 table 的高度据此固定。 */
    private static final int SINGLE_ROWS = 3;
    /** 网格里相邻两行之间的空隙：与左侧大词条组“按钮 + 向下箭头”的行距保持一致（32 + 上下各 2）。 */
    private static final float SINGLE_ROW_GAP = ARROW_SIZE + 4f;
    /** 单选项网格 table 的固定高度：行数 × 按钮格高 + 行间空隙（与左侧三选项列的按钮总高一致，外层再加 pad）。 */
    private static final float SINGLE_TABLE_H = SINGLE_ROWS * (SQUARE + 8f) + (SINGLE_ROWS - 1) * SINGLE_ROW_GAP;
    /** 滚轮一格横向移动的像素量（词条组横向滚动用；窗口内滚轮只有纵向量，需自己映射到横向）。 */
    private static final float WHEEL_STEP = 60f;

    /** 打开时左侧词条组整体自左平移的“很短”距离（非从屏幕外移入）。 */
    private static final float GROUP_IN_DX = 34f;
    /** 打开时右侧详情框自右滑入的距离。 */
    private static final float PANE_IN_DX = 240f;
    /** 入场/位移动画基础时长。 */
    private static final float OPEN_DUR = 0.3f;
    /** 每个词条按钮依次显示的间隔。 */
    private static final float STAGGER = 0.015f;
    /** 缩略图 ↔ “地图”按钮 形变动画时长。 */
    private static final float MORPH_DUR = 0.32f;

    /** 词条详情卡片：入场滑入时长、滑出时长、相邻卡片的入场间隔。 */
    private static final float BOX_IN_DUR = 0.25f, BOX_EXIT_DUR = 0.22f, BOX_STAGGER = 0.03f;
    /** 取消词条后下方卡片“逐个起步”补空的启动间隔与单张补空时长。 */
    private static final float BOX_SHIFT_DELAY = 0.05f, BOX_SHIFT_DUR = 0.18f;
    /** 新插入一行时，下方卡片快速下滑让位的时长（短促，避免看出是布局瞬间跳变）。 */
    private static final float BOX_PUSH_DUR = 0.15f;
    /** 详情卡片每行之间的竖直间距（也用于计算摘掉一行后上方缺口的补空距离）。 */
    private static final float BOX_ROW_PAD = 8f;

    private static TriPresetDialog instance;

    /** 选项按钮引用（[类别][选项]），供切换后仅同步该类别的按钮选中态。 */
    private final TextButton[][] optionButtons = new TextButton[TriPresetMode.categoryCount()][];
    /** 所有按钮的创建顺序（左→右、上→下），用于打开时依次显示。 */
    private final Seq<TextButton> orderedButtons = new Seq<>();
    /** 缩略图是否已换成真实预览（预览异步生成，先用占位图）。 */
    private boolean thumbLoaded;

    private Image mapThumb;
    /** 词条价值总数统计框（详情页面右上角）。 */
    private Table valueRow;
    private Label valueLabel;
    /** 右侧当前是否显示缩略图（false = 正在显示词条详情/或已变形为按钮）。 */
    private boolean thumbShown = true;
    /** 是否处于“保留选择、但临时切回地图视图”的预览态。 */
    private boolean previewMap = false;

    private Image topRule, bottomRule;
    private Table leftGroup;
    private Stack pane;
    private Table thumbHolder;
    private Table detailList;
    /** 词条详情的竖向 ScrollPane：高度受限于 pane，条目再多也不会溢出到面板外（只竖向滚动）。 */
    private ScrollPane detailScroll;
    /** 详情区可用宽度（pane 宽减去左右 pad），词条框按它定宽，避免长描述把框撑出面板。 */
    private float detailW;
    /** 已渲染的词条框缓存，key = {@link #entryKey}；选择变化时按 key 复用，未变的词条不再重建/不再滑入。 */
    private final ObjectMap<String, Table> detailBoxes = new ObjectMap<>();
    /** 当前 detailList 里卡片的顺序（含正在退场的卡片），用于把退场的框放回原槽位。 */
    private final Seq<String> detailOrder = new Seq<>();
    /** 正在滑出、尚未摘行的卡片 key。 */
    private final ObjectSet<String> leavingKeys = new ObjectSet<>();
    /** 同槽位换档的过渡容器：key = 新条目，值 = 同一行里同时承载新旧两张卡的 Stack。 */
    private final ObjectMap<String, Stack> slotOverlays = new ObjectMap<>();
    /** 过渡容器里正在退场的旧卡 key：key = 新条目。 */
    private final ObjectMap<String, String> slotOldKey = new ObjectMap<>();
    /** 详情卡片动画会话号：切地图/回详情时递增，用于作废旧动画里延时执行的隐藏回调。 */
    private int detailEpoch;
    private Image divider;
    private TextButton mapButton;
    private TextButton startButton;

    public static void open(){
        if(instance == null) instance = new TriPresetDialog();
        // 地图预览由原版异步加载/生成；先发起请求，纹理就绪后由 update 回调替换占位图
        if(Vars.maps != null) Vars.maps.loadPreviews();
        instance.show();
        instance.playOpen();
    }

    private TriPresetDialog(){
        super(bundle("tri.preset.title"));

        cont.defaults().pad(6f);

        float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        float groupW = sw * GROUP_W_RATIO, groupH = sh * GROUP_H_RATIO;
        float leftW = sw * LEFT_W_RATIO;

        // ---- 左侧词条区：按类别分列、列内自上而下；正方形；同类互斥；上下两条分割线夹住列表 ----
        // 只有单个选项的词条组（迷雾、增援……）不单独占列，统一收进最右侧的定高网格 table。
        Table buttonBox = new Table();
        boolean first = true;
        for(int c = 0; c < TriPresetMode.categoryCount(); c++){
            if(TriPresetMode.isSingleCategory(c)) continue;

            if(!first) buttonBox.add(separator()).width(SEP_WIDTH).growY().padTop(6f).padBottom(6f);
            first = false;

            Table column = new Table();
            int count = TriPresetMode.optionCount(c);
            optionButtons[c] = new TextButton[count];
            for(int o = 0; o < count; o++){
                // 同组两个词条之间插入向下的箭头，表示二者为同一组的纵向排列
                if(o > 0){
                    Image arrow = new Image(Icon.down);
                    arrow.setColor(1f, 1f, 1f, ARROW_ALPHA);
                    column.add(arrow).size(ARROW_SIZE).pad(2f).row();
                }
                TextButton button = optionButton(c, o);
                optionButtons[c][o] = button;
                orderedButtons.add(button);
                column.add(button).size(SQUARE, SQUARE).pad(4f).row();
            }

            buttonBox.add(column).top().pad(6f);
        }

        // 单选项词组：定高网格 table，按坐标排布（每列最多 SINGLE_ROWS 个，先竖后横）。
        // 第 i 个单选项组（i 从 0 起算）位于第 i / SINGLE_ROWS 列、第 i % SINGLE_ROWS 行，
        // 即“从左往右数第几列、从上往下数第几个”；表高固定，不随词条数量增长。
        int singles = TriPresetMode.singleCategoryCount();
        if(singles > 0){
            buttonBox.add(separator()).width(SEP_WIDTH).growY().padTop(6f).padBottom(6f);

            Table grid = new Table();
            grid.top().align(Align.topLeft);
            for(int i = 0; i < singles; i += SINGLE_ROWS){
                Table column = new Table();
                column.top();
                for(int r = 0; r < SINGLE_ROWS && i + r < singles; r++){
                    int category = TriPresetMode.singleCategory(i + r);
                    // 行间补上和左侧列箭头同等高的空隙，让网格的行与左侧各行横向对齐
                    if(r > 0) column.add().size(SQUARE, SINGLE_ROW_GAP).row();
                    optionButtons[category] = new TextButton[1];
                    TextButton button = optionButton(category, 0);
                    optionButtons[category][0] = button;
                    orderedButtons.add(button);
                    column.add(button).size(SQUARE, SQUARE).pad(4f).row();
                }
                // 内层子列只留左右间距：上下不再加 pad，否则按钮上方会叠出“外层 6 + 内层 6 + 按钮 4”，
                // 比左侧大列多出一层内边距，整组就会整体下移
                grid.add(column).top().padLeft(6f).padRight(6f);
            }
            buttonBox.add(grid).top().height(SINGLE_TABLE_H).pad(6f);
        }
        // 词条组的列数可能超出可用宽度：内容整体左右滑动，由外层 ScrollPane 当视口做裁剪，
        // 超出视口范围的直接隐藏。描边不再挂在内容上，改由上下两条固定的分割线框住视口。
        buttonBox.align(Align.center);

        Table frame = new Table();
        frame.add(buttonBox).pad(4f);

        ScrollPane scroller = new ScrollPane(frame, Styles.horizontalPane);
        scroller.setScrollingDisabled(false, true); // 只许横向滑动
        scroller.setClip(true);                     // 视口外不绘制，避免整卡滑出去盖住右栏
        // 鼠标滚轮在原版窗口里只产生纵向位移，这里用捕获监听把它映射成横向滚动，免去必须拖拽
        scroller.addCaptureListener(new InputListener(){
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                scroller.setScrollX(scroller.getScrollX() + amountY * WHEEL_STEP);
                return true;
            }
        });

        // 上下两条分割线夹住词条列表：线是固定的（不随滑动移动），并随左侧区域一直延伸到
        // 竖分割线之前，也就是右侧详情区左边框的位置；高度仍贴着按钮内容走，不拉满整组 0.6 屏高。
        // 打开时这两条线会“自左向右延长”到现在的长度（用 scaleX 从 0 展开，原点在左端）。
        topRule = rule();
        bottomRule = rule();
        leftGroup = new Table();
        leftGroup.add(topRule).growX().height(SEP_WIDTH).padTop(4f).padBottom(6f).row();
        leftGroup.add(scroller).growX().maxHeight(groupH).padLeft(6f).padRight(6f).row();
        leftGroup.add(bottomRule).growX().height(SEP_WIDTH).padTop(6f).padBottom(4f);

        // ---- 右侧“详情 table”：右上角价值总数 / 缩略图或详情共用区 / 横线 / 地图按钮 / 开始游戏 ----
        Table rightGroup = new Table();

        // 词条价值总数：默认是地图态，隐藏；进入详情时显示在右上角
        valueLabel = new Label("", Styles.defaultLabel);
        Table valueBox = new Table();
        valueBox.setBackground(TriDraw.whiteOutlined(Styles.black6));
        valueBox.add(new Label(Core.bundle.get("tri.preset.totalValue", "价值"), Styles.defaultLabel)).pad(6f).padRight(8f);
        valueBox.add(valueLabel).pad(6f);
        valueRow = new Table();
        valueRow.add(valueBox).right().padRight(2f);
        valueRow.visible = false;

        Map map = TriPresetMode.map();
        mapThumb = new Image(preview(map), Scaling.fit);

        // 词条详情：每条一个独立 table，从右侧屏幕外滑入
        detailList = new Table();
        detailList.top();

        // 详情区宽度：pane 左右各 pad 6，再留 2px 余量给贴图边框，避免词条框贴边或越界
        detailW = groupW - 12f - 2f;

        // 词条详情放进竖向 ScrollPane：高度被 pane 卡住，选中条目多时改为内部滚动，
        // 不再让 detailList 把内容顶到面板外（横向滚动禁用，宽度由 detailW 固定）。
        detailScroll = new FillHeightScrollPane(detailList, Styles.noBarPane);
        detailScroll.setScrollingDisabled(true, false);
        detailScroll.setFadeScrollBars(false);
        detailScroll.setOverscroll(false, false);
        detailScroll.visible = false;

        // 缩略图居中容器（只占信息区一部分，避免铺满整个右组）
        float thumbSize = Math.min(groupW, groupH) * THUMB_SIZE_RATIO;
        thumbHolder = new Table();
        thumbHolder.add(mapThumb).size(thumbSize).center();

        // 缩略图与详情共用同一块区域：切换显隐即可互换。
        pane = new Stack();
        pane.add(thumbHolder);
        pane.add(detailScroll);

        rightGroup.add(valueRow).growX().right().padTop(4f).row();
        rightGroup.add(pane).grow().pad(6f).row();

        // 详情区横线：选中时自右向左延长（用 scaleX 从 0 展开，原点在右端）
        divider = new Image(Core.atlas.find("white"));
        divider.setColor(1f, 1f, 1f, 0.35f);
        rightGroup.add(divider).growX().height(2f).padTop(4f).padBottom(8f).row();

        // “地图”按钮：选中后由缩略图形变而来，位于开始按钮上方；初始透明且不可点
        mapButton = new TextButton(Core.bundle.get("tri.preset.mapButton", "地图"), Styles.defaultt);
        mapButton.setColor(1f, 1f, 1f, 0f);
        mapButton.touchable = Touchable.disabled;
        mapButton.clicked(this::onMapButtonClicked);
        rightGroup.add(mapButton).size(160f, 44f).padBottom(6f).row();

        startButton = new TextButton(bundle("tri.preset.start"), Styles.defaultt);
        startButton.clicked(() -> {
            // 按钮是按 Style 的“选中/按下”态绘制的：在 clicked 回调里直接 hide()，这一帧的
            // touchUp 就没机会把 checked/down 清掉，而这个 Dialog 是单例，下次打开会带着
            // 上一次的“已按下”外观。所以先手动复位，再延到本帧末（输入处理完）才真正关窗口。
            startButton.setChecked(false);
            Core.app.post(() -> {
                hide();
                TriPresetMode.start();
            });
        });
        rightGroup.add(startButton).size(260f, 54f).padBottom(8f);

        // 详情区左边框处单独画一条竖向分割线：它不贴着左右两块，两侧各留 DIVIDER_PAD 的空白，
        // 详情区因此整体右移一点给这条线让位
        cont.add(leftGroup).size(leftW, groupH);
        cont.add(rule()).growY().width(SEP_WIDTH).padLeft(DIVIDER_PAD).padRight(DIVIDER_PAD);
        cont.add(rightGroup).size(groupW, groupH);
        cont.row();

        addCloseButton();

        // 地图预览是异步生成的：纹理就绪后把占位图换成真实缩略图
        mapThumb.update(() -> {
            if(thumbLoaded || map == null || map.texture == null) return;
            thumbLoaded = true;
            mapThumb.setDrawable(new TextureRegionDrawable(new TextureRegion(map.texture)));
        });
    }

    // ============================ 打开动画 ============================

    /** 打开时的入场动画：词条组短距左渐入 + 按钮依次显示 + 上下线自左向右延长 + 右侧框自右滑入 +
     *  缩略图渐显 + 开始按钮自下方屏幕外滑入。全部基于 translation，避免被 Table 重新布局打回。 */
    private void playOpen(){
        float sh = Core.graphics.getHeight();

        // 右侧先回到“地图态”
        thumbShown = true;
        previewMap = false;
        detailEpoch++; // 作废上一轮可能还在跑的退场回调
        detailScroll.visible = false;
        // 每次重新打开界面都清掉词条框缓存（选择可能已被上次会话改变），入场时按当前选择全量滑入
        clearDetailState();
        valueRow.visible = false;
        mapButton.setColor(1f, 1f, 1f, 0f);
        mapButton.touchable = Touchable.disabled;
        mapThumb.visible = true;
        mapThumb.clearActions();
        mapThumb.setScale(1f, 1f);
        mapThumb.setTranslation(0f, 0f);

        // 1) 左侧词条组整体自左“很短”距离渐入（位移，非从屏幕外）
        leftGroup.clearActions();
        leftGroup.setTranslation(-GROUP_IN_DX, 0f);
        leftGroup.addAction(Actions.translateBy(GROUP_IN_DX, 0f, OPEN_DUR, Interp.pow2Out));

        // 2) 每个词条按钮从左到右、从上到下依次显示（按创建顺序做 alpha 渐显 + 递增延迟）
        for(int i = 0; i < orderedButtons.size; i++){
            TextButton b = orderedButtons.get(i);
            b.clearActions();
            b.setColor(1f, 1f, 1f, 0f);
            b.addAction(Actions.sequence(Actions.delay(Math.min(i * STAGGER, 0.7f)), Actions.fadeIn(0.12f, Interp.fade)));
        }

        // 3) 夹住词条组的上下两条横线从左侧向右延长到当前长度（scaleX 自 0 展开，原点在左）
        for(int i = 0; i < 2; i++){
            Image r = i == 0 ? topRule : bottomRule;
            r.clearActions();
            r.setOrigin(0f, 0f);
            r.setScale(0f, 1f);
        }
        topRule.addAction(Actions.sequence(Actions.delay(0.05f), Actions.scaleTo(1f, 1f, OPEN_DUR, Interp.pow2Out)));
        bottomRule.addAction(Actions.sequence(Actions.delay(0.10f), Actions.scaleTo(1f, 1f, OPEN_DUR, Interp.pow2Out)));

        // 4) 右侧详情框自右侧滑入
        pane.clearActions();
        pane.setTranslation(PANE_IN_DX, 0f);
        pane.addAction(Actions.translateBy(-PANE_IN_DX, 0f, OPEN_DUR, Interp.pow2Out));

        // 5) 地图缩略图渐显
        mapThumb.clearActions();
        mapThumb.setColor(1f, 1f, 1f, 0f);
        mapThumb.addAction(Actions.sequence(Actions.delay(0.15f), Actions.fadeIn(0.25f, Interp.fade)));

        // 6) 开始按钮从下方屏幕外滑入（其父级就是这张右侧详情 table）
        //    这张界面是单例，“开始游戏”/“地图”按钮的 checked 会跨会话残留，重开时先复位
        startButton.setChecked(false);
        mapButton.setChecked(false);
        startButton.clearActions();
        startButton.setTranslation(0f, -sh);
        startButton.addAction(Actions.sequence(Actions.delay(0.10f), Actions.translateBy(0f, sh, 0.45f, Interp.pow2Out)));

        // 若打开时已有选中项（上次会话遗留）：缩略图渐显完成后，延迟进入详情
        if(TriPresetMode.hasSelection()){
            leftGroup.addAction(Actions.sequence(Actions.delay(0.45f), Actions.run(() -> {
                if(!TriPresetMode.hasSelection()) return;
                thumbShown = true;
                enterDetail();
            })));
        }else{
            divider.clearActions();
            divider.setScale(1f, 1f);
        }
    }

    // ============================ 词条切换 ============================

    /** 生成一个词条按钮：点击即切换该选项，并同步所有按钮的选中态与可用态。 */
    private TextButton optionButton(int category, int option){
        TextButton button = new TextButton(TriPresetMode.optionLabel(category, option), Styles.togglet);
        button.setChecked(TriPresetMode.isSelected(category, option));
        button.setDisabled(TriPresetMode.isOptionLocked(category, option));

        // 悬停提示：锁住时说清“需要先选什么”，可选时显示该词条的详情
        button.addListener(new Tooltip(t -> t.background(Styles.black6).margin(8f).label(() -> {
            String reason = TriPresetMode.lockReason(category, option);
            return reason != null ? reason : TriPresetMode.optionDetail(category, option);
        })));

        button.clicked(() -> {
            boolean wasSelected = TriPresetMode.hasSelection();
            TriPresetMode.toggle(category, option);
            // 前置变化会影响别的词条组，必须整表刷新而不是只刷本组
            refreshAll();
            boolean nowSelected = TriPresetMode.hasSelection();
            previewMap = false; // 改变选择即离开“预览地图”态

            if(nowSelected){
                // 有选中：进入/刷新详情
                if(thumbShown){
                    // 缩略图正显示 → 缩放移动到开始按钮上方，变形成“地图”按钮
                    morphThumbToButton();
                    thumbShown = false;
                }
                // 只让新增/换档的卡片播入场，已存在的卡片原地复用（不整组重播）
                showDetailContent(false);
                setMapButton(true, Core.bundle.get("tri.preset.mapButton", "地图"));
                extendDividerFromRight();
            }else{
                // 全部取消：反向还原成地图缩略图
                showMapState(true);
            }
        });
        return button;
    }

    /** 进入详情（首次选中或从预览切回详情时复用；调用方需已把缩略图 morph 掉）。 */
    private void enterDetail(){
        if(thumbShown){
            morphThumbToButton();
            thumbShown = false;
        }
        showDetailContent(true);
        setMapButton(true, Core.bundle.get("tri.preset.mapButton", "地图"));
        extendDividerFromRight();
        previewMap = false;
    }

    /**
     * 同步本次已选词条的详情列表：逐条渲染“名称 + 价值数字 / 分隔线 / 描述”，并刷新右上角价值总数。
     *
     * <p>已渲染过的词条按 {@link #entryKey} 复用原来的 table，选择变化时不重建、不重播入场：
     * <ul>
     *   <li>取消：该卡片向右滑出，滑出结束才摘掉这一行，下方卡片再“逐个起步”地向上补空；</li>
     *   <li>同类换档位：新旧两张卡放进同一槽位的 Stack（一进一出互不挤压其它行），
     *       旧卡向左滑出、新卡同时从右侧滑入，旧卡走完后槽位只剩新卡；</li>
     *   <li>新增别的类别：新卡片从右侧滑入，其余卡片原地不动。</li>
     * </ul>
     *
     * @param slideAll true 时所有卡片都重新从右侧依次滑入（从地图态切回详情时用）
     */
    private void showDetailContent(boolean slideAll){
        boolean wasHidden = !detailScroll.visible;
        detailScroll.visible = true;
        detailScroll.toFront(); // 回到详情态时把详情层提到最前（叠在缩略图之上）
        if(wasHidden) detailScroll.setScrollY(0f); // 从地图态切回来时回到顶部；切词条时保持滚动位置

        Seq<TriPresetMode.Entry> entries = TriPresetMode.selectedEntries();
        // 浅拷贝一份旧顺序（不能用 Seq(Seq) 构造，那个会共享同一个数组，后续改动会污染这份快照）
        Seq<String> prevOrder = new Seq<>();
        prevOrder.addAll(detailOrder);

        // 1) 新条目建框；已有条目直接复用缓存里的 table
        for(TriPresetMode.Entry e : entries){
            String key = entryKey(e);
            if(!detailBoxes.containsKey(key)) detailBoxes.put(key, buildDetailBox(e));
        }

        // 2) 上一轮没跑完的过渡容器：新档位仍被选中就继续用；否则立刻收尾
        //    （否则同一张卡会同时挂在 Stack 和新行两个父亲上，绘制错乱）
        flushStaleSlots(entries);

        // 3) 缓存里已不属于本次选择的框：同类还有新档位 → 走“同槽位换卡”；否则 → 取消滑出
        ObjectMap<String, String> swapPair = new ObjectMap<>(); // 新 key → 旧 key
        Seq<String> cancelled = new Seq<>();
        for(String key : detailBoxes.keys()){
            if(leavingKeys.contains(key) || hasEntry(entries, key)) continue;
            String newKey = firstKeyOfCategory(entries, categoryOf(key));
            if(newKey != null){
                // 同槽位里已经有过渡容器（连续快速换档）就不重复配对，交给正在跑的那次过渡收尾
                if(!slotOverlays.containsKey(newKey) && !swapPair.containsKey(newKey)) swapPair.put(newKey, key);
            }else{
                cancelled.add(key);
            }
        }

        // 3) 新顺序：按类别顺序排条目；被取消的旧框回到它原来的槽位
        Seq<String> order = new Seq<>();
        for(TriPresetMode.Entry e : entries) order.add(entryKey(e));
        for(String key : cancelled){
            int oldIdx = prevOrder.indexOf(key);
            if(oldIdx < 0) oldIdx = order.size;
            order.insert(Math.max(0, Math.min(oldIdx, order.size)), key);
        }

        // 4) 重排 cell：换档槽位用 Stack 同时容纳新旧两张卡，其余卡片复位或播入场
        detailList.clearChildren();
        detailOrder.clear();
        float off = detailW; // 起点是详情区右边界（ScrollPane 会裁掉区域外的绘制）
        int enterIdx = 0;
        for(int i = 0; i < order.size; i++){
            String key = order.get(i);
            Table box = detailBoxes.get(key);
            if(box == null) continue;
            detailOrder.add(key);

            String oldKey = swapPair.get(key);
            if(oldKey != null){
                // 同一槽位：新卡在下、旧卡在上，两张都按 detailW 定宽（Stack 行高取两者较高者）
                Stack slot = new Stack();
                slot.add(box);
                Table oldBox = detailBoxes.get(oldKey);
                if(oldBox != null) slot.add(oldBox);
                slotOverlays.put(key, slot);
                slotOldKey.put(key, oldKey);
                detailList.add(slot).width(detailW).padBottom(BOX_ROW_PAD).row();
                continue; // 入场/退场由 startSlotSwap 单独安排
            }

            Stack slot = slotOverlays.get(key);
            // 定宽加入：ScrollPane 里 detailList 的宽度由 detailW 固定，长描述只在框内换行，不横向撑开
            detailList.add(slot != null ? slot : box).width(detailW).padBottom(BOX_ROW_PAD).row();
            if(leavingKeys.contains(key) || slot != null) continue;

            boolean fresh = prevOrder.indexOf(key) < 0;
            if(slideAll || fresh){
                float delay = (slideAll ? i : enterIdx) * BOX_STAGGER; // 从上到下的顺序
                box.clearActions();
                box.setTranslation(off, 0f);
                box.addAction(Actions.sequence(Actions.delay(delay),
                        Actions.translateBy(-off, 0f, BOX_IN_DUR, Interp.pow2Out)));
                if(!slideAll) enterIdx++;
            }else{
                box.clearActions();
                box.setTranslation(0f, 0f);
            }
        }

        // 5) 启动同槽位换卡（旧卡左出、新卡右入）
        for(String newKey : swapPair.keys()) startSlotSwap(swapPair.get(newKey), newKey);
        // 6) 下方卡片被“瞬间推下去”的部分改成快速下滑：统计每张卡上方新增的行高，再补一段位移滑过去
        //    a) 本次新插入的行（点中间的词条时，它下面所有卡片都要让位）
        //    b) 换档槽位的行高取两张卡较高者，新卡更高时下方同样会被推下去
        if(!slideAll) pushDownRest(prevOrder, swapPair);
        // 7) 启动取消滑出（向右）
        for(String key : cancelled) startBoxLeave(key);

        valueLabel.setText(String.valueOf(TriPresetMode.selectedValueTotal()));
        valueRow.visible = true;
    }

    /**
     * 让被插入行推下去的卡片“快速下滑”到位，而不是瞬间跳到新位置。
     *
     * <p>逐张卡统计它上方新增的高度（新插入的行 + 换档槽位高出的部分），把该卡先按回原位置，
     * 再用 {@link #BOX_PUSH_DUR} 的短时长滑下去；新插入的卡、正在滑出的卡、换档中的卡不参与，
     * 以免打断它们自己的横向动画。
     */
    private void pushDownRest(Seq<String> prevOrder, ObjectMap<String, String> swapPair){
        int n = detailOrder.size;
        for(int j = 0; j < n; j++){
            String key = detailOrder.get(j);
            Table box = detailBoxes.get(key);
            if(box == null || leavingKeys.contains(key) || slotOverlays.containsKey(key)) continue;
            if(prevOrder.indexOf(key) < 0) continue; // 本次新建的卡自己就在新位置，无需补偿

            float delta = 0f;
            for(int k = 0; k < j; k++){
                String upKey = detailOrder.get(k);
                Table upBox = detailBoxes.get(upKey);
                if(upBox == null) continue;
                if(prevOrder.indexOf(upKey) < 0 && !slotOverlays.containsKey(upKey)){
                    delta += upBox.getPrefHeight() + BOX_ROW_PAD; // a) 上方新插入的行
                }
                String oldKey = swapPair.get(upKey);
                if(oldKey != null){
                    Table oldBox = detailBoxes.get(oldKey);
                    if(oldBox != null) delta += Math.max(0f, upBox.getPrefHeight() - oldBox.getPrefHeight()); // b) 换档槽位变高
                }
            }
            if(delta <= 0.5f) continue;

            box.clearActions();
            box.setTranslation(0f, delta); // 先画在旧位置（抵消被压下的位移）
            box.addAction(Actions.translateBy(0f, -delta, BOX_PUSH_DUR, Interp.pow2Out));
        }
    }

    /** 同槽位换卡：旧档位的卡向左滑出，新档位的卡同时从右侧滑入；旧卡走完即从槽位摘掉。 */
    private void startSlotSwap(String oldKey, String newKey){
        Table newBox = detailBoxes.get(newKey);
        Table oldBox = detailBoxes.get(oldKey);
        float off = detailW;

        if(newBox != null){
            newBox.clearActions();
            newBox.setTranslation(off, 0f);
            newBox.addAction(Actions.translateBy(-off, 0f, BOX_IN_DUR, Interp.pow2Out));
        }
        if(oldBox != null){
            oldBox.clearActions();
            oldBox.setTranslation(0f, 0f);
            oldBox.addAction(Actions.sequence(
                    Actions.translateBy(-off, 0f, BOX_EXIT_DUR, Interp.pow2Out),
                    // 等新卡的滑入也收尾（避免收槽时截断它的横向动画）
                    Actions.delay(Math.max(0f, BOX_IN_DUR - BOX_EXIT_DUR)),
                    Actions.run(() -> finishSlotSwap(newKey, oldKey))));
        }
    }

    /** 旧卡走完：槽位里只剩新卡；过渡行高比新卡高出的部分，让下方卡片逐个补空。 */
    private void finishSlotSwap(String newKey, String oldKey){
        Stack slot = slotOverlays.remove(newKey);
        slotOldKey.remove(newKey);
        Table newBox = detailBoxes.get(newKey);
        detailBoxes.remove(oldKey);
        if(slot == null) return;

        int idx = Math.max(0, detailOrder.indexOf(newKey));
        float oldRowH = slot.getHeight(); // 过渡期间的行高 = max(旧卡高, 新卡高)
        slot.clearChildren();             // 新旧两张都摘下来，新卡随后单独挂回
        rebuildRows();
        if(newBox == null) return;

        float shift = oldRowH - newBox.getPrefHeight(); // 行高收缩量
        if(shift <= 0.5f) return;
        int n = detailOrder.size;
        for(int i = idx + 1; i < n; i++){
            Table b = detailBoxes.get(detailOrder.get(i));
            if(b == null) continue;
            b.clearActions();
            b.setTranslation(0f, -shift); // 先画在旧位置
            b.addAction(Actions.sequence(
                    Actions.delay((i - idx - 1) * BOX_SHIFT_DELAY),
                    Actions.translateBy(0f, shift, BOX_SHIFT_DUR, Interp.pow2Out)));
        }
    }

    /** 取消某个词条：卡片向右滑出，滑完摘掉该行并让下方卡片逐个补空。 */
    private void startBoxLeave(String key){
        Table box = detailBoxes.get(key);
        if(box == null || leavingKeys.contains(key)) return;
        leavingKeys.add(key);
        box.clearActions();
        box.setTranslation(0f, 0f);
        box.addAction(Actions.sequence(
                Actions.translateBy(detailW, 0f, BOX_EXIT_DUR, Interp.pow2Out),
                Actions.run(() -> removeLeavingBox(key))));
    }

    /** 摘掉已滑出的卡片所在行，并让它下方的卡片从旧位置起“逐个起步”地向上补空（类似车队依次启动）。 */
    private void removeLeavingBox(String key){
        if(!detailBoxes.containsKey(key)) return;
        int idx = Math.max(0, detailOrder.indexOf(key));
        Table box = detailBoxes.remove(key);
        leavingKeys.remove(key);
        if(box == null) return;

        // 这一行的占位高度 = 卡片高 + 行间距；摘行后下方卡片会立即上移这么多
        float shift = box.getHeight() + BOX_ROW_PAD;
        detailOrder.remove(idx);
        rebuildRows();

        int n = detailOrder.size;
        for(int i = idx; i < n; i++){
            Table b = detailBoxes.get(detailOrder.get(i));
            if(b == null) continue;
            b.clearActions();
            b.setTranslation(0f, -shift);   // 先画在旧位置
            b.addAction(Actions.sequence(
                    Actions.delay((i - idx) * BOX_SHIFT_DELAY),  // 越靠下的启动越晚
                    Actions.translateBy(0f, shift, BOX_SHIFT_DUR, Interp.pow2Out)));
        }
    }

    /** 按 {@link #detailOrder} 重新挂一遍 cell（换档槽位挂它的 Stack，其余挂卡片本身）。 */
    private void rebuildRows(){
        detailList.clearChildren();
        for(int i = 0; i < detailOrder.size; i++){
            String key = detailOrder.get(i);
            Table box = detailBoxes.get(key);
            if(box == null) continue;
            Stack slot = slotOverlays.get(key);
            detailList.add(slot != null ? slot : box).width(detailW).padBottom(BOX_ROW_PAD).row();
        }
    }

    /** 本次选择里是否已有该 key。 */
    private static boolean hasEntry(Seq<TriPresetMode.Entry> entries, String key){
        for(TriPresetMode.Entry e : entries) if(entryKey(e).equals(key)) return true;
        return false;
    }

    /** 本次选择里属于该类别的第一条目的 key（同类换档时用来配对新旧两张卡）；没有则返回 null。 */
    private static String firstKeyOfCategory(Seq<TriPresetMode.Entry> entries, int category){
        for(TriPresetMode.Entry e : entries) if(e.category == category) return entryKey(e);
        return null;
    }

    /** 从 key（“类别:选项”）里取出类别下标。 */
    private static int categoryOf(String key){
        return Integer.parseInt(key.substring(0, key.indexOf(':')));
    }

    /** 词条框的唯一 key：类别下标 + 选项下标（同一类换档位算不同词条，会作为新框滑入）。 */
    private static String entryKey(TriPresetMode.Entry e){
        return e.category + ":" + e.option;
    }

    /** 构建单个词条框：首行“名称 + 价值数字”，次行分割线，末行描述。 */
    private Table buildDetailBox(TriPresetMode.Entry e){
        Table box = new Table();
        box.setBackground(TriDraw.whiteOutlined(Styles.black6));

        // 首行：左为名称，右为价值数字（1~3）
        Table head = new Table();
        Label nameLabel = new Label(e.name, Styles.defaultLabel);
        nameLabel.setAlignment(Align.left);
        Label valueText = new Label(String.valueOf(e.value), Styles.defaultLabel);
        valueText.setAlignment(Align.right);
        head.add(nameLabel).left().growX();
        head.add(valueText).right().padLeft(8f);
        box.add(head).growX().padLeft(10f).padRight(10f).padTop(8f).row();

        // 次行：水平分割线，隔开“名称/价值”与下方描述
        Image line = new Image(Core.atlas.find("white"));
        line.setColor(1f, 1f, 1f, RULE_ALPHA);
        box.add(line).growX().height(1f).padLeft(10f).padRight(10f).padTop(6f).padBottom(6f).row();

        // 描述
        Label desc = new Label(e.detail, Styles.defaultLabel);
        desc.setWrap(true);
        desc.setAlignment(Align.center);
        box.add(desc).growX().pad(10f).row();
        return box;
    }

    /** 回到地图视图：详情卡片自下而上依次滑出 → 隐藏详情区 → 缩略图（必要时）反向 morph 回来。 */
    private void showMapState(boolean animate){
        valueRow.visible = false;
        setMapButton(false, null);
        previewMap = false;

        boolean morphThumb = !thumbShown;
        thumbShown = true;

        // 有卡片在显示时走退场动画；否则直接切换
        if(animate && detailScroll.visible && detailOrder.size > 0){
            exitDetailToMap(morphThumb);
            return;
        }
        detailEpoch++;
        detailScroll.visible = false;
        clearDetailState();
        if(morphThumb){
            mapThumb.visible = true;
            mapThumb.clearActions();
            mapThumb.setTranslation(0f, 0f);
            mapThumb.setScale(1f, 1f);
            mapThumb.setColor(1f, 1f, 1f, 1f);
        }
    }

    /** 详情 → 地图的退场：卡片按“从下到上”的顺序依次向右滑出，缩略图与卡片退场同步出现并展开。 */
    private void exitDetailToMap(boolean morphThumb){
        int epoch = ++detailEpoch;

        // 手感优化：缩略图不等卡片走完，立刻从“地图”按钮位置开始 morph（同时出现、同时展开）
        if(morphThumb){
            thumbHolder.toFront(); // 缩略图所在层提到最前：卡片滑出时地图盖在上面，不被裁切/遮挡
            morphThumbFromButton();
        }

        int n = detailOrder.size;
        for(int i = 0; i < n; i++){
            Table box = detailBoxes.get(detailOrder.get(i));
            if(box == null) continue;
            float delay = (n - 1 - i) * BOX_SHIFT_DELAY; // 最下面的先动，往上逐张延后
            box.clearActions();
            box.setTranslation(0f, 0f);
            box.addAction(Actions.sequence(Actions.delay(delay),
                    Actions.translateBy(detailW, 0f, BOX_EXIT_DUR, Interp.pow2Out))); // 向右滑出（被 ScrollPane 裁掉）
        }

        // 卡片全部离开视区后才收起详情区（缩略图此时已经 morph 到位）
        float total = Math.max(0, n - 1) * BOX_SHIFT_DELAY + BOX_EXIT_DUR;
        pane.clearActions();
        pane.addAction(Actions.sequence(Actions.delay(total), Actions.run(() -> {
            if(epoch != detailEpoch) return; // 期间已切回详情或关闭界面：作废
            detailScroll.visible = false;
            clearDetailState();
        })));
    }

    /** 清空详情卡片缓存与列表（整组退场或关闭界面时用）。 */
    private void clearDetailState(){
        detailList.clearChildren();
        detailBoxes.clear();
        detailOrder.clear();
        leavingKeys.clear();
        slotOverlays.clear();
        slotOldKey.clear();
    }

    /**
     * 收尾上一轮没跑完的换档过渡：新档位已不在这份选择里的过渡容器立即拆掉。
     *
     * <p>拆的时候把两张卡都从 Stack 里摘下来、横向位移归零并清掉正在跑的动画，
     * 之后它们会按常规逻辑重新参与（该滑出的走取消分支，该保留的重新挂回自己的行）。
     */
    private void flushStaleSlots(Seq<TriPresetMode.Entry> entries){
        if(slotOverlays.size == 0) return;
        Seq<String> owners = new Seq<>();
        owners.addAll(slotOverlays.keys());
        for(String key : owners){
            if(hasEntry(entries, key)) continue;
            Stack slot = slotOverlays.remove(key);
            slotOldKey.remove(key);
            if(slot == null) continue;
            Seq<Element> kids = new Seq<>();
            kids.addAll(slot.getChildren());
            slot.clearChildren();
            for(Element kid : kids){
                kid.clearActions();
                kid.setTranslation(0f, 0f);
            }
        }
    }

    // ==================== 缩略图 ⇄ “地图”按钮 形变 ====================

    /** 计算“缩略图中心 → 地图按钮中心”的位移（在右组本地坐标系，等价于 stage 像素差）。 */
    private float[] morphDelta(){
        Vec2 a = thumbHolder.localToAscendantCoordinates(rightGroup(),
                new Vec2(thumbHolder.getWidth() / 2f, thumbHolder.getHeight() / 2f));
        Vec2 b = mapButton.localToAscendantCoordinates(rightGroup(),
                new Vec2(mapButton.getWidth() / 2f, mapButton.getHeight() / 2f));
        return new float[]{b.x - a.x, b.y - a.y};
    }

    private Element rightGroup(){
        // mapButton 的父容器即右侧详情 table
        return mapButton.parent;
    }

    /** 缩略图缩放变小并移动到“地图”按钮位置、淡出（形变成按钮）。 */
    private void morphThumbToButton(){
        float[] d = morphDelta();
        mapThumb.clearActions();
        mapThumb.visible = true;
        mapThumb.setScale(1f, 1f);
        mapThumb.setTranslation(0f, 0f);
        mapThumb.setColor(1f, 1f, 1f, 1f);
        mapThumb.addAction(Actions.parallel(
                Actions.translateBy(d[0], d[1], MORPH_DUR, Interp.pow2Out),
                Actions.scaleTo(0.3f, 0.3f, MORPH_DUR, Interp.pow2Out),
                Actions.alpha(0f, MORPH_DUR, Interp.pow2Out)
        ));
        mapThumb.addAction(Actions.sequence(Actions.delay(MORPH_DUR), Actions.run(() -> {
            mapThumb.visible = false;
            mapThumb.setTranslation(0f, 0f);
            mapThumb.setScale(1f, 1f);
            mapThumb.setColor(1f, 1f, 1f, 1f);
        })));
    }

    /** 反向：缩略图从“地图”按钮位置回到原处并放大、淡入（还原成地图缩略图）。 */
    private void morphThumbFromButton(){
        float[] d = morphDelta();
        mapThumb.clearActions();
        mapThumb.visible = true;
        mapThumb.setTranslation(d[0], d[1]);
        mapThumb.setScale(0.3f, 0.3f);
        mapThumb.setColor(1f, 1f, 1f, 0f);
        mapThumb.addAction(Actions.parallel(
                Actions.translateBy(-d[0], -d[1], MORPH_DUR, Interp.pow2Out),
                Actions.scaleTo(1f, 1f, MORPH_DUR, Interp.pow2Out),
                Actions.alpha(1f, MORPH_DUR, Interp.pow2Out)
        ));
    }

    private void setMapButton(boolean show, String label){
        if(label != null) mapButton.setText(label);
        mapButton.clearActions();
        if(show){
            mapButton.touchable = Touchable.enabled;
            mapButton.addAction(Actions.alpha(1f, 0.15f, Interp.fade));
        }else{
            mapButton.touchable = Touchable.disabled;
            mapButton.addAction(Actions.alpha(0f, 0.12f, Interp.fade));
        }
    }

    /** 详情横线自右向左延长到当前长度（scaleX 自 0 展开，原点放在右端）。 */
    private void extendDividerFromRight(){
        divider.clearActions();
        divider.setOrigin(divider.getWidth(), divider.getHeight() / 2f);
        divider.setScale(0f, 1f);
        divider.addAction(Actions.scaleTo(1f, 1f, 0.35f, Interp.pow2Out));
    }

    /** “地图”按钮：在详情态点击切回地图缩略图（保留选择），再点回到词条详情。 */
    private void onMapButtonClicked(){
        if(!TriPresetMode.hasSelection()) return;

        if(!previewMap){
            // 切回地图视图：详情卡片自下而上滑出后缩略图再 morph 回来，按钮改提示“词条”
            previewMap = true;
            thumbShown = true;
            valueRow.visible = false;
            if(detailScroll.visible && detailOrder.size > 0){
                exitDetailToMap(true);
            }else{
                detailEpoch++;
                detailScroll.visible = false;
                clearDetailState();
                morphThumbFromButton();
            }
            setMapButton(true, Core.bundle.get("tri.preset.detailButton", "词条"));
        }else{
            // 回到词条详情：缩略图再 morph 成按钮，卡片自上而下依次从右侧滑回
            previewMap = false;
            detailEpoch++; // 作废退场动画的延时回调（此时可能还没执行）
            morphThumbToButton();
            thumbShown = false;
            showDetailContent(true);
            setMapButton(true, Core.bundle.get("tri.preset.mapButton", "地图"));
            extendDividerFromRight();
        }
    }

    // ============================ 工具 ============================

    /**
     * 同步所有按钮的状态：选中态 + 可用态。
     *
     * <p>必须整表刷新：前置条件会让别的词条组一起变灰/解锁（例如取消“毁灭”难度，
     * 六个最高档词条要立刻变灰并且已被清除）。
     */
    private void refreshAll(){
        for(int c = 0; c < TriPresetMode.categoryCount(); c++){
            TextButton[] buttons = optionButtons[c];
            if(buttons == null) continue;
            for(int i = 0; i < buttons.length; i++){
                buttons[i].setChecked(TriPresetMode.isSelected(c, i));
                buttons[i].setDisabled(TriPresetMode.isOptionLocked(c, i));
            }
        }
    }

    /** 词条组之间的竖向分割线（高度随所在行最高的一组按钮）。 */
    private static Image separator(){
        Image sep = new Image(Core.atlas.find("white"));
        sep.setColor(1f, 1f, 1f, SEP_ALPHA);
        return sep;
    }

    /** 框住词条列表的横向分割线、详情区横线（比组间线更实）。 */
    private static Image rule(){
        Image line = new Image(Core.atlas.find("white"));
        line.setColor(1f, 1f, 1f, RULE_ALPHA);
        return line;
    }

    /** 缩略图素材：已有预览纹理就用它，否则先用原版 error 占位（就绪后由 update 替换）。 */
    private static Drawable preview(Map map){
        if(map != null && map.texture != null){
            return new TextureRegionDrawable(new TextureRegion(map.texture));
        }
        return new TextureRegionDrawable(Core.atlas.find("error"));
    }

    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }

    /**
     * 详情区的 ScrollPane：prefHeight 固定返回 0。
     *
     * <p>默认 ScrollPane 的 prefHeight = 内容高度，选中词条一多就会把 rightGroup 的这一行“pref”顶大，
     * 使 Table 在高度不足时按比例压缩其它行（横线、地图按钮、开始按钮都会被压扁）。
     * 这里让它不参与行高计算，只吃 {@code grow()} 分到的剩余高度，超出部分内部竖向滚动。
     */
    private static class FillHeightScrollPane extends ScrollPane{
        FillHeightScrollPane(Element widget, ScrollPaneStyle style){
            super(widget, style);
        }

        @Override
        public float getPrefHeight(){
            return 0f;
        }
    }
}
