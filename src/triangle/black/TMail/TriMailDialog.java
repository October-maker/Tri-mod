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
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import triangle.black.TriDraw;

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
    /** 分类栏四个按钮引用（按 ALL/READ/UNREAD/HISTORY 顺序），供 refreshReadState 更新未读角标。 */
    private static final TextButton[] catButtons = new TextButton[4];
    private static final Filter[] CATS = { Filter.ALL, Filter.READ, Filter.UNREAD, Filter.HISTORY };
    /** 当前邮件列表 Table（scroll 内容），供“未读”分类下移除已读卡片（轻量刷新，不重建收件箱）。 */
    private static Table listTable;
    /** 已构建卡片的发件人 Label 引用（mail → sender Label），供 refreshReadState 局部更新未读/已读高亮（方案B）。 */
    private static final ObjectMap<TriMail, Label> senderLabels = new ObjectMap<>();
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
        build(inbox); // 删除旧 content + rebuildContent（新建 content 并从屏幕右外侧滑入右 3/4 处）
    }

    /** 重建收件箱内容（删除旧 content 后，新建 content 并从屏幕右外侧滑入右 3/4 处）。
     *  删除旧 content 的逻辑不内置在本方法——调用方须在调用前自行移除旧 content
     *  （不同调用方可按不同样式删除：直接清空、淡出、滑出等）。 */
    private static void build(BaseDialog dlg){
        // 删除旧 content：这里直接清空 cont（如需其他删除样式，可在调用 rebuildContent 前自行处理）
        dlg.cont.clearChildren();
        senderLabels.clear(); // 旧卡片引用作废（即将重建）
        Seq<TriMail> all = TriInbox.unlocked(storyScope);
        rebuildContent(dlg, all, SLIDE_IN_DURATION);
    }

    /** 新建收件箱 content 卡片列表：在屏幕右外侧新建（分类栏 + 卡片列表），随后滑入右 3/4 处。
     *  不内置删除旧 content 的逻辑——调用方须在调用前自行移除旧 content（可按不同样式删除）。
     *  @param dlg 收件箱对话框；@param all 已解锁邮件列表快照；@param slideDuration 滑入时长（秒），可定制效果 */
    private static void rebuildContent(BaseDialog dlg, Seq<TriMail> all, float slideDuration){
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
        // 刷新按钮（方案B）：手动刷新已读/未读状态（角标 + 未读列表），样式同分类按钮；不入 ButtonGroup（非分类切换）
        // 【暂时注释】待用户决定是否恢复
        // TextButton refreshBtn = new TextButton(bundle("tri.mail.refresh"));
        // refreshBtn.clicked(() -> refreshReadState());
        // bar.add(refreshBtn).size(96f, 42f).pad(3);
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
        listTable = list; // 记录列表引用，供 refreshReadState 在“未读”分类下移除已读卡片

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

        // 方案1：延迟期间隐藏 content（本帧完全不可见，杜绝“中间闪现/闪烁”）；
        // 延迟一帧后（此时 Dialog.show()/父布局已稳定）在回调内定位屏幕右外侧 + 注册滑入动画——
        // 定位动作发生在布局稳定之后，不会再被父级 cont 的布局 validate 覆盖（setLayoutEnabled 只冻结 content 自身布局，不阻止父重排）。
        content.visible = false;
        Time.runTask(1f, () -> {
            // 若延迟期间 content 已被重建（lastContent 换新），跳过本次旧滑入任务，由新 build 自己的滑入处理
            if(!dlg.isShown() || lastContent != content) return;
            float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
            float cw = lastContent.getWidth(), ch = lastContent.getHeight();
            // 目标：content 中心位于屏幕右半屏中心（右 3/4 处）
            float targetScreenX = sw * 0.75f, targetScreenY = sh / 2f;
            // content 坐标是相对 cont 的局部坐标：
            // 局部目标 = 屏幕目标 - dialog 左下角(dlg.x/y) - cont 相对 dialog 偏移(dlg.cont.x/y) - content 半宽/半高
            float targetLocalX = targetScreenX - dlg.x - dlg.cont.x - cw / 2f;
            float targetLocalY = targetScreenY - dlg.y - dlg.cont.y - ch / 2f;
            contentHomeX = targetLocalX; // 记录“家”位置：打开详情时 content 向右滑出、关闭详情后滑回此处
            contentHomeY = targetLocalY;
            lastContent.setPosition(sw + cw, targetLocalY); // 起点：屏幕右外侧（布局已稳定，定位生效）
            lastContent.visible = true; // 恢复可见，随即滑入
            lastContent.clearActions();
            lastContent.actions(Actions.moveTo(targetLocalX, targetLocalY, slideDuration, Interp.pow2Out));
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

    /** 分类栏按钮下标（ALL/READ/UNREAD/HISTORY 顺序）。 */
    private static int catIndex(Filter f){
        for(int i = 0; i < 4; i++) if(CATS[i] == f) return i;
        return 0;
    }

    /** 仅刷新已读/未读状态：更新分类栏未读角标；局部更新已存在卡片的发件人高亮（方案B）；若当前是“未读”分类，将已读信件从列表移除。
     *  不重建整个收件箱（不 clearChildren cont），因此不会打断 content 的滑回动画。 */
    private static void refreshReadState(){
        if(inbox == null || !inbox.isShown()) return;
        Seq<TriMail> all = TriInbox.unlocked(storyScope);
        // 更新四个分类按钮的未读角标
        for(int i = 0; i < 4; i++){
            TextButton b = catButtons[i];
            if(b == null) continue;
            int unread = unreadIn(all, CATS[i]);
            String text = bundle("tri.mail.cat." + CATS[i].name().toLowerCase());
            if(unread > 0) text += " [accent](" + unread + ")";
            b.setText(text);
        }
        // 方案B：局部更新已存在卡片的发件人高亮（已读 → 普通色，未读 → accent），不动卡片布局
        for(ObjectMap.Entry<TriMail, Label> e : senderLabels){
            TriMail m = e.key;
            Label lbl = e.value;
            lbl.setText(TriInbox.isRead(m) ? m.sender : "[accent]" + m.sender);
        }
        // “未读”分类：移除已读信件（重建列表内容；不影响 content 滑回动画）
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

    /** 单封邮件的固定大小卡片：圆角矩形包裹，上发件人、中分割线、下内容（省略）。整卡可点击阅读。 */
    private static Table card(TriMail mail){
        Table card = new Table();
        card.top().left();
        // 圆角矩形包裹 + 白色描边（复用 TriDraw.whiteOutlined 的圆角描边绘制）
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
        senderLabels.put(mail, sender); // 记录引用，供 refreshReadState 局部刷新未读/已读高亮（方案B）

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
        detail.background(TriDraw.whiteOutlined(Styles.grayPanel)); // 白色描边
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
        // 方案A：所有退出路径（× / 透明按钮 / 已读）统一刷新已读/未读状态（角标 + 发件人高亮 + 未读列表）
        refreshReadState();
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
                refreshReadState(); // 方案A/E：附件收取后刷新已读/未读状态（角标 + 列表）
            });
            content.add(collect).size(130f, 44f).pad(8).row();
        }

        // 回信区（仅战役剧情信件显示）：按钮控制；按下后在邮件下方展开带描边的回信框
        if(mail.story){
            content.add(replySection(mail)).left().row();
        }

        // “已读”按钮：被按下时与退出按钮/透明按钮一样触发退出（关闭详情：恢复卡片 + content 滑回）；
        // 已读状态时按钮 disabled（不可按下），文字置灰（回归改动前：仅文字置灰，不整体着色）
        TextButton readBtn = new TextButton(bundle("tri.mail.back"));
        if(TriInbox.isReadBtnClicked(mail)){
            readBtn.setDisabled(true); // 已读：disabled 不可按下
            readBtn.getLabel().setColor(Color.gray); // 文字置灰（回归改动前）
        }
        readBtn.clicked(() -> {
            TriInbox.markReadBtnClicked(mail); // 记忆置灰状态
            readBtn.setDisabled(true); // 置灰 + 不可按下
            readBtn.getLabel().setColor(Color.gray); // 文字置灰（回归改动前）
            if(onExit != null) onExit.run(); // 触发同退出的行为：关闭详情（closeDetail 内已统一调用 refreshReadState）
        });
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
            box.background(TriDraw.whiteOutlined(Styles.grayPanel));
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
        box.background(TriDraw.whiteOutlined(Styles.grayPanel));
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
            refreshReadState(); // 方案E：回信发送完成后刷新已读/未读状态（角标 + 列表）
        });

        if(text.isEmpty()){
            box.row();
            box.add(sendBtn).size(100f, 44f).pad(8);
            return;
        }

        // 打字机：复用 TriDraw.typewriter（逻辑挂在 box 上，box 始终在场景中才会执行 update）；
        // typed 初始不加入布局（空文本占行导致空行），首字出现时动态加入；播放完成后动态加入“发送”按钮。
        // 回调内引用 typed 需经数组间接化：lambda 不能在变量自身初始化表达式中引用它（编译错误）
        Label[] typedRef = {null};
        Label typed = TriDraw.typewriter(box, text, mail.replySpeed > 0 ? mail.replySpeed : 24f, Align.topLeft,
            () -> { box.row(); box.add(typedRef[0]).left().top().growX().pad(12).row(); },
            () -> { box.row(); box.add(sendBtn).size(100f, 44f).pad(8); });
        typedRef[0] = typed; // 首字回调在下一次 update（≥1 帧）才触发，此时已赋值
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
