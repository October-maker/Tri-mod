package triangle.black.TriMiniGame;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.ui.Styles;

/**
 * 向主菜单“开始游戏”展开的 submenu 注入“预设模式”按钮。
 *
 * <p>原版 MenuFragment 会在打开/关闭子菜单时清空 submenu，因此与
 * {@code TriMainMenuButton} 一样采用自愈注入：按钮丢失时才重新扫描。
 */
public final class TriPresetMenu{

    private static TextButton injected;
    private static Table submenu;

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

        submenu.row();
        TextButton button = new TextButton(
                Core.bundle.get("tri.preset.entry", "tri.preset.entry"), Styles.cleart);
        button.clicked(TriPresetDialog::open);
        submenu.add(button).size(260f, 54f);
        injected = button;
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
