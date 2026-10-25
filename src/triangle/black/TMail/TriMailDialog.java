package triangle.black.TMail;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Action;
import arc.scene.actions.Actions;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.style.Drawable;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import mindustry.gen.Tex;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import triangle.black.TriDraw;
import triangle.black.TriSetting;

/**
 * 三角通讯收件箱界
 * 每封邮件固定大小卡片（圆角矩形包裹：上发件人、中分割线、下内容，过长省略）
 * 卡片竖直向下居中排列、可滚动；顶部居中分类栏（带未读角标）：全部 / 已读 / 未读 / 历史
 * 列表为空时显示灰色提示；详情页限
 * 打开时先在页面内播放 TmailLogo 入场动画，动画结束后邮件卡片组滑
 */
public class TriMailDialog{

    private enum Filter{ ALL, READ, UNREAD, HISTORY }

    private static final float CARD_WIDTH = 480f;
    private static final float CARD_HEIGHT = 76f;
    private static final float DETAIL_WIDTH = 560f;

    /** TmailLogo 分帧滑入时长（秒
*/
    private static final float LOGO_PIECE_DURATION = 0.55f;
    /** TmailLogo-2 相对 -1 的出发延迟（秒），形成上下交错拼
*/
    private static final float LOGO_PIECE_STAGGER = 0.08f;
    /** 两片拼合后到完整 Logo 替换之间的停顿（秒）
*/
    private static final float LOGO_ASSEMBLE_HOLD = 0.12f;
    /** 完整 TmailLogo 从中央移动到左侧停靠位的时长（秒
*/
    private static final float LOGO_COVER_MOVE_DURATION = 0.5f;
    /** 分帧滑入前与拼合时的放大倍数
*/
    private static final float LOGO_ASSEMBLE_SCALE = 1.15f;
    /** 分帧起点超出页面边缘的距离（像素
*/
    private static final float LOGO_OFFSCREEN_MARGIN = 24f;
    /** TmailLogo 宽度占屏幕宽度的比例
*/
    private static final float LOGO_WIDTH_RATIO = 0.56f;
    /** TmailLogo 最大宽度（像素），避免大屏上过
*/
    private static final float LOGO_MAX_WIDTH = 1000f;
    /** TmailLogo 停靠 x 比例：屏幕左
1/4 处（右侧 3/4 留给收件箱内容）
*/
    private static final float LOGO_TARGET_X_RATIO = 0.25f;
    /** 打开/关闭信件详情时，TmailLogo 移动到屏幕中
返回左侧的时长（秒）
*/
    private static final float LOGO_DETAIL_MOVE_DURATION = 0.3f;
    /** 当前 TmailLogo 层：动画结束后保留为页面左侧品牌标识，关闭页面时移除
*/
    private static Table logoIntro;
    /** 最
TmailLogo 所在的 Table（可整体移动，供详情打开时移到屏幕中央）
*/
    private static Table tmailLogoTable;
    /** TmailLogo 的左侧停靠位与中央位（相
inbox.cont 的局部坐标）
*/
    private static float logoHomeX, logoHomeY, logoCenterX, logoCenterY;

    private static BaseDialog inbox;
    private static Filter filter = Filter.ALL;
    /** 当前收件箱作用域：true=战役剧情邮件（战役星
TMail）；false=模组资讯邮件（主菜单 TMail
*/
    private static boolean storyScope = true;
    /** 收件箱打开时的全屏毛玻璃背
*/
    private static TriBlurBackground blur;
    /** 收件箱内容整
Table（供整体滑入动画
*/
    private static Table lastContent;
    /** 当前打开的信件详
table（画面中心），以及其对应的缩略卡片（用于关闭时恢复显示）
*/
    private static Table activeDetail, activeDetailCard;
    /** 详情根节点内的实际卡片内容（负责在缩略图/详情两种外观间切换）
*/
    private static Table activeDetailMorph;
    /** 详情卡片背后 / 返回缩略图时的全屏灰色半透明背景
*/
    private static Table activeDetailBg;
    /** 详情打开/关闭的形变动画时长（秒）
*/
    private static final float MORPH_DURATION = 0.45f;
    /** 当前详情对应的邮件（关闭动画中途重建缩略图外观时使用）
*/
    private static TriMail activeDetailMail;
    /** 对应缩略图在屏幕上的起始位置/尺寸（关闭时 morph table 回到这里
*/
    private static float detailStartX, detailStartY, detailStartW, detailStartH;
    /** 防止关闭动画期间重复触发关闭
*/
    private static boolean closingDetail;
    /** 回信面板相关：详情下方的回信按钮、左侧空白信纸、信纸下方的发信按钮
*/
    private static TextButton activeReplyButton, activeReplySend;
    private static TextButton activeReplyMiniButton, activeReplyCollapseButton;
    private static Table activeReplyPaper;
    private static Table activeReplyPaperContent;
    private static TriMail activeReplyMail;
    /** 回信纸收起按钮尺
/ 收起展开动画时长
*/
    private static final float REPLY_MINI_SIZE = 44f;
    private static final float REPLY_ICON_SIZE = 20f;
    private static final float REPLY_ICON_FADE = 0.15f;
    private static final float REPLY_COLLAPSE_DURATION = 0.35f;
    /** 收起/展开动画进行中，防止重复点击
*/
    private static boolean replyToggleAnimating;
    /** 回信纸展开/按钮滑出/状态停
打字机延迟时长（秒）
*/
    private static final float REPLY_LAYOUT_DURATION = 0.35f;
    private static final float REPLY_SLIDE_DURATION = 0.25f;
    private static final float REPLY_PAPER_DELAY = 0.4f;
    private static final float REPLY_SENT_DURATION = 1.5f;
    private static final float REPLY_PAPER_EXIT_DURATION = 0.2f;
    private static final float REPLY_GAP = 16f;
    /** 详情背后的全屏透明按钮：点击详情卡片外的部

触发关闭详情（置于收件箱之上、详情之下）
*/
    private static Table activeBackdrop;
    /** 分类栏四个按钮引用（
ALL/READ/UNREAD/HISTORY 顺序），
refreshReadState 更新未读角标
*/
    private static final TextButton[] catButtons = new TextButton[4];
    private static final Filter[] CATS = { Filter.ALL, Filter.READ, Filter.UNREAD, Filter.HISTORY };
    /** 当前邮件列表 Table（scroll 内容），供“未读”分类下移除已读卡片（轻量刷新，不重建收件箱
*/
    private static Table listTable;
    /** 已构建卡片的发件
Label 引用（mail
sender Label），
refreshReadState 局部更新未
已读高亮
*/
    private static final ObjectMap<TriMail, Label> senderLabels = new ObjectMap<>();
    /** 收件箱内容的“家”位置（滑入目标 = 屏幕
3/4 处）：打开详情时向右滑出、关闭详情后滑回此处
*/
    private static float contentHomeX, contentHomeY;
    /** 收件箱内容滑入时长（先快后慢
*/
    private static final float SLIDE_IN_DURATION = 0.4f;
    /** 打开/关闭详情时，背景 content 滑出（加速）/滑回（减速）时长（≤0.2s
*/
    private static final float CONTENT_SWEEP_DURATION = 0.2f;
    /** Tmail 退出时 logo/content 高速移出时长。 */
    private static final float TMAIL_EXIT_DURATION = 0.1f;
    private static boolean exitAnimating;

    /** Tmail 动画时长按设置倍率缩放。 */
    private static float anim(float seconds){
        return seconds / TriSetting.tmailAnimMultiplier();
    }

    private static Action moveTo(float x, float y, float seconds, Interp interp){
        return Actions.moveTo(x, y, anim(seconds), interp);
    }

    private static Action sizeTo(float width, float height, float seconds, Interp interp){
        return Actions.sizeTo(width, height, anim(seconds), interp);
    }

    private static Action scaleTo(float sx, float sy, float seconds, Interp interp){
        return Actions.scaleTo(sx, sy, anim(seconds), interp);
    }

    private static Action delay(float seconds){
        return Actions.delay(anim(seconds));
    }

