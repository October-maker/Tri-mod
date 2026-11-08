package triangle.black.TriMiniGame;

import arc.Core;
import arc.Events;
import arc.math.Interp;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

/**
 * 向主菜单“开始游戏”展开的 submenu 注入“特殊模式”按钮。
 *
 * <p>原版 MenuFragment 会在打开/关闭子菜单时清空 submenu，因此与
 * {@code TriMainMenuButton} 一样采用自愈注入：按钮丢失时才重新扫描。
 *
 * <p>尺寸不显式指定：submenu 自身的 {@code defaults()} 已把单元格设为
 * “菜单宽 × 70”，交给原版布局即可与“战役模式”“加入游戏”等按钮完全一致；
 * 样式同样复用原版的 {@code Styles.flatToggleMenut}，并沿用 {@code marginLeft(11f)}。
 */
public final class TriPresetMenu{

    private static TextButton injected;
    private static Table submenu;

    /** 入口按钮图标（暂时使用游戏内的 sharder 贴图；缺失时退回原版 play 图标）。 */
    private static Drawable icon(){
        return Core.atlas.has("sharder")
                ? new TextureRegionDrawable(Core.atlas.find("sharder"))
                : Icon.play;
    }

    private TriPresetMenu(){}

    public static void init(){
        Events.run(Trigger.update, TriPresetMenu::update);
    }

    private static void update(){
        if(Vars.ui == null || Vars.ui.menuGroup == null) return;

        if(injected != null && attached(injected)) return;

        if(submenu == null || !attached(submenu)){
            submenu = findSubmenu(Vars.ui.menuGroup);
            injected = null;
            if(submenu == null) return;
        }

        // 子菜单未打开时是空表，避免往隐藏菜单里塞按钮。
        if(submenu.getChildren().isEmpty()) return;

        if(injected != null) injected.remove();

        // 与 MenuFragment.buttons() 完全一致：button(text, icon, style, runnable).marginLeft(11f)，末尾另起一行
        submenu.row();
        injected = (TextButton)submenu
                .button(Core.bundle.get("tri.preset.entry", "tri.preset.entry"), icon(),
                        Styles.flatToggleMenut, () -> {
                            fadeOutSubmenu();
                            TriPresetDialog.open();
                        })
                .marginLeft(11f)
                .get();
        submenu.row();

        // flatToggleMenut 是 toggle 样式：点击后 checked 会被记住，松开仍显示“按下”贴图。
        // 每帧把 checked 对齐到“是否正被按下”，只在按下时显示按下贴图（同原版 setChecked(currentMenu == b) 的效果）。
        injected.update(() -> injected.setChecked(injected.isPressed()));
    }

    /**
     * 与原版 MenuFragment.fadeOutMenu() 相同的收尾：先瞬间复位 alpha，再淡出 0.2s，最后清空子菜单。
     * 这样点进入口按钮后，按钮所属的栏位会像原版一样整列淡出消失（而不是留在原处被对话框盖住）。
     */
    private static void fadeOutSubmenu(){
        if(submenu == null || submenu.getChildren().isEmpty()) return;

        submenu.clearActions();
        submenu.actions(
                Actions.alpha(1f),
                Actions.alpha(0f, 0.2f, Interp.fade),
                Actions.run(submenu::clearChildren)
        );
    }

    private static boolean attached(Element element){
        for(Element p = element; p != null; p = p.parent){
            if(p == Vars.ui.menuGroup) return true;
        }
        return false;
    }

    private static Table findSubmenu(Group group){
        for(Element e : group.getChildren()){
            if(e instanceof Table table && "submenu".equals(table.name)){
                return table;
            }
            if(e instanceof Group child){
                Table result = findSubmenu(child);
                if(result != null) return result;
            }
        }
        return null;
    }
}
