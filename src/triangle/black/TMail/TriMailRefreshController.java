package triangle.black.TMail;

import arc.Core;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.Vars;
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
    private static boolean transitionActive;
    private static boolean pendingUpdate;
    private static boolean pendingFailure;

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

    /** 开启一次手动刷新过渡；自动刷新不触发该动画。 */
    static void onRefreshStarted(boolean forced){
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
        BaseDialog inbox = TriMailDialog.inbox;
        if(inbox == null || !inbox.isShown()) return;

        Table loading = new Table();
        loading.add("[accent]" + TriMailDialog.bundle("tri.mail.refreshingLatest"))
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
        if(!TriMailDialog.storyScope && TriMailDialog.inbox != null
                && TriMailDialog.inbox.isShown() && Vars.ui != null){
            Vars.ui.showInfo(TriMailDialog.bundle("tri.mail.refreshFailed"));
        }

        if(transitionActive){
            pendingFailure = true;
            return;
        }

        if(!TriMailDialog.storyScope && TriMailDialog.inbox != null
                && TriMailDialog.inbox.isShown() && TriMailDialog.activeDetail == null){
            TriMailDialog.build(TriMailDialog.inbox);
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

        if(TriMailDialog.activeDetail != null) return;

        TriMailDialog.build(TriMailDialog.inbox);
    }

    private static void finishPending(){
        if(!pendingUpdate && !pendingFailure) return;

        pendingUpdate = false;
        pendingFailure = false;

        if(TriMailDialog.inbox != null && TriMailDialog.inbox.isShown() && !TriMailDialog.storyScope){
            TriMailDialog.build(TriMailDialog.inbox);
        }
    }
}
