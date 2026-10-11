package triangle.black.TMail;

import arc.func.Boolp;

/**
 * 一封叙事邮件：内容数据 + 战役进度解锁条件。
 */
public class TriMail{

    /** 唯一标识，用于持久化已读状态（Core.settings 键：tri-mail-read-&lt;key&gt;）。 */
    public final String key;
    /** 发件人。 */
    public final String sender;
    /** 主题。 */
    public final String subject;
    /** 正文（可含换行）。 */
    public final String body;
    /** 解锁条件：返回 true 表示当前战役进度已解锁该邮件。 */
    public final Boolp unlocked;
    /** 类型：true=战役剧情邮件（战役星球 TMail 显示）；false=模组资讯邮件（主菜单 TMail 显示）。 */
    public final boolean story;

    public TriMail(String key, String sender, String subject, String body, Boolp unlocked){
        this(key, sender, subject, body, unlocked, true);
    }

    public TriMail(String key, String sender, String subject, String body, Boolp unlocked, boolean story){
        this.key = key;
        this.sender = sender;
        this.subject = subject;
        this.body = body;
        this.unlocked = unlocked;
        this.story = story;
    }
}
