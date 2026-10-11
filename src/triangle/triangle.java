package triangle;

import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.mod.*;
import triangle.black.ArmorReactiveBulletType;
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

        TriItems.load();
        TriLiquids.load();
        TriFactory.load();
        TriCore.load();
        TriStatus.load();
        TriTurret.load();
        TriWeather.load();
        SerTriTechTree.load();
        Vars.renderer.minZoom =0.3f;
        Vars.renderer.maxZoom = 20.0f;
        float MiZ = Vars.renderer.minZoom;
        float MaZ = Vars.renderer.maxZoom;
        Log.info("Min Zoon = " + MiZ);
        Log.info("Max Zoon = " + MaZ);
        Log.info("Load some triangle content.");
    }

    public triangle(){
        // 启动欢迎弹窗与设置页等逻辑集中在 TriSetting，此处仅注册
        TriSetting.init();
    }
}