    private static Action alpha(float value, float seconds, Interp interp){
        return Actions.alpha(value, anim(seconds), interp);
    }

    private TriMailDialog(){}

    /** 打开战役剧情收件箱（战役星球 TMail）：先播
TmailLogo 入场动画
*/
    public static void show(){
        showWithIntro(true);
    }

    /**
     * 先播
TmailLogo 入场动画，再打开战役剧情收件
     */
    public static void showWithIntro(){
        showWithIntro(true);
    }

    /**
     * 先打开 Tmail 页面，再播放 TmailLogo 入场动画；动画结束后邮件卡片组滑
     *
     * <p>流程：立即创建并显示收件箱窗口（毛玻璃背
+ 空内容）
TmailLogo-1 / -2
     * 分别从页面上方、下方滑入并在中央拼

完整 TmailLogo 覆盖替换两片并停靠到页面左侧
     * （作为品牌标识）
移除遮罩
构建邮件卡片并从右侧滑入
     * 若收件箱已经打开，则直接刷新，不重复播放动画
     */
    public static void showWithIntro(boolean story){
        if(Core.scene == null){
            show(story);
            return;
        }
        if(inbox != null && inbox.isShown()){
            show(story);
            return;
        }

        openInbox(story);
        playLogoIntro(story);
    }

    /**
     * 立即打开收件箱窗口，但不构建邮件卡片
     * 用于
TmailLogo 动画先显示在已打开
Tmail 页面内，动画结束后再调用 {@link #build(BaseDialog)}
     */
    private static void openInbox(boolean story){
        storyScope = story;

        if(inbox == null){
            inbox = new BaseDialog(""){
                @Override
                public void hide(){
                    if(exitAnimating) return;
                    playExitAnimation(this::hideImmediately);
                }

                private void hideImmediately(){
                    super.hide();
                }
            };
            inbox.addCloseButton();
            // 收件箱关闭时移除毛玻璃背景、Logo 层，并同步关闭可能仍打开的信件详情与透明按钮
            inbox.hidden(() -> {
                exitAnimating = false;
                cancelLogoIntro();
                if(activeDetail != null){
                    activeDetail.remove();
                    activeDetail = null;
                    activeDetailMorph = null;
                    activeDetailCard = null;
                }
                activeDetailMail = null;
                closingDetail = false;
                removeDetailOverlays();
                removeReplyUi();
                if(blur != null){
                    blur.remove();
                    blur = null;
                }
            });
        }
        inbox.title.setText(story ? bundle("tri.mail.title") : bundle("tri.modinfo.title"));
        // 上一次退出时冻结过父布局，重新打开时恢复
        inbox.cont.setLayoutEnabled(true);

        // 全屏毛玻璃背景（截屏 + 模糊 + 透明度渐深），置于收件箱窗口之下
        if(blur == null || !blur.hasParent()){
            blur = new TriBlurBackground();
            Core.scene.add(blur);
        }

        // 动画期间先移除旧内容，但保留可能存在 Logo
        clearPage();
        inbox.show();
    }

    /**
     * Tmail 退出动画：logo 向左移出、content 向右移出，线性高速，不做缓动。
     */
    private static void playExitAnimation(Runnable done){
        if(exitAnimating) return;
        exitAnimating = true;
        // 退出动画期间冻结父布局，避免父 Table 重新布局把 content 拉回“家”位置造成闪回
        if(inbox != null){
            inbox.cont.setLayoutEnabled(false);
        }

        // 退出时立即收掉详情与回信浮层，避免遮挡退场
        if(activeDetail != null){
            activeDetail.remove();
            activeDetail = null;
            activeDetailMorph = null;
            activeDetailCard = null;
            activeDetailMail = null;
        }
        closingDetail = false;
        removeDetailOverlays();
        removeReplyUi();

        float sw = Core.graphics.getWidth();
        if(tmailLogoTable != null){
            tmailLogoTable.clearActions();
            tmailLogoTable.actions(moveTo(-tmailLogoTable.getWidth() - 24f, tmailLogoTable.y,
                TMAIL_EXIT_DURATION, Interp.linear));
        }else if(logoIntro != null){
            logoIntro.clearActions();
            logoIntro.actions(moveTo(-sw, logoIntro.y, TMAIL_EXIT_DURATION, Interp.linear));
        }

        if(lastContent != null){
            lastContent.clearActions();
            lastContent.actions(moveTo(sw + lastContent.getWidth(), lastContent.y,
                TMAIL_EXIT_DURATION, Interp.linear));
        }

        Time.runTask(anim(TMAIL_EXIT_DURATION) * 60f + 1f, () -> {
            exitAnimating = false;
            if(done != null) done.run();
        });
    }

    /**
     * 在已打开Tmail 页面内播TmailLogo 组合动画
     *
     * <p>与开始界面（TriWelcome）类似：
     * TmailLogo-1 从页面上方滑入、TmailLogo-2 从页面下方滑入，两片在页面中央拼合；
     * 随后完整TmailLogo 覆盖并替换两片，移动到页面左侧停靠；
     * 动画结束后移除黑色遮罩并构建/滑入邮件卡片
     *
     * <p>整个 Logo 层是 {@code inbox.cont} 的子节点，与 content 一样位Tmail 页面内部
     */
    private static void playLogoIntro(boolean story){
        cancelLogoIntro();

        float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        TextureRegion piece1Tex = TriDraw.tex("Tmail/TmailLogo-1");
        TextureRegion piece2Tex = TriDraw.tex("Tmail/TmailLogo-2");
        TextureRegion coverTex = TriDraw.tex("Tmail/TmailLogo");
        float logoW = Math.min(sw * LOGO_WIDTH_RATIO, LOGO_MAX_WIDTH);
        float logoH = logoW * coverTex.height / Math.max(coverTex.width, 1);

        // 让页面先完成布局，拿 cont 的实际尺 位置
        inbox.validate();
        inbox.cont.validate();
        float pageW = Math.max(inbox.cont.getWidth(), 1f);
        float pageH = Math.max(inbox.cont.getHeight(), 1f);

        // Logo 层作 Tmail 页面 cont 的子节点，而不是直接挂 Core.scene
        Table layer = new Table();
        layer.setBounds(0f, 0f, pageW, pageH);
        layer.setLayoutEnabled(false);
        layer.touchable = Touchable.enabled; // 动画期间拦截点击，避免误触底层页
        inbox.cont.addChild(layer);

        // 半透明黑底：TmailLogo 为白色，压暗背景以保证可读性；动画结束后移
        Image dim = new Image(Core.atlas.find("white"));
        dim.setSize(pageW, pageH);
        dim.setColor(0f, 0f, 0f, 0.35f);
        dim.touchable = Touchable.disabled;
        layer.addChild(dim);

        //  content 的屏幕坐标换算成 cont 局部坐
        float centerX = sw / 2f - inbox.x - inbox.cont.x;
        float centerY = sh / 2f - inbox.y - inbox.cont.y;
        float targetX = sw * LOGO_TARGET_X_RATIO - inbox.x - inbox.cont.x;
        float targetY = centerY;

        float pieceX = centerX - logoW / 2f;
        float pieceY = centerY - logoH / 2f;
        float topStartY = pageH + LOGO_OFFSCREEN_MARGIN;
        float bottomStartY = -logoH - LOGO_OFFSCREEN_MARGIN;

        Image piece1 = new Image(piece1Tex);
        piece1.setSize(logoW, logoH);
        piece1.setOrigin(Align.center);
        piece1.setScale(LOGO_ASSEMBLE_SCALE);
        piece1.setPosition(pieceX, topStartY);
        layer.addChild(piece1);

        Image piece2 = new Image(piece2Tex);
        piece2.setSize(logoW, logoH);
        piece2.setOrigin(Align.center);
        piece2.setScale(LOGO_ASSEMBLE_SCALE);
        piece2.setPosition(pieceX, bottomStartY);
        layer.addChild(piece2);

        piece1.actions(moveTo(pieceX, pieceY, LOGO_PIECE_DURATION, Interp.pow2Out));
        piece2.actions(
            delay(LOGO_PIECE_STAGGER),
            moveTo(pieceX, pieceY, LOGO_PIECE_DURATION, Interp.pow2Out)
        );

        float assembleAt = LOGO_PIECE_STAGGER + LOGO_PIECE_DURATION + LOGO_ASSEMBLE_HOLD;
        layer.actions(delay(assembleAt), Actions.run(() -> {
            if(logoIntro != layer) return;

            // 完整 TmailLogo 覆盖并替换两片分帧；放入独立 Table，便于详情打开时整体移
            Image cover = new Image(coverTex);
            cover.setSize(logoW, logoH);
            cover.setOrigin(Align.center);
            cover.setScale(LOGO_ASSEMBLE_SCALE);

            Table logoTable = new Table();
            logoTable.add(cover).size(logoW, logoH);
            logoTable.pack();
            logoTable.setOrigin(Align.center);
            logoTable.setPosition(pieceX, pieceY);
            layer.addChild(logoTable);
            tmailLogoTable = logoTable;

            piece1.remove();
            piece2.remove();
            dim.remove();

            // 记录停靠 中央位（相对 inbox.cont 的局部坐标）
            logoHomeX = targetX - logoW / 2f;
            logoHomeY = targetY - logoH / 2f;
            logoCenterX = pieceX;
            logoCenterY = pieceY;

            // 类似 TriWelcome：先慢后快滑入停靠位，同时缩放回正常大小
            logoTable.actions(
                moveTo(logoHomeX, logoHomeY, LOGO_COVER_MOVE_DURATION, Interp.pow2In),
                Actions.run(() -> {
                    if(logoIntro != layer) return;
                    layer.touchable = Touchable.disabled;
                    // 兜底：正常情况下 content 已在下面提前构建；若因关闭等原因未构建，则这里补一
                    if(inbox != null && inbox.isShown() && lastContent == null){
                        build(inbox);
                    }
                })
            );
            cover.actions(scaleTo(1f, 1f, LOGO_COVER_MOVE_DURATION, Interp.pow2In));

            // 调整 content 入场时机：content 滑入（SLIDE_IN_DURATION）结 + 1 tick 延迟
            // 恰好 Logo 滑到停靠位（LOGO_COVER_MOVE_DURATION）同一时刻
            float contentStartDelay = Math.max(0f, LOGO_COVER_MOVE_DURATION - SLIDE_IN_DURATION - 1f / 60f);
            layer.actions(delay(contentStartDelay), Actions.run(() -> {
                if(logoIntro == layer && inbox != null && inbox.isShown() && lastContent == null){
                    build(inbox);
                }
            }));
        }));

        logoIntro = layer;
    }

