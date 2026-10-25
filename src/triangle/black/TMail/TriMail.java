package triangle.black.TMail;

import arc.func.Boolp;

/**
 * 一封叙事邮件：内容数据 + 战役进度解锁条件 + 可选附件（收取时执行动作）。
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
    /** 是否有附件（可选，编写信件时可省略）。 */
    public final boolean hasAttachment;
    /** 附件名称/说明（在正文下方显示）。 */
    public final String attachment;
    /** 收取附件时执行的动作（如强制解锁科技）。 */
    public final Runnable onCollect;
    /** 按下“回信”按钮时执行的事件触发器（可空：仅展示回信框，不触发额外动作）。 */
    public final Runnable onReply;
    /**
     * 回信正文：非空时直接使用（硬编码文本，如中文示例信，不参与 bundle 本地化）；
     * 为 null 时回信框回退到 bundle 键 tri.mail.reply.&lt;key&gt;。
     */
    public final String replyText;
    /** 回信打字机速度（字符/秒）；&lt;=0 表示未设置，使用默认 24 字/秒。 */
    public final float replySpeed;

    /** 无附件、无回信事件。 */
    public TriMail(String key, String sender, String subject, String body, Boolp unlocked){
        this(key, sender, subject, body, unlocked, true);
    }

    /** 无附件、无回信事件。 */
    public TriMail(String key, String sender, String subject, String body, Boolp unlocked, boolean story){
        this(key, sender, subject, body, unlocked, story, null, null, null, null, -1f);
    }

    /** 带附件。 */
    public TriMail(String key, String sender, String subject, String body, Boolp unlocked, boolean story, String attachment, Runnable onCollect){
        this(key, sender, subject, body, unlocked, story, attachment, onCollect, null, null, -1f);
    }

    /** 带附件与回信事件触发器（按下“发送”按钮时执行；原回信按钮触发器保留备用）。 */
    public TriMail(String key, String sender, String subject, String body, Boolp unlocked, boolean story, String attachment, Runnable onCollect, Runnable onReply){
        this(key, sender, subject, body, unlocked, story, attachment, onCollect, onReply, null, -1f);
    }

    /** 带附件、回信事件触发器与硬编码回信正文（replyText 非空时不用 bundle）。 */
    public TriMail(String key, String sender, String subject, String body, Boolp unlocked, boolean story, String attachment, Runnable onCollect, Runnable onReply, String replyText){
        this(key, sender, subject, body, unlocked, story, attachment, onCollect, onReply, replyText, -1f);
    }

    /** 全参数构造器：可同时指定回信打字机速度（replySpeed 字符/秒；&lt;=0 默认 1）。 */
    public TriMail(String key, String sender, String subject, String body, Boolp unlocked, boolean story, String attachment, Runnable onCollect, Runnable onReply, String replyText, float replySpeed){
        this.key = key;
        this.sender = sender;
        this.subject = subject;
        this.body = body;
        this.unlocked = unlocked;
        this.story = story;
        this.hasAttachment = attachment != null;
        this.attachment = attachment;
        this.onCollect = onCollect;
        this.onReply = onReply;
        this.replyText = replyText;
        this.replySpeed = replySpeed;
    }
}
