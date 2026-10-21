package triangle.black.TMail;

import arc.Core;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.scene.Element;
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
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import triangle.black.MRFactory.MultiRecipeFactory;

/**
 * 三角通讯收件箱界面。
 * 每封邮件固定大小卡片（圆角矩形包裹：上发件人、中分割线、下内容，过长省略）；
 * 卡片竖直向下居中排列、可滚动；顶部居中分类栏（带未读角标）：全部 / 已读 / 未读 / 历史；
 * 列表为空时显示灰色提示；详情页限宽。
 */
public class TriMailDialog{

    private enum Filter{ ALL, READ, UNREAD, HISTORY }

    private static final float CARD_WIDTH = 480f;
    private static final float CARD_HEIGHT = 76f;
    private static final float DETAIL_WIDTH = 560f;

    private static BaseDialog inbox;
    private static Filter filter = Filter.ALL;
    /** 当前收件箱作用域：true=战役剧情邮件（战役星球 TMail）；false=模组资讯邮件（主菜单 TMail）。 */
    private static boolean storyScope = true;
    /** 收件箱打开时的全屏毛玻璃背景。 */
    private static TriBlurBackground blur;
    /** 收件箱内容整体 Table（供整体滑入动画）。 */
    private static Table lastContent;
    /** 当前打开的信件详情 table（画面中心），以及其对应的缩略卡片（用于关闭时恢复显示）。 */
    private static Table activeDetail, activeDetailCard;
    /** 详情背后的全屏透明按钮：点击详情卡片外的部分 → 触发关闭详情（置于收件箱之上、详情之下）。 */
    private static Table activeBackdrop;
    /** 收件箱内容的“家”位置（滑入目标 = 屏幕右 3/4 处）：打开详情时向右滑出、关闭详情后滑回此处。 */
    private static float contentHomeX, contentHomeY;
    /** 收件箱内容滑入时长（先快后慢）。 */
    private static final float SLIDE_IN_DURATION = 0.4f;
    /** 打开/关闭详情时，背景 content 滑出（加速）/滑回（减速）时长（≤0.2s）。 */
    private static final float CONTENT_SWEEP_DURATION = 0.2f;

    private TriMailDialog(){}

    /** 打开战役剧情收件箱（战役星球 TMail）。 */
    public static void show(){
        show(true);
    }

    /** 打开收件箱（持久实例，可原地重建避免弹窗堆叠）。story=true 剧情 / story=false 资讯。 */
    public static void show(boolean story){
        storyScope = story;
        if(inbox == null){
            inbox = new BaseDialog("");
            inbox.addCloseButton();
            // 收件箱关闭时移除毛玻璃背景，并同步关闭可能仍打开的信件详情与透明按钮（避免悬浮残留）
            inbox.hidden(() -> {
                if(activeDetail != null){
                    activeDetail.remove();
                    activeDetail = null;
                    activeDetailCard = null;
                }
                if(activeBackdrop != null){
                    activeBackdrop.remove();
                    activeBackdrop = null;
                }
                if(blur != null){
                    blur.remove();
                    blur = null;
                }
            });
        }
        inbox.title.setText(story ? bundle("tri.mail.title") : bundle("tri.modinfo.title"));

        // 全屏毛玻璃背景（截屏 + 模糊 + 透明度渐深），置于收件箱窗口之下
        if(blur == null || !blur.hasParent()){
            blur = new TriBlurBackground();
            Core.scene.add(blur);
        }

        inbox.show();
        build(inbox);

        // 内容整体滑入：收件箱内容 Table 从右侧滑入到屏幕右半屏中心（右 3/4 处），空出左半屏（用于 logo）。
        // 延迟到下一帧触发，避免与 Dialog.show() 内部流程同帧竞争；
        // content 布局已冻结（setLayoutEnabled(false)），位置手动控制，不会被 Table 布局覆盖。
        Time.runTask(1f, () -> {
            if(!inbox.isShown() || lastContent == null) return;
            float cw = lastContent.getWidth(), ch = lastContent.getHeight();
            if(cw <= 0f || ch <= 0f) return;
            float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
            // 目标：content 中心位于屏幕右半屏中心（右 3/4 处）
            float targetScreenX = sw * 0.75f, targetScreenY = sh / 2f;
            // content 坐标是相对 cont 的局部坐标：
            // 局部目标 = 屏幕目标 - dialog 左下角(inbox.x/y) - cont 相对 dialog 偏移(inbox.cont.x/y) - content 半宽/半高
            float targetLocalX = targetScreenX - inbox.x - inbox.cont.x - cw / 2f;
            float targetLocalY = targetScreenY - inbox.y - inbox.cont.y - ch / 2f;
            // 记录“家”位置：打开详情时 content 向右滑出、关闭详情后滑回此处
            contentHomeX = targetLocalX;
            contentHomeY = targetLocalY;
            // 起点：content 完全移出 cont 右侧（y 保持目标 y → 水平平行滑入）
            lastContent.setPosition(cw, targetLocalY);
            lastContent.actions(Actions.moveTo(targetLocalX, targetLocalY, SLIDE_IN_DURATION, Interp.pow2Out));
        });
    }

