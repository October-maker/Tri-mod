package triangle.black.TMail;

import arc.Core;
import arc.graphics.Color;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * Tmail 主界面“获取最新资讯”的过渡与加载状态。
 *
 * <p>从 {@link TriMailDialog} 中抽出的刷新流程控制器，负责：
 * <ul>
 *   <li>手动刷新时让旧 content 向下移出并删除；</li>
 *   <li>在屏幕右 3/4 处显示“正在获取模组最新资讯”；</li>
 *   <li>获取完成/失败时删除加载提示并触发 content 重建；</li>
 *   <li>记录刷新期间到达的成功/失败回调，避免网络快于动画时丢失事件。</li>
 * </ul>
 */
final class TriMailRefreshController{

    private static Table loadingTable;
    private static Table statusTable;
    private static boolean transitionActive;
    private static boolean pendingUpdate;
    private static boolean pendingFailure;

    /** 单个字符完整跳落动画时长（秒）。 */
    private static final float WAVE_DURATION = 0.7f;
    /** 单字从原位弹到最高点的时间占比。 */
    private static final float WAVE_RISE = 0.25f;
    /** 相邻字符的起始延迟（秒）。 */
    private static final float WAVE_STAGGER = 0.06f;
    /** 整轮波浪结束后额外静止时间（秒），随后重新触发。 */
    private static final float WAVE_REST = 0.3f;
    /** 弹起高度（像素）。 */
    private static final float WAVE_HEIGHT = 25f;

    private TriMailRefreshController(){}

    static boolean hasLoading(){
        return loadingTable != null;
    }

    /** 清除加载提示引用（页面 clearChildren 后调用，元素本身已被父节点移除）。 */
    static void clearLoadingReference(){
        loadingTable = null;
    }

    static void removeLoading(){
        if(loadingTable != null){
            loadingTable.remove();
            loadingTable = null;
        }
    }

    /** 清除成功/失败提示。 */
    private static void removeStatus(){
        if(statusTable != null){
            statusTable.remove();
            statusTable = null;
        }
    }

    /**
     * 刷新结束后用结果文字替换加载提示：结果停留 1 秒，然后一边渐隐，
     * 一边让重建后的 content 卡片组滑入。
     */
    private static void showRefreshResult(boolean success, boolean rebuildContent){
        removeLoading();
        removeStatus();

        BaseDialog inbox = TriMailDialog.inbox;
        if(inbox == null || !inbox.isShown()) return;

        Label result = new Label(
                TriMailDialog.bundle(success ? "tri.mail.refreshSuccess" : "tri.mail.refreshFailedShort"),
                new Label.LabelStyle(Styles.defaultt.font,
                        success ? Color.valueOf("FFD37F") : Color.valueOf("FF5A5A")));

        Table status = new Table();
        status.touchable = Touchable.disabled;
        status.add(result)
                .padLeft(Core.graphics.getWidth() * 0.5f)
                .center();
        inbox.addChild(status);
        status.setFillParent(true);
        statusTable = status;

        Time.runTask(30f, () -> {
            if(statusTable != status) return;

            // 文字与 content 同时开始：结果渐隐，卡片组从右侧滑入。
            if(rebuildContent){
                TriMailDialog.build(inbox);
            }
            status.actions(
                    Actions.alpha(0f, 0.3f, Interp.pow2In),
                    Actions.remove()
            );
            statusTable = null;
        });
    }

    /** 开启一次手动刷新过渡；自动刷新不触发该动画。 */
    static void onRefreshStarted(boolean forced){
        removeStatus();
        if(TriMailDialog.latestInfoButton != null){
            TriMailDialog.latestInfoButton.setDisabled(true);
        }

        if(!forced || TriMailDialog.storyScope || TriMailDialog.inbox == null
                || !TriMailDialog.inbox.isShown() || TriMailDialog.exitAnimating || transitionActive){
            return;
        }

        transitionActive = true;
        pendingUpdate = false;
        pendingFailure = false;

        Table content = TriMailDialog.lastContent;
        if(content == null){
            transitionActive = false;
            if(loadingTable == null){
                showLoadingTable(false);
            }
            return;
        }

        content.clearActions();
        content.actions(
                TriMailDialog.moveTo(content.x, -content.getHeight(),
                        TriMailDialog.CONTENT_SWEEP_DURATION, Interp.pow2In),
                Actions.run(() -> {
                    if(TriMailDialog.lastContent != content) return;

                    TriMailDialog.clearPage();
                    transitionActive = false;
                    showLoadingTable(false);
                    finishPending();
                })
        );
    }