    /** 移除当前 TmailLogo 层（关闭页面、重复打开或直接刷新时调用
*/
    private static void cancelLogoIntro(){
        if(logoIntro != null){
            logoIntro.remove();
            logoIntro = null;
        }
        tmailLogoTable = null;
    }

    /** 打开/关闭信件详情时，TmailLogo 移到屏幕中央或移回左侧停靠位*/
    private static void moveLogoToDetail(boolean centered){
        if(tmailLogoTable == null) return;
        float x = centered ? logoCenterX : logoHomeX;
        float y = centered ? logoCenterY : logoHomeY;
        tmailLogoTable.clearActions();
        tmailLogoTable.actions(moveTo(x, y, LOGO_DETAIL_MOVE_DURATION, Interp.pow2Out));
    }

    /** 打开收件箱并立即构建内容（不播放 Logo 动画；供程序化刷新使用）。story=true 剧情 / false 资讯*/
    public static void show(boolean story){
        openInbox(story);
        build(inbox);
    }

    /** 重建收件箱内容（删除content 后，新建 content 并从屏幕右外侧滑入右 3/4 处）
     *  删除content 的逻辑不内置在本方法——调用方须在调用前自行移除旧 content
     *  （不同调用方可按不同样式删除：直接清空、淡出、滑出等
*/
    private static void build(BaseDialog dlg){
        // 清空 content 的单元格（Element.remove 不会移除 Table  cell），再放 Logo
        if(lastContent != null){
            clearPage();
        }
        senderLabels.clear(); // 旧卡片引用作废（即将重建
        Seq<TriMail> all = TriInbox.unlocked(storyScope);
        rebuildContent(dlg, all, SLIDE_IN_DURATION);
    }

    /**
     * 清空 Tmail 页面内的 content 单元格，但保TmailLogo
     * Table.clearChildren 会连cell 一起清除；因此先保 Logo 层，清空后再重新挂回
     */
    private static void clearPage(){
        Table logo = logoIntro;
        if(inbox != null){
            inbox.cont.clearChildren();
            if(logo != null){
                inbox.cont.addChild(logo);
            }
        }
        lastContent = null;
    }