    /** 重建收件箱内容（清空后重绘分类栏 + 邮件列表）。整个内容包成一个 Table 供整体滑入动画。 */
    private static void build(BaseDialog dlg){
        dlg.cont.clearChildren();

        // 一次解锁遍历，供分类角标与列表共用（避免每个分类各自 new Seq + 重复遍历全部邮件）
        Seq<TriMail> all = TriInbox.unlocked(storyScope);

        // 整个收件箱内容包成一个 Table（便于整体滑入动画与后续改进）
        Table content = new Table();
        content.top();

        // 顶部居中的分类栏（放大按钮 + 未读角标）
        Table bar = new Table();
        bar.center();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        addCategory(dlg, bar, group, Filter.ALL, "tri.mail.cat.all", all);
        addCategory(dlg, bar, group, Filter.READ, "tri.mail.cat.read", all);
        addCategory(dlg, bar, group, Filter.UNREAD, "tri.mail.cat.unread", all);
        addCategory(dlg, bar, group, Filter.HISTORY, "tri.mail.cat.history", all);
        content.add(bar).padTop(8).padBottom(4).row();

        // 竖直向下的邮件列表（居中），可滚动
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

        dlg.cont.add(content).grow();
        // 先让父布局（cont）把 content 排到正确尺寸/位置（grow 填满），再让 content 子元素布局，
        // 随后冻结布局：位置改由手动控制（供整体滑入动画，不被 Table 布局覆盖）。
        dlg.cont.validate();
        content.validate();
        content.setLayoutEnabled(false);
        lastContent = content;

        // 详情页存在时，点击 Tmail 主界面（收件箱区域）→ 触发详情页关闭（避免叠加次序问题）。
        // 用 touchDown（按下瞬间）：先于子元素（卡片）的 touchUp/clicked 触发，
        // 因此点卡片时先关闭旧详情、随后卡片再打开新详情，不会误关新开的详情。
        content.addListener(new ClickListener(){
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(activeDetail != null){
                    closeDetail(activeDetail, activeDetailCard);
                }
                return false; // 不拦截事件，继续向子元素传播
            }
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
    }

    /** 单封邮件的固定大小卡片：圆角矩形包裹，上发件人、中分割线、下内容（省略）。整卡可点击阅读。 */
    private static Table card(TriMail mail){
        Table card = new Table();
        card.top().left();
        // 圆角矩形包裹 + 白色描边（复用 MultiRecipeFactory.whiteOutlined 的圆角描边绘制）
        card.background(MultiRecipeFactory.whiteOutlined(Styles.grayPanel));

        // 文本单元格必须显式定宽（不能 growX），否则列宽被最长文本撑大、ellipsis 无法触发
        float innerW = CARD_WIDTH - 28;
        boolean read = TriInbox.isRead(mail);

        // 上一行：发件人（未读时高亮），顶部左对齐
        String senderText = read ? mail.sender : "[accent]" + mail.sender;
        Label sender = new Label(senderText);
        sender.setEllipsis(true);
        sender.setAlignment(Align.topLeft);
        card.add(sender).top().left().width(innerW).height(32).padLeft(14).padRight(14).padTop(7).row();

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

    /** 打开一封邮件的正文（自动标记已读并记录阅读时间）。
     *  被点击的信件缩略卡片在列表中隐藏（保留占位），关闭详情后恢复显示。
     *  详情以画面中心新建的 table 直接显示（限宽、按内容实际高度真正居中）。 */
    private static void openMail(TriMail mail, Table mailCard){
        TriInbox.markRead(mail);

        // 打开新详情前关闭已有详情（避免多个详情叠加；同时移除旧的全屏透明按钮）
        if(activeDetail != null) closeDetail(activeDetail, activeDetailCard);
        if(activeBackdrop != null){
            activeBackdrop.remove();
            activeBackdrop = null;
        }

        // 隐藏被点击的信件缩略图卡片（保留占位，列表布局不移动）
        if(mailCard != null) mailCard.visible = false;

        // 打开详情：背景 content 卡片组加速移出画面（先慢再快 pow2In，≤0.2s），空出收件箱区域
        if(lastContent != null){
            lastContent.clearActions();
            float sw = Core.graphics.getWidth();
            lastContent.actions(Actions.moveTo(sw + lastContent.getWidth(), contentHomeY, CONTENT_SWEEP_DURATION, Interp.pow2In));
        }

        // 画面中心 table：直接显示信件详情
        float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        Table detail = new Table();
        detail.top().left();
        detail.background(MultiRecipeFactory.whiteOutlined(Styles.grayPanel)); // 白色描边
        buildDetailContent(detail, mail, () -> closeDetail(detail, mailCard));
        // 限定宽度后布局，取内容实际高度（而非期望高度）用于真正居中
        detail.setSize(DETAIL_WIDTH, 600f);
        detail.validate();
        float w = detail.getWidth(), h = Math.min(detail.getHeight(), sh - 120f); // 限高防超屏
        detail.setSize(w, h);
        detail.validate();
        // 详情背后的全屏透明按钮（先于详情加入场景 → 在详情之下、收件箱之上）：
        // 点击详情卡片外的部分 → 统一触发关闭详情；按钮本体不可见，随详情关闭一并移除
        Table backdrop = new Table();
        backdrop.touchable = Touchable.enabled; // 全屏可触摸（拦截详情外点击）
        backdrop.clicked(() -> {
            if(activeDetail != null) closeDetail(activeDetail, activeDetailCard);
        });
        Core.scene.root.addChild(backdrop);
        backdrop.setFillParent(true); // 填满整个场景
        activeBackdrop = backdrop;

        // 再挂详情（在透明按钮之上）
        Core.scene.root.addChild(detail);
        detail.toFront();
        detail.setPosition(sw / 2f - w / 2f, sh / 2f - h / 2f); // 画面正中心

        // 记录当前详情（供透明按钮/主界面点击时关闭；并关联缩略卡片用于恢复）
        activeDetail = detail;
        activeDetailCard = mailCard;
    }

    /** 关闭画面中心详情 table：移除 table、移除背后的全屏透明按钮、恢复被隐藏的信件缩略卡片、content 减速滑回右 3/4（先快后慢 pow2Out，≤0.2s）。
     *  可重复调用（幂等）：详情已不在场景时直接返回。 */
    private static void closeDetail(Table detail, Table mailCard){
        if(detail == null || !detail.hasParent()) return; // 已关闭
        if(activeDetail == detail){
            activeDetail = null;
            activeDetailCard = null;
        }
        if(activeBackdrop != null){ // 移除全屏透明按钮
            activeBackdrop.remove();
            activeBackdrop = null;
        }
        detail.remove(); // 移除画面中心详情 table
        if(mailCard != null) mailCard.visible = true; // 恢复信件缩略卡片
        if(lastContent != null){
            lastContent.clearActions();
            lastContent.actions(Actions.moveTo(contentHomeX, contentHomeY, CONTENT_SWEEP_DURATION, Interp.pow2Out));
        }
    }

    /** 构建信件详情内容：标题（右带关闭按钮）/发件人/分割线/正文/附件/回信区/已读按钮。
     *  onExit 供关闭按钮与已读按钮共用（关闭详情：恢复卡片 + content 滑回）。 */
    private static void buildDetailContent(Table content, TriMail mail, Runnable onExit){
        content.top().left();

        // 顶部：标题（左）+ 关闭按钮 ×（右）——按钮放大便于点按
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
        // 底部留 2px：使下方回信区紧贴正文
        content.add(body).left().top().growX().pad(12, 12, 2, 12).row();

        // 附件：正文下方，用与发件人/正文同款的分割线分隔；可点“收取附件”执行动作
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
            });
            content.add(collect).size(130f, 44f).pad(8).row();
        }

