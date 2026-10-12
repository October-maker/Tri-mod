package triangle.black.TMail;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
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
        }
        inbox.title.setText(story ? bundle("tri.mail.title") : bundle("tri.modinfo.title"));
        inbox.show();
        build(inbox);
    }

    /** 重建收件箱内容（清空后重绘分类栏 + 邮件列表）。 */
    private static void build(BaseDialog dlg){
        dlg.cont.clearChildren();

        // 顶部居中的分类栏（放大按钮 + 未读角标）
        Table bar = new Table();
        bar.center();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        addCategory(dlg, bar, group, Filter.ALL, "tri.mail.cat.all");
        addCategory(dlg, bar, group, Filter.READ, "tri.mail.cat.read");
        addCategory(dlg, bar, group, Filter.UNREAD, "tri.mail.cat.unread");
        addCategory(dlg, bar, group, Filter.HISTORY, "tri.mail.cat.history");
        dlg.cont.add(bar).padTop(8).padBottom(4).row();

        // 竖直向下的邮件列表（居中），可滚动
        Table list = new Table();
        list.center();
        Seq<TriMail> shown = mailsFor(filter);
        if(shown.isEmpty()){
            list.add("[#7f8c8d]" + bundle("tri.mail.empty") + "[/]").pad(30);
        }else{
            for(TriMail m : shown){
                list.row();
                list.add(card(m)).size(CARD_WIDTH, CARD_HEIGHT).pad(3);
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setOverscroll(false, false);
        dlg.cont.add(scroll).grow().pad(4);
    }

    private static void addCategory(BaseDialog dlg, Table bar, ButtonGroup<TextButton> group, Filter f, String key){
        // 角标：该分类结果集中未读邮件的数量（>0 才显示）
        int unread = unreadIn(mailsFor(f));
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
        String senderText = read ? mail.sender : "[accent]" + mail.sender + "[/]";
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

        card.clicked(() -> openMail(mail));
        return card;
    }

    /** 打开一封邮件的正文（自动标记已读并记录阅读时间）。详情页限宽。 */
    private static void openMail(TriMail mail){
        TriInbox.markRead(mail);

        BaseDialog dialog = new BaseDialog(mail.subject);

        // 统一放入固定宽度的内容表，限制详情页宽度
        Table content = new Table();
        content.top().left();
        content.add("[accent]" + mail.subject).left().growX().pad(12).row();
        content.add("[accent]" + mail.sender).left().growX().pad(12).row();

        // 分割线：发件人与正文之间
        Image divider = new Image(Tex.whiteui, Color.gray);
        content.add(divider).growX().height(2).padLeft(12).padRight(12).row();

        Label body = new Label(mail.body);
        body.setWrap(true);
        body.setAlignment(Align.topLeft);
        content.add(body).left().top().growX().pad(12).row();

        // 附件：正文下方，用与发件人/正文同款的分割线分隔；可点“收取附件”执行动作
        if(TriInbox.hasAttachment(mail)){
            Image div2 = new Image(Tex.whiteui, Color.gray);
            content.add(div2).growX().height(2).padLeft(12).padRight(12).row();
            content.add("[accent]附件：[/]" + mail.attachment).left().growX().pad(12).row();

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

        content.button(bundle("tri.mail.back"), () -> {
            dialog.hide();
            build(inbox); // 返回并刷新收件箱（已读状态/历史顺序/角标）
        }).size(100f, 50f).pad(10);

        dialog.cont.add(content).width(DETAIL_WIDTH); // 限定详情页宽度
        dialog.addCloseButton();
        dialog.show();
    }

    /** 按当前分类过滤（历史按最后阅读时间倒序）。 */
    private static Seq<TriMail> mailsFor(Filter f){
        switch(f){
            case READ:{
                Seq<TriMail> r = new Seq<>();
                for(TriMail m : TriInbox.unlocked(storyScope)) if(TriInbox.isRead(m)) r.add(m);
                return r;
            }
            case UNREAD:{
                Seq<TriMail> r = new Seq<>();
                for(TriMail m : TriInbox.unlocked(storyScope)) if(!TriInbox.isRead(m)) r.add(m);
                return r;
            }
            case HISTORY:{
                Seq<TriMail> r = new Seq<>();
                for(TriMail m : TriInbox.unlocked(storyScope)) if(TriInbox.isRead(m)) r.add(m);
                r.sort((a, b) -> Long.compare(TriInbox.readTime(b), TriInbox.readTime(a))); // 最后阅读的在前
                return r;
            }
            default: return TriInbox.unlocked(storyScope); // ALL，且限定当前作用域
        }
    }

    /** 统计某列表中的未读数量。 */
    private static int unreadIn(Seq<TriMail> list){
        int n = 0;
        for(TriMail m : list) if(!TriInbox.isRead(m)) n++;
        return n;
    }

    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
