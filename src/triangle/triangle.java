package triangle;

import arc.util.*;
import mindustry.*;
import mindustry.mod.*;
import triangle.black.ArmorReactiveBulletType;
import triangle.black.TriArmorTurret;
import triangle.black.TMail.TriInbox;
import triangle.black.TMail.TriMainMenuButton;
import triangle.black.TMail.TriPlanetMailButton;
import triangle.black.TMail.TriSettingsDataButton;
import triangle.black.TriSetting;
import triangle.content.*;
import tmi.RecipeEntryPoint;
import triangle.tmi.TriRecipeEntry;

@RecipeEntryPoint(TriRecipeEntry.class)
public class triangle extends Mod{

    @Override
    public void loadContent(){
        // 注册自定义子弹类型到 ClassMap，使其可在 JSON 内容（如炮塔）中使用：JSON 里写 "type": "ArmorReactiveBulletType"，引擎经 ClassMap 按类名解析并无参实例化。
        ClassMap.classes.put("ArmorReactiveBulletType", ArmorReactiveBulletType.class);
        // 注册自定义炮塔类，用于在炮台详情页隐藏护甲反应弹药的默认伤害/穿甲并追加自定义横向伤害表。
        ClassMap.classes.put("TriArmorTurret", TriArmorTurret.class);

        TriItems.load();
        TriLiquids.load();
        TriFactory.load();
        TriCore.load();
        TriStatus.load();
        TriTurret.load();
        TriWeather.load();
        SerTriTechTree.load();
        // 注册叙事邮件（此时 vanilla 内容已创建，可按科技/占领进度解锁）
        TriInbox.registerDefaults();
        Vars.renderer.minZoom =0.3f;
        Vars.renderer.maxZoom = 20.0f;
        float MiZ = Vars.renderer.minZoom;
        float MaZ = Vars.renderer.maxZoom;
        Log.info("Min Zoon = " + MiZ);
        Log.info("Max Zoon = " + MaZ);
        Log.info("Load some triangle content.");
    }

    public triangle(){
        // 欢迎弹窗与设置页逻辑集中在 TriSetting
        TriSetting.init();
        // 战役星球界面“收件箱”按钮
        TriPlanetMailButton.init();
        // 主菜单 TMail 入口（固定模组资讯）
        TriMainMenuButton.init();
        // 设置→数据页“重置战役信件”按钮
        TriSettingsDataButton.init();
    }
}