    /** 新建收件content 卡片列表：在屏幕右外侧新建（分类+ 卡片列表），随后滑入3/4
     *  不内置删除旧 content 的逻辑——调用方须在调用前自行移除旧 content（可按不同样式删除）
     *  @param dlg 收件箱对话框；@param all 已解锁邮件列表快照；@param slideDuration 滑入时长（秒），可定制效
*/
    private static void rebuildContent(BaseDialog dlg, Seq<TriMail> all, float slideDuration){
        // 整个收件箱内容包成一 Table（便于整体滑入动画与后续改进
        Table content = new Table();
        content.top();

        // 顶部居中的分类栏（放大按 + 未读角标
        Table bar = new Table();
        bar.center();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        addCategory(dlg, bar, group, Filter.ALL, "tri.mail.cat.all", all);
        addCategory(dlg, bar, group, Filter.READ, "tri.mail.cat.read", all);
        addCategory(dlg, bar, group, Filter.UNREAD, "tri.mail.cat.unread", all);
        addCategory(dlg, bar, group, Filter.HISTORY, "tri.mail.cat.history", all);
        // 刷新按钮（暂未启用）：手动刷新已 未读状态（角标 + 未读列表），样式同分类按钮；不入 ButtonGroup（非分类切换
        // 【暂时注释】待用户决定是否恢复
        // TextButton refreshBtn = new TextButton(bundle("tri.mail.refresh"));
        // refreshBtn.clicked(() -> refreshReadState());
        // bar.add(refreshBtn).size(96f, 42f).pad(3);
        content.add(bar).padTop(8).padBottom(4).row();

        // 竖直向下的邮件列表（居中），可滚
        Table list = new Table();
        list.center();
        Seq<TriMail> shown = mailsFor(filter, all);
        if(shown.isEmpty()){
            list.add("[#7f8c8d]" + bundle("tri.mail.empty")).pad(30);
        }else{
            for(TriMail m : shown){
                list.row();
                list.add(card(m)).size(CARD_WIDTH, CARD_HEIGHT).pad(3);
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setOverscroll(false, false);
        content.add(scroll).grow().pad(4);
        listTable = list; // 记录列表引用，供 refreshReadState 在“未读”分类下移除已读卡片

        dlg.cont.add(content).grow();
        // 先让父布局（cont）把 content 排到正确尺寸/位置（grow 填满），再让 content 子元素布局
        // 随后冻结布局：位置改由手动控制（供整体滑入动画，不被 Table 布局覆盖
        dlg.cont.validate();
        content.validate();
        content.setLayoutEnabled(false);
        lastContent = content;

        // 详情页存在时，点 Tmail 主界面（收件箱区域） 触发详情页关闭（避免叠加次序问题
        //  touchDown（按下瞬间）：先于子元素（卡片） touchUp/clicked 触发
        // 因此点卡片时先关闭旧详情、随后卡片再打开新详情，不会误关新开的详
        content.addListener(new ClickListener(){
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(activeDetail != null){
                    closeDetail(activeDetail, activeDetailCard);
                }
                return false; // 不拦截事件，继续向子元素传播
            }
        });

        // 方案1：延迟期间隐 content（本帧完全不可见，杜绝“中间闪 闪烁”）
        // 延迟一帧后（此 Dialog.show()/父布局已稳定）在回调内定位屏幕右外 + 注册滑入动画
        // 定位动作发生在布局稳定之后，不会再被父 cont 的布局 validate 覆盖（setLayoutEnabled 只冻 content 自身布局，不阻止父重排）
        content.visible = false;
        Time.runTask(1f, () -> {
            // 若延迟期 content 已被重建（lastContent 换新），跳过本次旧滑入任务，由新 build 自己的滑入处
            if(!dlg.isShown() || lastContent != content) return;
            float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
            float cw = lastContent.getWidth(), ch = lastContent.getHeight();
            // 目标：content 中心位于屏幕右半屏中心（ 3/4 处）
            float targetScreenX = sw * 0.75f, targetScreenY = sh / 2f;
            // content 坐标是相 cont 的局部坐标：
            // 局部目 = 屏幕目标 - dialog 左下 dlg.x/y) - cont 相对 dialog 偏移(dlg.cont.x/y) - content 半宽/半高
            float targetLocalX = targetScreenX - dlg.x - dlg.cont.x - cw / 2f;
            float targetLocalY = targetScreenY - dlg.y - dlg.cont.y - ch / 2f;
            contentHomeX = targetLocalX; // 记录“家”位置：打开详情 content 向右滑出、关闭详情后滑回此处
            contentHomeY = targetLocalY;
            lastContent.setPosition(sw + cw, targetLocalY); // 起点：屏幕右外侧（布局已稳定，定位生效
            lastContent.visible = true; // 恢复可见，随即滑
            lastContent.clearActions();
            lastContent.actions(moveTo(targetLocalX, targetLocalY, slideDuration, Interp.pow2Out));
        });
    }

    private static void addCategory(BaseDialog dlg, Table bar, ButtonGroup<TextButton> group, Filter f, String key, Seq<TriMail> all){
        // 角标：该分类结果集中未读邮件的数量（>0 才显示）
        int unread = unreadIn(all, f);
        String text = bundle(key);
        if(unread > 0) text += " [accent](" + unread + ")";

        TextButton b = new TextButton(text);
        group.add(b);
        b.setChecked(filter == f);
        b.clicked(() -> {
            filter = f;
            build(dlg);
        });
        bar.add(b).size(96f, 42f).pad(3);
        catButtons[catIndex(f)] = b; // 记录引用，供 refreshReadState 更新未读角标
    }

    /** 分类栏按钮下标（ALL/READ/UNREAD/HISTORY 顺序
*/
    private static int catIndex(Filter f){
        for(int i = 0; i < 4; i++) if(CATS[i] == f) return i;
        return 0;
    }

    /** 仅刷新已
未读状态：更新分类栏未读角标；局部更新已存在卡片的发件人高亮；若当前是“未读”分类，将已读信件从列表移除
     *  不重建整个收件箱（不 clearChildren cont），因此不会打断 content 的滑回动
*/
    private static void refreshReadState(){
        if(inbox == null || !inbox.isShown()) return;
        Seq<TriMail> all = TriInbox.unlocked(storyScope);
        // 更新四个分类按钮的未读角
        for(int i = 0; i < 4; i++){
            TextButton b = catButtons[i];
            if(b == null) continue;
            int unread = unreadIn(all, CATS[i]);
            String text = bundle("tri.mail.cat." + CATS[i].name().toLowerCase());
            if(unread > 0) text += " [accent](" + unread + ")";
            b.setText(text);
        }
        // 局部更新已存在卡片的发件人高亮（已  普通色，未  accent），不动卡片布局
        for(ObjectMap.Entry<TriMail, Label> e : senderLabels){
            TriMail m = e.key;
            Label lbl = e.value;
            lbl.setText(TriInbox.isRead(m) ? m.sender : "[accent]" + m.sender);
        }
        // “未读”分类：移除已读信件（重建列表内容；不影 content 滑回动画
        if(filter == Filter.UNREAD && listTable != null){
            listTable.clearChildren();
            boolean any = false;
            for(TriMail m : all){
                if(!TriInbox.isRead(m)){
                    listTable.row();
                    listTable.add(card(m)).size(CARD_WIDTH, CARD_HEIGHT).pad(3);
                    any = true;
                }
            }
            if(!any){
                listTable.add("[#7f8c8d]" + bundle("tri.mail.empty")).pad(30);
            }
        }
    }

    /** 单封邮件的固定大小卡片：圆角矩形包裹，上发件人、中分割线、下内容（省略）。整卡可点击阅读
*/
    private static Table card(TriMail mail){
        Table card = new Table();
        card.top().left();
        // 圆角矩形包裹 + 白色描边（复 TriDraw.whiteOutlined 的圆角描边绘制）
        card.background(TriDraw.whiteOutlined(Styles.grayPanel));

        // 文本单元格必须显式定宽（不能 growX），否则列宽被最长文本撑大、ellipsis 无法触发
        float innerW = CARD_WIDTH - 28;
        boolean read = TriInbox.isRead(mail);

        // 上一行：发件人（未读时高亮），顶部左对齐
        String senderText = read ? mail.sender : "[accent]" + mail.sender;
        Label sender = new Label(senderText);
        sender.setEllipsis(true);
        sender.setAlignment(Align.topLeft);
        card.add(sender).top().left().width(innerW).height(32).padLeft(14).padRight(14).padTop(7).row();
        senderLabels.put(mail, sender); // 记录引用，供 refreshReadState 局部刷新未 已读高亮

        // 分割线：发件人与内容之间
        Image divider = new Image(Tex.whiteui, Color.gray);
        card.add(divider).growX().height(2).padLeft(14).padRight(14).row();

        // 下一行：内容（过长省略，换行折叠为单行），顶部左对齐
        Label content = new Label(mail.body.replace('\n', ' '));
        content.setEllipsis(true);
        content.setAlignment(Align.topLeft);
        card.add(content).top().left().width(innerW).height(28).padLeft(14).padRight(14).padBottom(7);

        card.clicked(() -> openMail(mail, card));
        return card;
    }

    /** 构建与缩略图一致的卡片外观（不注册点击、不覆盖 senderLabels），供详情形变动画使
*/
    private static void buildCardContent(Table card, TriMail mail){
        buildCardContent(card, mail, TriInbox.isRead(mail));
    }

    private static void buildCardContent(Table card, TriMail mail, boolean read){
        card.top().left();
        card.background(TriDraw.whiteOutlined(Styles.grayPanel));

        float innerW = CARD_WIDTH - 28;

        Label sender = new Label(read ? mail.sender : "[accent]" + mail.sender);
        sender.setEllipsis(true);
        sender.setAlignment(Align.topLeft);
        card.add(sender).top().left().width(innerW).height(32).padLeft(14).padRight(14).padTop(7).row();

        Image divider = new Image(Tex.whiteui, Color.gray);
        card.add(divider).growX().height(2).padLeft(14).padRight(14).row();

        Label content = new Label(mail.body.replace('\n', ' '));
        content.setEllipsis(true);
        content.setAlignment(Align.topLeft);
        card.add(content).top().left().width(innerW).height(28).padLeft(14).padRight(14).padBottom(7);
    }

    /**
     * 打开一封邮件：从被点击缩略卡片的屏幕位置生成一
morph table
     * 先保持卡片外观移动到画面中央，途中切换为详情内
     */
    private static void openMail(TriMail mail, Table mailCard){
        // 先记录点击时的已读外观，确保 morph 初始缩略图与用户看到的卡片一
        boolean wasRead = TriInbox.isRead(mail);
        TriInbox.markRead(mail);

        // 打开新详情前立即结束旧详情（避免两个形变 table 叠加
        if(activeDetail != null){
            closeDetail(activeDetail, activeDetailCard, true);
        }
        removeDetailOverlays();
        removeReplyUi();
        closingDetail = false;

        float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        Vec2 cardPos = mailCard != null
            ? mailCard.localToStageCoordinates(new Vec2(0f, 0f))
            : new Vec2(sw / 2f, sh / 2f);
        detailStartX = cardPos.x;
        detailStartY = cardPos.y;
        // 缩略图卡片统一 list.add(card(m)).size(CARD_WIDTH, CARD_HEIGHT) 布局
        // 不读 Table.getWidth()/getHeight()：Table 的实测尺寸可能包含布局/内边距差异，
        // 会导 morph Table 初始高度与缩略图对不
        detailStartW = CARD_WIDTH;
        detailStartH = CARD_HEIGHT;

        // 隐藏被点击的缩略卡片（保留占位，关闭动画结束前不恢复
        if(mailCard != null) mailCard.visible = false;

        // content 卡片组加速移出画面，空出收件箱区
        if(lastContent != null){
            lastContent.clearActions();
            lastContent.actions(moveTo(sw + lastContent.getWidth(), contentHomeY, CONTENT_SWEEP_DURATION, Interp.pow2In));
        }

        // 全屏透明点击 + 灰色半透明背景
        Table backdrop = new Table();
        backdrop.touchable = Touchable.enabled;
        backdrop.clicked(() -> {
            if(activeDetail != null && !closingDetail) closeDetail(activeDetail, activeDetailCard);
        });
        Core.scene.root.addChild(backdrop);
        backdrop.setFillParent(true);
        activeBackdrop = backdrop;

        Table detailBg = new Table();
        detailBg.background(((TextureRegionDrawable)Tex.whiteui).tint(Color.valueOf("10101080")));
        detailBg.touchable = Touchable.disabled;
        Core.scene.root.addChild(detailBg);
        detailBg.setFillParent(true);
        activeDetailBg = detailBg;

        // 先测量详情内容的最终尺寸（用临 table，避免隐藏内容参 morph 初始布局
        Table measure = new Table();
        measure.top().left();
        measure.background(TriDraw.whiteOutlined(Styles.grayPanel));
        buildDetailContent(measure, mail, () -> {});
        measure.setSize(DETAIL_WIDTH, 600f);
        measure.validate();
        float dw = measure.getWidth();
        float dh = Math.min(measure.getHeight(), sh - 120f);
        measure.setSize(dw, dh);
        measure.validate();

        // morph table 初始只放缩略图内容，不再包含隐藏的详情内容，避免其行 换行影响初始高度
        Table morph = new Table();
        buildCardContent(morph, mail, wasRead);

        // 详情根节点：morph 只是它的子卡片；回信按钮也挂在根节点上，
        // 这样卡片收回/缩放时按钮会作为子节点一起移动
        Table detailRoot = new Table();
        detailRoot.add(morph).grow();
        detailRoot.setBounds(detailStartX, detailStartY, detailStartW, detailStartH);
        Core.scene.root.addChild(detailRoot);
        detailRoot.toFront();

        activeDetail = detailRoot;
        activeDetailMorph = morph;
        activeDetailCard = mailCard;
        activeDetailMail = mail;

        float targetX = sw / 2f - dw / 2f;
        float targetY = sh / 2f - dh / 2f;

        // 位置/尺寸同步过渡；正好在中点时替换为详情内容
        detailRoot.actions(Actions.parallel(
            moveTo(targetX, targetY, MORPH_DURATION, Interp.pow2Out),
            sizeTo(dw, dh, MORPH_DURATION, Interp.pow2Out)
        ));
        detailRoot.addAction(Actions.sequence(
            delay(MORPH_DURATION / 2f),
            Actions.run(() -> {
                if(activeDetail != detailRoot) return;
                morph.clearChildren();
                morph.background(TriDraw.whiteOutlined(Styles.grayPanel));
                buildDetailContent(morph, mail, () -> {
                    if(activeDetail != null) closeDetail(activeDetail, activeDetailCard);
                });
                morph.validate();
            })
        ));
        detailRoot.addAction(Actions.sequence(
            delay(MORPH_DURATION),
            Actions.run(() -> {
                if(activeDetail == detailRoot){
                    detailRoot.setBounds(targetX, targetY, dw, dh);
                    // 详情卡片到位后，才显示回信按 / 已回信信
                    setupReplyUi(mail);
                }
            })
        ));

        // 有信件详情时，把 TmailLogo 移到屏幕中央（位于详情背后）
        moveLogoToDetail(true);
    }

    /** 关闭详情：默认播放“详

缩略卡片”的返回形变动画
*/
    private static void closeDetail(Table detail, Table mailCard){
        closeDetail(detail, mailCard, false);
    }

    /**
     * 关闭详情
     *
     * @param immediate true 时立即移除（用于打开另一封信前清理旧详情）；
     *                  false 时播放返回动画：morph table 回到原缩略图位置，途中切回卡片外观
     *                  content 卡片组同时回移；二者到位后再恢复缩略图并删
morph table
     */
    private static void closeDetail(Table detail, Table mailCard, boolean immediate){
        if(detail == null || !detail.hasParent()) return;
        if(activeDetail != detail) return;
        if(closingDetail && !immediate) return;

        if(immediate){
            closingDetail = false;
            removeReplyUi();
            detail.clearActions();
            detail.remove();
            removeDetailOverlays();
            activeDetail = null;
            activeDetailMorph = null;
            activeDetailCard = null;
            activeDetailMail = null;
            closingDetail = false;
            if(mailCard != null) mailCard.visible = true;
            moveLogoToDetail(false);
            if(lastContent != null){
                lastContent.clearActions();
                lastContent.actions(moveTo(contentHomeX, contentHomeY, CONTENT_SWEEP_DURATION, Interp.pow2Out));
            }
            refreshReadState();
            return;
        }

        closingDetail = true;
        animateReplyUiOut();
        TriMail mail = activeDetailMail;

        // content 卡片组同步回移， morph table 同时到位
        if(lastContent != null){
            lastContent.clearActions();
            lastContent.actions(moveTo(contentHomeX, contentHomeY, MORPH_DURATION, Interp.pow2Out));
        }
        // Logo 返回左侧
        moveLogoToDetail(false);

        detail.clearActions();
        detail.actions(Actions.parallel(
            moveTo(detailStartX, detailStartY, MORPH_DURATION, Interp.pow2In),
            sizeTo(detailStartW, detailStartH, MORPH_DURATION, Interp.pow2In)
        ));
        // 中途切回缩略图外观
        detail.addAction(Actions.sequence(
            delay(MORPH_DURATION / 2f),
            Actions.run(() -> {
                if(activeDetail != detail || mail == null) return;
                Table morph = activeDetailMorph;
                if(morph != null){
                    morph.clearChildren();
                    morph.background(TriDraw.whiteOutlined(Styles.grayPanel));
                    buildCardContent(morph, mail);
                    morph.validate();
                }
            })
        ));
        // 双方到位：删 morph table，恢复原缩略
        detail.addAction(Actions.sequence(
            delay(MORPH_DURATION),
            Actions.run(() -> {
                if(activeDetail != detail) return;
                closingDetail = false;
                removeReplyUi();
                detail.remove();
                removeDetailOverlays();
                activeDetail = null;
                activeDetailMorph = null;
                activeDetailCard = null;
                activeDetailMail = null;
                if(mailCard != null) mailCard.visible = true;
                refreshReadState();
            })
        ));
    }

    /** 移除详情相关的全屏点击层与灰色背
*/
    private static void removeDetailOverlays(){
        if(activeBackdrop != null){
            activeBackdrop.remove();
            activeBackdrop = null;
        }
        if(activeDetailBg != null){
            activeDetailBg.remove();
            activeDetailBg = null;
        }
    }

    /** 详情卡片到位后，按回信状态显示回信按钮或直接展开已回信信
*/
    private static void setupReplyUi(TriMail mail){
        if(activeDetail == null || activeDetailMail != mail || !mail.story || replyTextOf(mail).isEmpty()) return;

        activeReplyMail = mail;
        if(TriInbox.isReplied(mail)){
            showReplyMiniButton(mail);
        }else{
            showReplyButton(mail);
        }
    }

    /** 已回信邮件首次打开详情时，回信区默认收起为详情卡左上角外侧的小图标按钮
*/
    private static void showReplyMiniButton(TriMail mail){
        if(activeDetail == null || !activeDetail.hasParent()) return;
        removeReplyMiniButton();

        TextButton mini = iconButton();
        // 挂在详情卡上，详情卡移动/缩放时按钮会一起跟随
        mini.setBounds(-REPLY_MINI_SIZE - 10f,
            activeDetail.getHeight() - REPLY_MINI_SIZE,
            REPLY_MINI_SIZE, REPLY_MINI_SIZE);
        mini.clicked(() -> {
            if(replyToggleAnimating) return;
            replyToggleAnimating = true;
            expandReplyPaperFromMini(mail);
        });
        activeDetail.addChild(mini);
        activeReplyMiniButton = mini;
        fadeReplyIconIn(mini);
    }

    /** 无文字图标按钮；当前先用内置 chat 图标占位，之后替换为自建图标
*/
    private static TextButton iconButton(){
        TextButton button = new TextButton("");
        button.clearChildren();
        Image icon = new Image(Icon.chat);
        icon.color.a = 0f; // 默认隐藏，避免按钮刚创建时图标提前出
        button.add(icon).size(REPLY_ICON_SIZE);
        button.setSize(REPLY_MINI_SIZE, REPLY_MINI_SIZE);
        return button;
    }

    private static Image iconOf(TextButton button){
        if(button == null) return null;
        for(Element child : button.getChildren()){
            if(child instanceof Image) return (Image)child;
        }
        return null;
    }

    /** 按钮图标渐隐；与信纸缩放动画同时进行
*/
    private static void fadeReplyIconOut(TextButton button, float duration){
        Image icon = iconOf(button);
        if(icon == null) return;
        icon.clearActions();
        icon.actions(alpha(0f, duration, Interp.pow2In));
    }

    /** 按钮图标渐显；与信纸缩放动画同时进行
*/
    private static void fadeReplyIconIn(TextButton button, float duration){
        Image icon = iconOf(button);
        if(icon == null) return;
        icon.clearActions();
        icon.color.a = 0f;
        icon.actions(alpha(1f, duration, Interp.pow2Out));
    }

    /** 静态出现（无缩放）时使用的短渐显
*/
    private static void fadeReplyIconIn(TextButton button){
        fadeReplyIconIn(button, REPLY_ICON_FADE);
    }

    /** 在详情卡片下方显示“回信”按钮，从卡片底部被覆盖处向下滑
*/
    private static void showReplyButton(TriMail mail){
        if(activeDetail == null || !activeDetail.hasParent()) return;
        removeReplyButton();

        TextButton button = new TextButton(bundle("tri.mail.reply"));
        button.setSize(120f, 44f);
        button.clicked(() -> openReplyPaper(mail, false));

        // 作为详情根节点的子节点放在卡片下面，卡片移动/缩小时按钮会跟随
        float x = activeDetail.getWidth() / 2f - button.getWidth() / 2f;
        float endY = -button.getHeight() - 10f;
        float startY = -button.getHeight() / 2f;

        button.setPosition(x, startY);
        activeDetail.addChildAt(0, button); // 位于内部卡片之下，被卡片遮住上半部分
        activeReplyButton = button;

        button.actions(moveTo(x, endY, REPLY_SLIDE_DURATION, Interp.pow2Out));
    }

    /**
     * 展开回信纸：详情卡片右移，左侧出现与详情卡片同尺寸的信纸
     * 已回信时直接显示完整回信；首次回信时先显示空白信纸，延迟后播放打字机
     */
    private static void openReplyPaper(TriMail mail, boolean alreadyReplied){
        if(activeDetail == null || !activeDetail.hasParent()) return;

        removeReplyButton();
        removeReplyMiniButton();
        removeReplyCollapseButton();
        removeReplyPaper();
        activeReplyMail = mail;

        float w = activeDetail.getWidth();
        float h = activeDetail.getHeight();
        float sw = Core.graphics.getWidth();
        float gap = REPLY_GAP;
        float left = (sw - (w * 2f + gap)) / 2f;
        float detailX = left + w + gap;
        float y = activeDetail.y;

        // 详情卡片右移，给左侧信纸让位
        activeDetail.clearActions();
        activeDetail.actions(moveTo(detailX, y, REPLY_LAYOUT_DURATION, Interp.pow2Out));

        // 信纸根节点：内容卡片与发信按钮都是它的子节点，移 缩小时会一起跟
        Table paperRoot = new Table();
        paperRoot.setBounds(-w - 24f, y, w, h);

        Table paper = new Table();
        paper.top().left();
        paper.background(TriDraw.whiteOutlined(Styles.grayPanel));
        paperRoot.add(paper).grow();

        // 收起按钮挂在详情卡上，而不是会缩放的 paperRoot 上：
        // 坐标固定在详情卡左侧、信纸右上角附近，信纸缩放时图标不会跟着乱跑。
        TextButton collapse = iconButton();
        float collapseX = -REPLY_GAP - REPLY_MINI_SIZE - 8f;
        float collapseY = h - REPLY_MINI_SIZE - 8f;
        collapse.setPosition(collapseX, collapseY);
        collapse.visible = alreadyReplied; // 回信后才显示收起按钮
        collapse.clicked(() -> {
            if(replyToggleAnimating || !TriInbox.isReplied(mail)) return;
            replyToggleAnimating = true;
            collapseReplyPaper(mail);
        });
        activeDetail.addChild(collapse);

        Core.scene.root.addChild(paperRoot);
        activeDetail.toFront(); // 让详情卡上的收起按钮盖在信纸边缘之上
        activeReplyPaper = paperRoot;
        activeReplyPaperContent = paper;
        activeReplyCollapseButton = collapse;
        if(alreadyReplied) fadeReplyIconIn(collapse);

        // 从屏幕左侧外滑入，先快后慢，落到详情卡左
        paperRoot.actions(moveTo(left, y, REPLY_LAYOUT_DURATION, Interp.pow2Out));

        if(alreadyReplied){
            buildReplyPaperFull(paper, mail);
        }else{
            // 空白信纸停留一小段时间后，再播放回信打字机
            Time.runTask(anim(REPLY_PAPER_DELAY) * 60f, () -> {
                if(activeReplyPaperContent == paper){
                    buildReplyPaperTyping(paper, mail);
                }
            });
        }
    }

    /** 信纸抬头：发件人 / 收件
/ 分割
*/
    private static void buildReplyPaperHeader(Table paper, TriMail mail){
        paper.clearListeners();
        paper.top().left();

        Label from = new Label(bundle("tri.mail.reply.from") + bundle("tri.mail.reply.sender"));
        from.touchable = Touchable.disabled;
        paper.add(from).left().pad(12).row();

        Label to = new Label(bundle("tri.mail.reply.to") + mail.sender);
        to.touchable = Touchable.disabled;
        paper.add(to).left().pad(12).row();

        Image divider = new Image(Tex.whiteui, Color.gray);
        divider.touchable = Touchable.disabled;
        paper.add(divider).growX().height(2).padLeft(12).padRight(12).row();
    }

    /** 已回
发送完成后的完整信纸内
*/
    private static void buildReplyPaperFull(Table paper, TriMail mail){
        paper.clearChildren();
        buildReplyPaperHeader(paper, mail);

        Label full = new Label(replyTextOf(mail));
        full.touchable = Touchable.disabled;
        full.setWrap(true);
        full.setAlignment(Align.topLeft);
        paper.add(full).left().top().growX().pad(12).row();
    }

    /** 首次回信：空白信纸展开后播放打字机，播放完成时显示下方发信按钮
*/
    private static void buildReplyPaperTyping(Table paper, TriMail mail){
        paper.clearChildren();
        buildReplyPaperHeader(paper, mail);

        String text = replyTextOf(mail);
        if(text.isEmpty()){
            showReplySendButton(paper, mail);
            return;
        }

        Label[] typedRef = {null};
        Label typed = TriDraw.typewriter(paper, text,
            mail.replySpeed > 0 ? mail.replySpeed : 24f, Align.topLeft,
            () -> {
                paper.row();
                paper.add(typedRef[0]).left().top().growX().pad(12).row();
            },
            () -> showReplySendButton(paper, mail));
        typedRef[0] = typed;
    }

    /** 在信纸下方显示“发信”按钮，从信纸底部被覆盖处向下滑
*/
    private static void showReplySendButton(Table paper, TriMail mail){
        if(activeReplyPaperContent != paper || activeReplyPaper == null) return;
        removeReplySend();

        TextButton send = new TextButton(bundle("tri.mail.send"));
        send.setSize(110f, 44f);
        send.clicked(() -> sendReply(paper, mail));

        Table root = activeReplyPaper;
        float x = root.getWidth() / 2f - send.getWidth() / 2f;
        float endY = -send.getHeight() - 10f;
        float startY = -send.getHeight() / 2f;

        send.setPosition(x, startY);
        root.addChildAt(0, send); // 位于信纸内容之下，被信纸遮住上半部分
        activeReplySend = send;

        send.actions(moveTo(x, endY, REPLY_SLIDE_DURATION, Interp.pow2Out));
    }

    /** 发信：执行原回调、记录已回信、显示“已发信”，随后恢复完整回信正文
*/
    private static void sendReply(Table paper, TriMail mail){
        if(activeReplyPaperContent != paper || TriInbox.isReplied(mail)) return;

        if(mail.onReply != null) mail.onReply.run();
        TriInbox.markReplied(mail);
        removeReplySend();

        // 回信完成后才显示右上角收起按
        if(activeReplyCollapseButton != null){
            activeReplyCollapseButton.visible = true;
            fadeReplyIconIn(activeReplyCollapseButton);
        }

        paper.clearChildren();
        paper.center();
        paper.add("[accent]" + bundle("tri.mail.sent")).expand().center();

        refreshReadState();

        Time.runTask(REPLY_SENT_DURATION * 60f, () -> {
            if(activeReplyPaperContent == paper){
                buildReplyPaperFull(paper, mail);
            }
        });
    }

    /**
     * 关闭详情时让回信相关元素先回收：
     * - 回信按钮滑回详情卡片底部被覆盖的位置
     * - 发信按钮滑回信纸底部被覆盖的位置
     * - 已展开的信纸加速向左滑出屏幕
     */
    private static void animateReplyUiOut(){
        // 关闭触发瞬间立即移除回信 icon；信纸/发信按钮仍按各自动画回收
        removeReplyMiniButton();
        removeReplyCollapseButton();
        if(activeReplyPaper != null){
            Table paper = activeReplyPaper;
            paper.clearActions();

            if(activeReplySend != null){
                TextButton send = activeReplySend;
                send.clearActions();
                float targetY = -send.getHeight() / 2f;
                send.actions(
                    moveTo(send.x, targetY, REPLY_PAPER_EXIT_DURATION, Interp.pow2Out),
                    Actions.run(() -> {
                        if(activeReplySend == send){
                            send.remove();
                            activeReplySend = null;
                        }
                    })
                );
            }

            // 信纸向左侧屏幕外加速移出（先慢后快 pow2In
            paper.actions(
                moveTo(-paper.getWidth() - 24f, paper.y, REPLY_PAPER_EXIT_DURATION, Interp.pow2In),
                Actions.run(() -> {
                    if(activeReplyPaper == paper){
                        removeReplyPaper();
                    }
                })
            );
        }else if(activeReplyButton != null){
            TextButton button = activeReplyButton;
            button.clearActions();
            float targetY = -button.getHeight() / 2f;
            button.actions(
                moveTo(button.x, targetY, REPLY_SLIDE_DURATION, Interp.pow2Out),
                Actions.run(() -> {
                    if(activeReplyButton == button){
                        button.remove();
                        activeReplyButton = null;
                    }
                })
            );
        }
    }

    /**
     * 回信纸收起：信纸缩小到左上角小按钮，详情卡同时回到屏幕中央
     * 中点隐藏信纸文字并出现图标，结尾替换为真正的小按钮
     */
    private static void collapseReplyPaper(TriMail mail){
        Table root = activeReplyPaper;
        Table content = activeReplyPaperContent;
        if(root == null || content == null || activeDetail == null) return;

        removeReplySend();
        TextButton collapse = activeReplyCollapseButton;
        if(collapse != null){
            // 按钮边框立即消失，缩放中的边框由信纸 table 自己承担；仅图标继续渐隐
            collapse.setStyle(Styles.nonet);
            fadeReplyIconOut(collapse, REPLY_COLLAPSE_DURATION / 2f);
        }

        float sw = Core.graphics.getWidth();
        float w = activeDetail.getWidth(), h = activeDetail.getHeight();
        float detailX = (sw - w) / 2f;
        float y = activeDetail.y;
        float miniX = detailX - REPLY_MINI_SIZE - 10f;
        float miniY = y + h - REPLY_MINI_SIZE;

        activeDetail.clearActions();
        activeDetail.actions(moveTo(detailX, y, REPLY_COLLAPSE_DURATION, Interp.pow2Out));

        root.clearActions();
        root.actions(Actions.parallel(
            moveTo(miniX, miniY, REPLY_COLLAPSE_DURATION, Interp.pow2Out),
            sizeTo(REPLY_MINI_SIZE, REPLY_MINI_SIZE, REPLY_COLLAPSE_DURATION, Interp.pow2Out)
        ));
        root.addAction(Actions.sequence(
            delay(REPLY_COLLAPSE_DURATION / 2f),
            Actions.run(() -> {
                if(activeReplyPaper != root) return;
                content.visible = false;
                root.background(TriDraw.whiteOutlined(Styles.grayPanel));
                if(collapse != null){
                    collapse.remove();
                    activeReplyCollapseButton = null;
                }

                // 收缩过半时小按钮出现，图标在后半段随信纸缩小同步渐显
                TextButton mini = iconButton();
                mini.setBounds(-REPLY_MINI_SIZE - 10f,
                    activeDetail.getHeight() - REPLY_MINI_SIZE,
                    REPLY_MINI_SIZE, REPLY_MINI_SIZE);
                mini.clicked(() -> {
                    if(replyToggleAnimating) return;
                    replyToggleAnimating = true;
                    expandReplyPaperFromMini(mail);
                });
                activeDetail.addChild(mini);
                activeReplyMiniButton = mini;
                fadeReplyIconIn(mini, REPLY_COLLAPSE_DURATION / 2f);
            })
        ));
        root.addAction(Actions.sequence(
            delay(REPLY_COLLAPSE_DURATION),
            Actions.run(() -> {
                if(activeReplyPaper != root) return;
                root.remove();
                activeReplyPaper = null;
                activeReplyPaperContent = null;
                activeReplyMail = mail;
                replyToggleAnimating = false;
            })
        ));
    }

    /** 从小按钮展开回信纸：信纸从按钮尺寸放大到完整大小，详情卡右移
*/
    private static void expandReplyPaperFromMini(TriMail mail){
        if(activeDetail == null) return;
        TextButton mini = activeReplyMiniButton;
        removeReplyCollapseButton();
        removeReplyPaper();
        activeReplyMail = mail;

        float w = activeDetail.getWidth(), h = activeDetail.getHeight();
        float sw = Core.graphics.getWidth();
        float gap = REPLY_GAP;
        float left = (sw - (w * 2f + gap)) / 2f;
        float detailX = left + w + gap;
        float y = activeDetail.y;
        float startX = (sw - w) / 2f - REPLY_MINI_SIZE - 10f;
        float startY = y + h - REPLY_MINI_SIZE;

        activeDetail.clearActions();
        activeDetail.actions(moveTo(detailX, y, REPLY_COLLAPSE_DURATION, Interp.pow2Out));

        Table root = new Table();
        root.setBounds(startX, startY, REPLY_MINI_SIZE, REPLY_MINI_SIZE);
        root.background(TriDraw.whiteOutlined(Styles.grayPanel));

        Table paper = new Table();
        paper.top().left();
        paper.background(TriDraw.whiteOutlined(Styles.grayPanel));
        paper.visible = false;
        root.add(paper).grow();

        TextButton collapse = iconButton();
        collapse.setPosition(-REPLY_GAP - REPLY_MINI_SIZE - 8f,
            h - REPLY_MINI_SIZE - 8f);
        collapse.visible = false;
        collapse.clicked(() -> {
            if(replyToggleAnimating || !TriInbox.isReplied(mail)) return;
            replyToggleAnimating = true;
            collapseReplyPaper(mail);
        });
        activeDetail.addChild(collapse);

        Core.scene.root.addChild(root);
        activeDetail.toFront(); // 小按钮/收起按钮属于详情卡，保持在信纸上方
        if(mini != null){
            mini.toFront();
            // 边框立即消失，展开中的边框由信纸 table 负责
            mini.setStyle(Styles.nonet);
            fadeReplyIconOut(mini, REPLY_COLLAPSE_DURATION / 2f);
        }
        activeReplyPaper = root;
        activeReplyPaperContent = paper;
        activeReplyCollapseButton = collapse;

        root.actions(Actions.parallel(
            moveTo(left, y, REPLY_COLLAPSE_DURATION, Interp.pow2Out),
            sizeTo(w, h, REPLY_COLLAPSE_DURATION, Interp.pow2Out)
        ));
        root.addAction(Actions.sequence(
            delay(REPLY_COLLAPSE_DURATION / 2f),
            Actions.run(() -> {
                if(activeReplyPaper != root) return;
                if(activeReplyMiniButton == mini){
                    mini.remove();
                    activeReplyMiniButton = null;
                }
                paper.visible = true;
                root.background(null);
                collapse.visible = TriInbox.isReplied(mail);
                if(collapse.visible) fadeReplyIconIn(collapse, REPLY_COLLAPSE_DURATION / 2f);
            })
        ));
        root.addAction(Actions.sequence(
            delay(REPLY_COLLAPSE_DURATION),
            Actions.run(() -> {
                if(activeReplyPaper != root) return;
                root.setBounds(left, y, w, h);
                if(TriInbox.isReplied(mail)){
                    buildReplyPaperFull(paper, mail);
                }else{
                    buildReplyPaperTyping(paper, mail);
                }
                replyToggleAnimating = false;
            })
        ));
    }

    /** 移除详情下方回信按钮、左侧信纸与发信按钮
*/
    private static void removeReplyUi(){
        replyToggleAnimating = false;
        removeReplyButton();
        removeReplyMiniButton();
        removeReplyCollapseButton();
        removeReplyPaper();
    }

    private static void removeReplyButton(){
        if(activeReplyButton != null){
            activeReplyButton.remove();
            activeReplyButton = null;
        }
    }

    private static void removeReplySend(){
        if(activeReplySend != null){
            activeReplySend.remove();
            activeReplySend = null;
        }
    }

    private static void removeReplyMiniButton(){
        if(activeReplyMiniButton != null){
            activeReplyMiniButton.remove();
            activeReplyMiniButton = null;
        }
    }

    private static void removeReplyCollapseButton(){
        if(activeReplyCollapseButton != null){
            activeReplyCollapseButton.remove();
            activeReplyCollapseButton = null;
        }
    }

    private static void removeReplyPaper(){
        removeReplySend();
        if(activeReplyPaper != null){
            activeReplyPaper.remove();
            activeReplyPaper = null;
        }
        activeReplyPaperContent = null;
        activeReplyMail = null;
    }

    /** 构建信件详情内容：标题（右带关闭按钮
发件
分割
正文/附件/回信
已读按钮
     *  onExit 供关闭按钮与已读按钮共用（关闭详情：恢复卡片 + content 滑回
*/
    private static void buildDetailContent(Table content, TriMail mail, Runnable onExit){
        content.top().left();

        // 顶部：标题（左）+ 关闭按钮 ×（右）——按钮放大便于点
        Table head = new Table();
        head.left();
        head.add("[accent]" + mail.subject).left().growX();
        TextButton close = new TextButton("×");
        close.clicked(() -> {
            if(onExit != null) onExit.run();
        });
        head.add(close).size(56f, 48f).pad(8);
        content.add(head).growX().pad(12).row();

        content.add("[accent]" + mail.sender).left().growX().pad(12).row();

        // 分割线：发件人与正文之间
        Image divider = new Image(Tex.whiteui, Color.gray);
        content.add(divider).growX().height(2).padLeft(12).padRight(12).row();

        Label body = new Label(mail.body);
        body.setWrap(true);
        body.setAlignment(Align.topLeft);
        // 底部 2px：使下方回信区紧贴正
        content.add(body).left().top().growX().pad(12, 12, 2, 12).row();

        // 附件：正文下方，用与发件 正文同款的分割线分隔；可点“收取附件”执行动
        if(TriInbox.hasAttachment(mail)){
            Image div2 = new Image(Tex.whiteui, Color.gray);
            content.add(div2).growX().height(2).padLeft(12).padRight(12).row();
            content.add("[accent]附件：" + mail.attachment).left().growX().pad(12).row();

            TextButton collect = new TextButton(bundle("tri.mail.collect"));
            boolean done = TriInbox.attachmentCollected(mail);
            if(done){
                collect.setText(bundle("tri.mail.collected"));
                collect.setDisabled(true);
            }
            collect.clicked(() -> {
                TriInbox.collectAttachment(mail);
                collect.setText(bundle("tri.mail.collected"));
                collect.setDisabled(true);
                refreshReadState(); // 附件收取后刷新已读/未读状态（角标 + 列表）
            });
            content.add(collect).size(130f, 44f).pad(8).row();
        }

        // 回信按钮/回信纸改为详情卡片外部元素，不再放进正文 Table ?
        // “已读”按钮：被按下时与退出按?透明按钮一样触发退出（关闭详情：恢复卡?+ content 滑回）；
        // 已读状态时按钮 disabled（不可按下），文字置灰（回归改动前：仅文字置灰，不整体着色）
        TextButton readBtn = new TextButton(bundle("tri.mail.back"));
        if(TriInbox.isReadBtnClicked(mail)){
            readBtn.setDisabled(true); // 已读：disabled 不可按下
            readBtn.getLabel().setColor(Color.gray); // 文字置灰（回归改动前
        }
        readBtn.clicked(() -> {
            TriInbox.markReadBtnClicked(mail); // 记忆置灰状
            readBtn.setDisabled(true); // 置灰 + 不可按下
            readBtn.getLabel().setColor(Color.gray); // 文字置灰（回归改动前
            if(onExit != null) onExit.run(); // 触发同退出的行为：关闭详情（closeDetail 内已统一调用 refreshReadState
        });
        content.add(readBtn).size(100f, 50f).pad(10);
    }

    /** 回信正文：优先硬编码 replyText（中文示例信，不本地化）；否?bundle（tri.mail.reply.<key>
*/
    private static String replyTextOf(TriMail mail){
        String text = mail.replyText != null ? mail.replyText : bundle("tri.mail.reply." + mail.key);
        if(mail.replyText == null && text.equals("tri.mail.reply." + mail.key)) return ""; // 未配置保持空
        return text;
    }

    /** 按当前分类过滤（历史按最后阅读时间倒序）。传入已解锁邮件列表，避免重复遍
*/
    private static Seq<TriMail> mailsFor(Filter f, Seq<TriMail> all){
        switch(f){
            case READ: return filter(all, true);
            case UNREAD: return filter(all, false);
            case HISTORY:{
                Seq<TriMail> r = filter(all, true);
                r.sort((a, b) -> Long.compare(TriInbox.readTime(b), TriInbox.readTime(a))); // 最后阅读的在前
                return r;
            }
            default: return all; // ALL，且限定当前作用
        }
    }

    /** 按已读状态过滤出新的列表（复用传入的解锁列表，不重复遍历 TriInbox
*/
    private static Seq<TriMail> filter(Seq<TriMail> all, boolean read){
        Seq<TriMail> r = new Seq<>();
        for(TriMail m : all) if(TriInbox.isRead(m) == read) r.add(m);
        return r;
    }

    /** 统计某分类中的未读数量（已读/历史类恒?0；全?未读类返回未读总数，一次遍历计数）?*/
    private static int unreadIn(Seq<TriMail> all, Filter f){
        if(f == Filter.READ || f == Filter.HISTORY) return 0;
        int n = 0;
        for(TriMail m : all) if(!TriInbox.isRead(m)) n++;
        return n;
    }

    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