        // 回信区（仅战役剧情信件显示）：按钮控制；按下后在邮件下方展开带描边的回信框
        if(mail.story){
            content.add(replySection(mail)).left().row();
        }

        // “已读”按钮：点击后置灰并禁用（详情页已有右上角返回键，无需再次返回操作）；
        // 置灰状态持久化记忆，重新打开详情页时保持置灰
        TextButton readBtn = new TextButton(bundle("tri.mail.back"));
        if(TriInbox.isReadBtnClicked(mail)){
            readBtn.setDisabled(true);
        }else{
            readBtn.clicked(() -> {
                TriInbox.markReadBtnClicked(mail); // 记忆置灰状态
                readBtn.setDisabled(true);
                if(onExit != null) onExit.run(); // 关闭详情（恢复卡片 + content 滑回）
                // 等滑回动画播完（约 0.2s）后再刷新收件箱，避免 build 立即清空重建打断滑回动画；
                // 重建后把新 content 放回右 3/4（按当前尺寸重算），不会落到屏幕中心
                Time.runTask(CONTENT_SWEEP_DURATION + 0.05f, () -> {
                    if(!inbox.isShown()) return;
                    build(inbox);
                    if(lastContent != null){
                        float cw = lastContent.getWidth(), ch = lastContent.getHeight();
                        float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
                        lastContent.setPosition(sw * 0.75f - inbox.x - inbox.cont.x - cw / 2f,
                                                sh / 2f - inbox.y - inbox.cont.y - ch / 2f);
                    }
                });
            });
        }
        content.add(readBtn).size(100f, 50f).pad(10);
    }

    /**
     * 回信区：检测回信状态后决定显示形态——
     * 未回信（isReplied=false）：显示原来的“回信”按钮，点击进入打字机 + 发送流程；
     * 已回信（检测到回信完成）：显示“回信内容”面板（点击查看完整回信，展开时点击收回）。
     */
    private static Table replySection(TriMail mail){
        Table section = new Table();
        section.top().left();
        // 未设置回信内容的信件（无 replyText 且 bundle 无 tri.mail.reply.<key>）：整个回信区不显示
        if(replyTextOf(mail).isEmpty()){
            return section;
        }
        if(TriInbox.isReplied(mail)){
            // 检测到回信已完成：显示“回信内容”面板（默认收起），紧贴正文
            section.add(replyBox(mail)).width(DETAIL_WIDTH).pad(2, 0, 2, 0).row();
        }else{
            // 未回信：显示“回信”按钮（回信功能入口）
            addReplyButton(section, mail);
        }
        return section;
    }

    /** 回信按钮（未回信入口）：点击后进入打字机回信框；打字机未发送时点击框收回会恢复本按钮。 */
    private static void addReplyButton(Table section, TriMail mail){
        TextButton replyBtn = new TextButton(bundle("tri.mail.reply"));
        replyBtn.clicked(() -> {
            // 整表重建：按钮的 Table cell 连同布局一并清除。
            // 注意：Element.remove() 只移除组件、其 cell 仍会占位，导致回信框上方残留空行；
            // arc Table.clearChildren() 会连 cells 一并清除，可彻底消除占位。
            section.clearChildren();
            section.top().left();
            Table box = new Table();
            box.top().left();
            box.background(MultiRecipeFactory.whiteOutlined(Styles.grayPanel));
            buildReplyFull(box, mail, replyTextOf(mail), false, () -> {
                if(TriInbox.isReplied(mail)){
                    // 已发送：点击框 → 收起为“回信内容”面板
                    buildReplyMini(box, mail, replyTextOf(mail));
                }else{
                    // 未发送：点击框 → 恢复“回信”按钮
                    section.clearChildren();
                    section.top().left();
                    addReplyButton(section, mail);
                }
            });
            section.add(box).width(DETAIL_WIDTH).pad(2, 0, 2, 0).row();
        });
        // 回信按钮紧贴正文（顶部仅 2px）
        section.add(replyBtn).size(110f, 44f).pad(2, 8, 8, 8).row();
    }

    /** 回信框（已回信查看入口）：白描边圆角（whiteOutlined），宽度与详情页一致；初始为收起的“回信内容”面板。 */
    private static Table replyBox(TriMail mail){
        Table box = new Table();
        box.top().left();
        box.background(MultiRecipeFactory.whiteOutlined(Styles.grayPanel));
        buildReplyMini(box, mail, replyTextOf(mail));
        return box;
    }

    /** 回信正文：优先硬编码 replyText（中文示例信，不本地化）；否则 bundle（tri.mail.reply.<key>）。 */
    private static String replyTextOf(TriMail mail){
        String text = mail.replyText != null ? mail.replyText : bundle("tri.mail.reply." + mail.key);
        if(mail.replyText == null && text.equals("tri.mail.reply." + mail.key)) return ""; // 未配置保持空白
        return text;
    }

    /** 回信面板收起态（仅已回信显示）：白色描边框内显示“回信内容”（一行字体的高度），
     *  点击面板任意处展开完整回信；展开时点击则收回。
     *  面板用透明样式 TextButton 承载（天然可点击组件，不依赖表格命中判定）。 */
    private static void buildReplyMini(Table box, TriMail mail, String text){
        box.clearListeners();
        box.clearChildren();
        box.top().left();
        box.touchable = Touchable.enabled; // 防御：确保面板本身可点击

        // 一行字高的透明按钮行：点击展开完整回信（已回信查看）
        TextButton open = new TextButton(bundle("tri.mail.reply.panel"), Styles.cleart);
        open.clicked(() -> buildReplyFull(box, mail, text, true, null));
        box.add(open).growX().height(26).pad(2);
    }

    /** 回信框展开态。done=false：打字机播放（每封信可单独设置速度，未设置默认 24 字/秒），
     *  播完动态出现“发送”按钮；done=true：直接完整显示回信（无发送按钮、无空行）。
     *  展开时点击整个表格任意处则收回：onCollapse 非空（未回信打字机）→ 执行该回调（恢复“回信”按钮）；
     *  否则（已回信查看）→ 收起为“回信内容”面板。
     *  注意：arc 事件冒泡（子→父），点击框内任意子元素都会冒泡到本表触发 clicked；
     *  发送按钮的点击由按钮自身处理，收回监听通过事件目标排除其区域，避免“发送后立即收起”。 */
    private static void buildReplyFull(Table box, TriMail mail, String text, boolean done, Runnable onCollapse){
        box.clearListeners();
        box.clearChildren();
        box.top().left();
        box.touchable = Touchable.enabled; // 防御：确保整表可点击（点击收回）

        // 发件人 / 收件人：带标注字样（本地化）；文字不拦截点击（点击整表任意处可收回）
        Label from = new Label(bundle("tri.mail.reply.from") + bundle("tri.mail.reply.sender"));
        from.touchable = Touchable.disabled;
        box.add(from).left().pad(12).row();
        Label to = new Label(bundle("tri.mail.reply.to") + mail.sender);
        to.touchable = Touchable.disabled;
        box.add(to).left().pad(12).row();

        // 分割线：收发信息与回信正文之间（同信件格式）
        Image divider = new Image(Tex.whiteui, Color.gray);
        divider.touchable = Touchable.disabled;
        box.add(divider).growX().height(2).padLeft(12).padRight(12).row();

        // 发送按钮引用：不预先加入布局（空文本 Label 与按钮占位都会造成空行）；
        // 供收回判定排除其区域，且仅打字机态/无内容态动态加入
        TextButton sendBtn = new TextButton(bundle("tri.mail.send"));
        boolean[] btnAdded = {false};

        // 点击整表收回。arc 事件冒泡：点击框内任意子元素都会冒泡到本表触发 clicked；
        // 若本次点击目标是发送按钮（或其子元素），由按钮自身处理，不收回
        Element[] clickTarget = {null};
        box.addListener(new ClickListener(){
            @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, KeyCode button){
                clickTarget[0] = e.targetActor;
                return super.touchDown(e, x, y, pointer, button);
            }
            @Override public void clicked(InputEvent e, float x, float y){
                if(clickTarget[0] != null && (clickTarget[0] == sendBtn || clickTarget[0].isDescendantOf(sendBtn))) return;
                if(onCollapse != null){
                    onCollapse.run();
                }else{
                    buildReplyMini(box, mail, text);
                }
            }
        });

        if(done){
            // 已回信查看：直接用文本创建正文（避免空 Label 占行），无发送按钮
            Label full = new Label(text);
            full.touchable = Touchable.disabled;
            full.setWrap(true);
            full.setAlignment(Align.topLeft);
            box.add(full).left().top().growX().pad(12).row();
            return;
        }

        // 发送：执行触发动作 + 记忆状态；发送后默认展开（“默认开始”：完整回信显示，无按钮、无空行）
        sendBtn.clicked(() -> {
            if(mail.onReply != null) mail.onReply.run();
            TriInbox.markReplied(mail);
            buildReplyFull(box, mail, text, true, null);
        });

        if(text.isEmpty()){
            box.row();
            box.add(sendBtn).size(100f, 44f).pad(8);
            return;
        }

        // 打字机：逻辑挂在 box 上（box 始终在场景中才会执行 update）；
        // typed 初始不加入布局（空文本占行导致空行），出现第一个字后动态加入；
        // 播放完成后动态加入“发送”按钮
        Label typed = new Label("");
        typed.touchable = Touchable.disabled;
        typed.setWrap(true);
        typed.setAlignment(Align.topLeft);
        float speed = mail.replySpeed > 0 ? mail.replySpeed : 24f;
        float start = Time.time;
        int[] last = {0};
        boolean[] typedAdded = {false};
        box.update(() -> {
            int shown = Math.min(text.length(), (int)((Time.time - start) * speed));
            if(shown != last[0]){
                last[0] = shown;
                typed.setText(text.substring(0, shown));
                if(shown > 0 && !typedAdded[0]){
                    typedAdded[0] = true;
                    box.row();
                    box.add(typed).left().top().growX().pad(12).row();
                }
            }
            if(shown >= text.length() && !btnAdded[0]){
                btnAdded[0] = true;
                box.row();
                box.add(sendBtn).size(100f, 44f).pad(8);
            }
        });
    }

    /** 按当前分类过滤（历史按最后阅读时间倒序）。传入已解锁邮件列表，避免重复遍历。 */
    private static Seq<TriMail> mailsFor(Filter f, Seq<TriMail> all){
        switch(f){
            case READ: return filter(all, true);
            case UNREAD: return filter(all, false);
            case HISTORY:{
                Seq<TriMail> r = filter(all, true);
                r.sort((a, b) -> Long.compare(TriInbox.readTime(b), TriInbox.readTime(a))); // 最后阅读的在前
                return r;
            }
            default: return all; // ALL，且限定当前作用域
        }
    }

    /** 按已读状态过滤出新的列表（复用传入的解锁列表，不重复遍历 TriInbox）。 */
    private static Seq<TriMail> filter(Seq<TriMail> all, boolean read){
        Seq<TriMail> r = new Seq<>();
        for(TriMail m : all) if(TriInbox.isRead(m) == read) r.add(m);
        return r;
    }

    /** 统计某分类中的未读数量（已读/历史类恒为 0；全部/未读类返回未读总数，一次遍历计数）。 */
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
