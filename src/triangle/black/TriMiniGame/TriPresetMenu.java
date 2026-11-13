package triangle.black.TriMiniGame;

import arc.Core;
import arc.Events;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Icon;
import mindustry.ui.fragments.MenuFragment;

/**
 * 把“特殊模式”注册成主菜单“开始游戏”子菜单里的一项。
 */
public final class TriPresetMenu{

    /** 注册到子菜单里的条目（只建一次，跨帧复用以保证幂等判断走的是同一个实例）。 */
    private static MenuFragment.MenuButton entry;

    private TriPresetMenu(){}

    public static void init(){
        Events.run(Trigger.update, TriPresetMenu::update);
    }

    private static void update(){
        if(Vars.ui == null || Vars.ui.menufrag == null) return;

        Seq<MenuFragment.MenuButton> top = Vars.ui.menufrag.desktopButtons;
        if(top == null || top.size == 0) return;

        // 找到“开始游戏”那一项：优先按原版文案键 @play 匹配，取不到就退回第一个带子菜单的按钮
        MenuFragment.MenuButton play = null;
        for(MenuFragment.MenuButton button : top){
            if(button == null || button.submenu == null) continue;
            if(play == null) play = button;
            if("@play".equals(button.text)){ play = button; break; }
        }
        if(play == null) return;

        if(entry == null){
            entry = new MenuFragment.MenuButton(
                    "@tri.preset.entry",                       // 文案键，由原版创建按钮时解析成当前语言
                    icon(),
                    TriPresetDialog::open
            );
        }
        if(!play.submenu.contains(entry)) play.submenu.add(entry);
    }

    /** 入口按钮图标（暂时使用游戏内的 sharder 贴图；缺失时退回原版 play 图标）。 */
    private static Drawable icon(){
        return Core.atlas != null && Core.atlas.has("sharder")
                ? new TextureRegionDrawable(Core.atlas.find("sharder"))
                : Icon.play;
    }
}
