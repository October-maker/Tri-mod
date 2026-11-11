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
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
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
        detailList.visible = false;

        // 缩略图居中容器（只占信息区一部分，避免铺满整个右组）
        float thumbSize = Math.min(groupW, groupH) * THUMB_SIZE_RATIO;
        thumbHolder = new Table();
        thumbHolder.add(mapThumb).size(thumbSize).center();

        // 缩略图与详情共用同一块区域：切换显隐即可互换。
        pane = new Stack();
        pane.add(thumbHolder);
        pane.add(detailList);

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
            hide();
            TriPresetMode.start();
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
        detailList.visible = false;
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

    /** 生成一个词条按钮：点击即切换该选项，并同步同类按钮的选中态与右侧信息栏。 */
    private TextButton optionButton(int category, int option){
        TextButton button = new TextButton(TriPresetMode.optionLabel(category, option), Styles.togglet);
        button.setChecked(TriPresetMode.isSelected(category, option));
        button.clicked(() -> {
            boolean wasSelected = TriPresetMode.hasSelection();
            TriPresetMode.toggle(category, option);
            refresh(category);
            boolean nowSelected = TriPresetMode.hasSelection();
            previewMap = false; // 改变选择即离开“预览地图”态

            if(nowSelected){
                // 有选中：进入/刷新详情
                if(thumbShown){
                    // 缩略图正显示 → 缩放移动到开始按钮上方，变形成“地图”按钮
                    morphThumbToButton();
                    thumbShown = false;
                }
                showDetailContent(true);
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

    /** 显示本次已选词条：逐条渲染“名称 + 价值数字 / 分隔线 / 描述”，并从右上角显示价值总数。 */
    private void showDetailContent(boolean animateBoxes){
        detailList.visible = true;
        detailList.clearChildren();

        float off = Core.graphics.getWidth(); // 从右侧屏幕外滑入的距离
        int idx = 0;
        for(TriPresetMode.Entry e : TriPresetMode.selectedEntries()){
            // 每个词条单独一个 table（便于单独做从右滑入的动画）
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

            detailList.add(box).growX().padBottom(8f).row();

            if(animateBoxes){
                box.setTranslation(off, 0f);
                box.addAction(Actions.sequence(Actions.delay(idx * 0.03f),
                        Actions.translateBy(-off, 0f, 0.3f, Interp.pow2Out)));
            }
            idx++;
        }

        valueLabel.setText(String.valueOf(TriPresetMode.selectedValueTotal()));
        valueRow.visible = true;
    }

    /** 回到地图视图：缩略图（必要时反向 morph 回来）+ 隐藏详情与价值总数与地图按钮。 */
    private void showMapState(boolean animate){
        detailList.visible = false;
        valueRow.visible = false;
        setMapButton(false, null);
        previewMap = false;

        if(!thumbShown){
            if(animate){
                morphThumbFromButton();
            }else{
                mapThumb.visible = true;
                mapThumb.setTranslation(0f, 0f);
                mapThumb.setScale(1f, 1f);
                mapThumb.setColor(1f, 1f, 1f, 1f);
            }
            thumbShown = true;
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
            // 切回地图视图：缩略图反向 morph 回来，隐藏详情/价值总数，按钮改提示“词条”
            previewMap = true;
            morphThumbFromButton();
            thumbShown = true;
            detailList.visible = false;
            valueRow.visible = false;
            setMapButton(true, Core.bundle.get("tri.preset.detailButton", "词条"));
        }else{
            // 回到词条详情：缩略图再 morph 成按钮
            previewMap = false;
            morphThumbToButton();
            thumbShown = false;
            showDetailContent(true);
            setMapButton(true, Core.bundle.get("tri.preset.mapButton", "地图"));
            extendDividerFromRight();
        }
    }

    // ============================ 工具 ============================

    /** 同步某一类别的按钮选中态（同类互斥由 TriPresetMode 保证，其余类别不动）。 */
    private void refresh(int category){
        TextButton[] buttons = optionButtons[category];
        for(int i = 0; i < buttons.length; i++){
            buttons[i].setChecked(TriPresetMode.isSelected(category, i));
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
}