    /** 在屏幕右 3/4 处显示加载提示；初次进入时可从右侧滑入。 */
    static void showLoadingTable(boolean slideIn){
        removeLoading();
        removeStatus();
        BaseDialog inbox = TriMailDialog.inbox;
        if(inbox == null || !inbox.isShown()) return;

        Table loading = new Table();
        // 逐字拆分：每个字符独立一个 Label，由各自 update 计算竖直位移；
        // 相邻字符的相位依次延后，形成从左到右弹起再落回原位的波浪。
        String text = TriMailDialog.bundle("tri.mail.refreshingLatest").replace("[accent]", "");
        Table word = new Table();
        // 使用真实时间的毫秒时钟，而不是 Time.time：
        // Mindustry 的 Time.delta 以 tick 为单位，Time.time 会随帧率/60Hz 逻辑加速。
        long waveStart = Time.millis();
        int charIndex = 0;
        int charCount = text.codePointCount(0, text.length());
        // 最后一字开始时间 + 单字动画 + 静止段，构成整轮循环。
        float cycleDuration = WAVE_DURATION + Math.max(0, charCount - 1) * WAVE_STAGGER + WAVE_REST;
        for(int i = 0; i < text.length(); ){
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            String glyph = new String(Character.toChars(codePoint));
            BouncingLabel charLabel = new BouncingLabel("[accent]" + glyph);
            final int index = charIndex++;
            charLabel.update(() -> {
                float cycleTime = ((Time.millis() - waveStart) / 1000f) % cycleDuration;
                float elapsed = cycleTime - index * WAVE_STAGGER;
                float y = 0f;

                if(elapsed >= 0f && elapsed <= WAVE_DURATION){
                    float t = elapsed / WAVE_DURATION;
                    // 先短促弹起，再用原版 Interp.bounceOut 做自由落体后的阻尼弹跳。
                    if(t < WAVE_RISE){
                        y = WAVE_HEIGHT * Interp.pow2Out.apply(t / WAVE_RISE);
                    }else{
                        y = WAVE_HEIGHT * (1f - Interp.bounceOut.apply(
                                (t - WAVE_RISE) / (1f - WAVE_RISE)));
                    }
                }
                charLabel.waveOffset = y;
            });
            word.add(charLabel);
        }
        loading.add(word)
                .padLeft(Core.graphics.getWidth() * 0.5f)
                .center();
        inbox.cont.add(loading).grow();
        loadingTable = loading;

        if(!slideIn){
            inbox.cont.invalidate();
            return;
        }

        loading.visible = false;
        inbox.cont.validate();
        loading.setLayoutEnabled(false);
        float targetX = loading.x, targetY = loading.y;
        float sw = Core.graphics.getWidth();

        Time.runTask(1f, () -> {
            if(loadingTable != loading || inbox == null || !inbox.isShown()) return;

            loading.setPosition(sw + loading.getWidth(), targetY);
            loading.visible = true;
            loading.clearActions();
            loading.actions(TriMailDialog.moveTo(targetX, targetY,
                    TriMailDialog.SLIDE_IN_DURATION, Interp.pow2Out));
        });
    }

    /** 刷新结束（成功或失败）后恢复按钮。 */
    static void onRefreshFinished(){
        if(TriMailDialog.latestInfoButton != null){
            TriMailDialog.latestInfoButton.setDisabled(false);
        }
    }

    static void onRefreshFailed(){
        if(transitionActive){
            pendingFailure = true;
            return;
        }

        if(!TriMailDialog.storyScope && TriMailDialog.inbox != null
                && TriMailDialog.inbox.isShown()){
            showRefreshResult(false, TriMailDialog.activeDetail == null);
        }
    }

    static void onUpdated(){
        if(TriMailDialog.storyScope || TriMailDialog.inbox == null
                || !TriMailDialog.inbox.isShown() || TriMailDialog.exitAnimating){
            return;
        }

        if(transitionActive){
            pendingUpdate = true;
            return;
        }

        showRefreshResult(true, TriMailDialog.activeDetail == null);
    }

    private static void finishPending(){
        if(!pendingUpdate && !pendingFailure) return;

        boolean success = pendingUpdate;
        pendingUpdate = false;
        pendingFailure = false;

        if(TriMailDialog.inbox != null && TriMailDialog.inbox.isShown() && !TriMailDialog.storyScope){
            showRefreshResult(success, true);
        }
    }

    /**
     * 逐字弹跳专用 Label。
     *
     * <p>不使用 Element.translation，而是在 draw 时临时偏移 y。
     * 这样可以避开 Table/Cell 在布局阶段对子元素位置的覆盖，
     * 同时不影响 Label 的布局尺寸。
     */
    private static class BouncingLabel extends Label{
        float waveOffset;

        BouncingLabel(CharSequence text){
            super(text);
        }

        @Override
        public void draw(){
            float oldY = y;
            y += waveOffset;
            super.draw();
            y = oldY;
        }
    }
}
