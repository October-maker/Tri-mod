package triangle.black.TMail;

import arc.Core;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import mindustry.ui.dialogs.BaseDialog;

/**
 * 三角通讯收件箱界面：列出邮件，未解锁置灰，点击已解锁邮件查看正文。
 */
public class TriMailDialog{

    private TriMailDialog(){}

    /** 打开收件箱。 */
    public static void show(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("tri.mail.title", "三角通讯"));
        dialog.addCloseButton();

        // 顶部：全部已读
        dialog.cont.button(Core.bundle.get("tri.mail.markAll", "全部已读"), () -> {
            for(TriMail m : TriInbox.unlocked()) TriInbox.markRead(m);
            dialog.hide();
            show(); // 重建并刷新
        }).width(120f).pad(4).row();

        // 邮件列表：仅显示已“送达”（解锁）的邮件；未解锁的邮件在达到条件后才会出现在收件箱
        Table list = new Table();
        list.left();
        for(TriMail mail : TriInbox.unlocked()){
            list.row();
            list.add(mailRow(mail)).growX().pad(2);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setOverscroll(false, false);
        dialog.cont.add(scroll).grow().pad(4);

        dialog.show();
    }

    /** 单封已送达邮件的行 UI。 */
    private static Table mailRow(TriMail mail){
        Table row = new Table();
        row.left();

        boolean read = TriInbox.isRead(mail);
        row.add(mail.sender).padRight(8);
        // 未读主题用高亮色
        String subj = read ? mail.subject : "[accent]" + mail.subject + "[/]";
        row.add(subj).growX();
        row.button(Core.bundle.get("tri.mail.open", "阅读"), () -> openMail(mail)).width(60f).padLeft(8);
        return row;
    }

    /** 打开一封已解锁邮件的正文（并自动标记已读）。 */
    private static void openMail(TriMail mail){
        TriInbox.markRead(mail);

        BaseDialog dialog = new BaseDialog(mail.subject);
        dialog.cont.add("[accent]" + mail.sender + "[/]").left().pad(8).row();
        dialog.cont.add(mail.body).left().top().wrap().width(560).pad(8).row();
        dialog.cont.button(Core.bundle.get("tri.mail.back", "返回"), () -> {
            dialog.hide();
            show(); // 返回收件箱并刷新已读状态
        }).size(100f, 50f);
        dialog.addCloseButton();
        dialog.show();
    }
}
