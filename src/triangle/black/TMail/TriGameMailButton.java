package triangle.black.TMail;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.Label.LabelStyle;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.ui.Styles;
import triangle.black.TriIcon;

/**
 * 战役地图内的悬浮 TMail 入口：
 * 外层为更大的透明 Table（可点击/拖动区域），内部按钮仅含 Tmail 图标（居中），
 * 背景为原版默认灰色按钮（Styles.defaultt）；
 * 未读角标为独立 Label 置于外层容器右上角。
 *
 * 短按按下后触发统一预览流程（TriMailPreview.trigger）：
 * 战役模式（Vars.state.isCampaign()）→ 剧情邮件预览，自定义游戏 → 模组资讯预览；
 * 长按（0.3 秒）后移动进入拖动模式，可自由移动位置；
 * 默认位置为屏幕左下角（容器左下角贴屏幕左下角）。
 *
 * 仅当玩家处于游戏内（state.isGame()）时显示；离开地图时移除；
 * 重新进入地图时按默认位置重新创建。
 */
public class TriGameMailButton{

    /** 长按进入拖动模式的阈值（秒）。 */
    private static final float LONG_PRESS = 0.3f;
    /** 外层透明容器相对按钮四周扩出的宽度。 */
    private static final float PAD = 14f;

    private static Table container;
    private static TextButton button;
    private static Label badge;
    /** 上次展示的未读数，用于仅在数值变化时更新角标文本。 */
    private static int lastUnread = -1;
    private static boolean registered = false;

    private TriGameMailButton(){}

    /** 注册（在 triangle 主类构造中调用一次）。 */
    public static void init(){
        if(registered) return;
        registered = true;
        Events.run(EventType.Trigger.update, TriGameMailButton::update);
    }

    private static void update(){
        // 游戏内显示：战役模式（isCampaign）→ 剧情；自定义游戏 → 资讯
        boolean inGame = Vars.state.isGame();
        if(inGame){
            if(container == null || !container.hasParent()){
                create();
            }

            // 角标：仅未读数变化时更新文本，避免每帧重建 Label
            boolean inCampaign = Vars.state.isCampaign();
            int unread = inCampaign ? TriInbox.unreadCount() : TriInbox.unreadCount(false);
            if(unread != lastUnread){
                lastUnread = unread;
                badge.setText(unread > 0 ? "[accent](" + unread + ")" : "");
            }
        }else if(container != null){
            container.remove();
            container = null;
            button = null;
            badge = null;
            lastUnread = -1;
        }
    }

    private static void create(){
        // 外层透明容器：可点击/可拖动区域（按钮居中 + 角标右上角）
        container = new Table();
        container.touchable = Touchable.enabled;

        // 按钮：仅 Tmail 图标（居中），背景为原版默认灰色按钮
        button = new TextButton("", Styles.defaultt);
        button.clearChildren();
        button.add(new Image(TriIcon.tex("Tmail"))).size(36f).expand().center();
        button.pack();
        // 进一步扩大：内容尺寸基础上四周再扩展，使按钮更醒目
        button.setSize(button.getPrefWidth() + 16f, button.getPrefHeight() + 24f);

        // 角标：独立 Label，置于外层容器右上角（不参与按钮内容）；复用默认按钮字体
        badge = new Label("", new LabelStyle(Styles.defaultt.font, Styles.defaultt.fontColor));

        // 手动布局：容器比按钮四周各宽 PAD；按钮居中、角标贴右上角。
        // 直接 addChild 的元素不参与 Table 布局（无 cell），位置由 setPosition 手动控制。
        float bw = button.getWidth(), bh = button.getHeight();
        container.setSize(bw + PAD * 2f, bh + PAD * 2f);
        container.addChild(button);
        container.addChild(badge);
        button.setPosition(container.getWidth() / 2f, container.getHeight() / 2f, Align.center);
        badge.setPosition(container.getWidth(), container.getHeight(), Align.topRight);

        // 长按拖动 / 短按打开（作用于整个容器，含透明区域）
        container.addListener(new DragListener());
        // 挂到游戏内 HUD 组（自由定位）
        Vars.ui.hudGroup.addChild(container);
        // 默认位置：屏幕左下角（容器左下角贴屏幕左下角）
        container.setPosition(0f, 0f, Align.bottomLeft);
        lastUnread = -1;
    }

    /**
     * 长按（0.3 秒）后移动进入拖动模式；未实际移动过（无论按住时长）抬起即视为点击：
     * 触发统一预览流程（TriMailPreview.trigger，战役/自定义自动分流）。
     * 拖动判定只看“是否真正进入过拖动模式”，避免长按未移动时既不算点击也不算拖动的死区。
     */
    static class DragListener extends InputListener{
        private boolean down;
        private boolean dragging;
        private float pressTime;
        private float lastX, lastY;

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode code){
            if(pointer != 0) return false;
            down = true;
            dragging = false;
            pressTime = Time.time;
            // InputEvent.stageX/stageY 为场景（Stage）坐标
            lastX = event.stageX;
            lastY = event.stageY;
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer){
            if(!down || pointer != 0) return;
            if(!dragging && Time.time - pressTime > LONG_PRESS){
                dragging = true;
            }
            if(dragging && container != null){
                float mx = event.stageX, my = event.stageY;
                container.moveBy(mx - lastX, my - lastY);
                lastX = mx;
                lastY = my;
            }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode code){
            if(pointer != 0) return;
            boolean wasDragging = dragging;
            down = false;
            dragging = false;
            // 未实际拖动（无论按住时长）即视为点击：直接打开对应收件箱
            // 战役模式（isCampaign）→ 三角邮箱；自定义游戏 → Tmail资讯
            if(!wasDragging){
                TriMailDialog.show(Vars.state.isCampaign());
            }
        }
    }
}
