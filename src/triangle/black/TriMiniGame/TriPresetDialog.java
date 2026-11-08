package triangle.black.TriMiniGame;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
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
 *       组间有竖向分割线；列表由上下两条固定的横向分割线夹住（不再使用整体描边框），
 *       内容整体可横向滑动（滚轮或拖拽），超出视口的部分由 ScrollPane 裁掉；</li>
 *   <li>竖分割线：单独一条，画在右侧详情区左边框处，两侧各留 {@link #DIVIDER_PAD} 空隙；</li>
 *   <li>右侧：地图组，中心位于屏幕 3/4 附近（与 Tmail 的 content 卡片组同位置），
 *       自上而下为 地图名称 → 地图缩略图（与该词条详情共用区域）→ 分割线 → “开始游戏”按钮。</li>
 * </ul>
 *
 * <p>玩家改变某类词条后，右侧缩略图与名称隐藏，改为显示该词条详情文案；
 * 当所有词条都未选中（取消勾选回到默认态）时，右侧重新回到地图缩略图。
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
    /** 框住词条列表的上下两条分割线、以及详情区左边框那条竖分割线的不透明度（比组间线更实一些）。 */
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

    private static TriPresetDialog instance;

    /** 选项按钮引用（[类别][选项]），供切换后仅同步该类别的按钮选中态。 */
    private final TextButton[][] optionButtons = new TextButton[TriPresetMode.categoryCount()][];
    /** 缩略图是否已换成真实预览（预览异步生成，先用占位图）。 */
    private boolean thumbLoaded;

    private Label mapName;
    private Image mapThumb;
    /** 已选词条的总效果：每个词条一条 whiteOutlined 描边框，等宽逐行排列。 */
    private Table detailList;

    public static void open(){
        if(instance == null) instance = new TriPresetDialog();
        // 地图预览由原版异步加载/生成；先发起请求，纹理就绪后由 update 回调替换占位图
        if(Vars.maps != null) Vars.maps.loadPreviews();
        instance.show();
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
        // 竖分割线之前，也就是右侧详情区左边框的位置；高度仍贴着按钮内容走，不拉满整组 0.6 屏高
        Table leftGroup = new Table();
        leftGroup.add(rule()).growX().height(SEP_WIDTH).padTop(4f).padBottom(6f).row();
        leftGroup.add(scroller).growX().maxHeight(groupH).padLeft(6f).padRight(6f).row();
        leftGroup.add(rule()).growX().height(SEP_WIDTH).padTop(6f).padBottom(4f);

        // ---- 右 1/4：地图组（名称 / 缩略图或详情 / 分割线 / 开始游戏） ----
        Table rightGroup = new Table();

        mapName = new Label(bundle("tri.preset.mapName"), Styles.defaultLabel);
        mapName.setAlignment(Align.center);

        Map map = TriPresetMode.map();
        mapThumb = new Image(preview(map), Scaling.fit);

        // 词条总效果：每条描述各自套一个 whiteOutlined 描边框，逐行等宽排列
        detailList = new Table();
        detailList.top();
        detailList.visible = false;

        // 缩略图与详情共用同一块区域：切换显隐即可互换。
        // 缩略图包一层居中容器并只占信息区的一部分（THUMB_SIZE_RATIO），避免铺满整个右组。
        float thumbSize = Math.min(groupW, groupH) * THUMB_SIZE_RATIO;
        Table thumbHolder = new Table();
        thumbHolder.add(mapThumb).size(thumbSize).center();

        Stack pane = new Stack();
        pane.add(thumbHolder);
        pane.add(detailList);

        rightGroup.add(mapName).padTop(8f).row();
        rightGroup.add(pane).grow().pad(6f).row();

        Image divider = new Image(Core.atlas.find("white"));
        divider.setColor(1f, 1f, 1f, 0.35f);
        rightGroup.add(divider).growX().height(2f).padTop(4f).padBottom(8f).row();

        TextButton start = new TextButton(bundle("tri.preset.start"), Styles.defaultt);
        start.clicked(() -> {
            hide();
            TriPresetMode.start();
        });
        rightGroup.add(start).size(260f, 54f).padBottom(8f);

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

    /** 显示本次已选词条的总效果：隐藏地图名称与缩略图，改为逐条列出各词条描述。 */
    private void showDetail(){
        mapName.visible = false;
        mapThumb.visible = false;

        detailList.clearChildren();
        for(String text : TriPresetMode.selectedDetails()){
            Table box = new Table();
            box.setBackground(TriDraw.whiteOutlined(Styles.black6));

            Label label = new Label(text, Styles.defaultLabel);
            label.setWrap(true);
            label.setAlignment(Align.center);
            box.add(label).growX().pad(10f);

            detailList.add(box).growX().padBottom(8f).row();
        }
        detailList.visible = true;
    }

    /** 回到地图视图：隐藏详情，重新显示地图名称与缩略图。 */
    private void showMap(){
        detailList.visible = false;
        mapName.visible = true;
        mapThumb.visible = true;
    }

    /** 生成一个词条按钮：点击即切换该选项，并同步同类按钮的选中态与右侧信息栏。 */
    private TextButton optionButton(int category, int option){
        TextButton button = new TextButton(TriPresetMode.optionLabel(category, option), Styles.togglet);
        button.setChecked(TriPresetMode.isSelected(category, option));
        button.clicked(() -> {
            TriPresetMode.toggle(category, option);
            refresh(category);
            // 有选中项时显示“已选词条的总效果”；全都取消后回到地图缩略图
            if(TriPresetMode.hasSelection()){
                showDetail();
            }else{
                showMap();
            }
        });
        return button;
    }

    /** 词条组之间的竖向分割线（高度随所在行最高的一组按钮）。 */
    private static Image separator(){
        Image sep = new Image(Core.atlas.find("white"));
        sep.setColor(1f, 1f, 1f, SEP_ALPHA);
        return sep;
    }

    /** 框住词条列表的横向分割线、详情区左边框的竖向分割线（比组间线更实）。 */
    private static Image rule(){
        Image line = new Image(Core.atlas.find("white"));
        line.setColor(1f, 1f, 1f, RULE_ALPHA);
        return line;
    }

    /** 同步某一类别的按钮选中态（同类互斥由 TriPresetMode 保证，其余类别不动）。 */
    private void refresh(int category){
        TextButton[] buttons = optionButtons[category];
        for(int i = 0; i < buttons.length; i++){
            buttons[i].setChecked(TriPresetMode.isSelected(category, i));
        }
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
