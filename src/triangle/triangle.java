package triangle;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;
import triangle.black.ArmorReactiveBulletType;
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
        Events.on(ClientLoadEvent.class, e -> {
            String remind = Core.bundle.get("remind");
            String open = Core.bundle.get("began");
            String stop = Core.bundle.get("end");
            String word = Core.bundle.get("login");
            String get = Core.bundle.get("Iknow");
            //读取bundle里面的字符串
            Time.runTask(1f, () -> {
                BaseDialog dialog = new BaseDialog("Triangle");
                dialog.cont.image(Core.atlas.find("logo")).pad(20f).row();
                dialog.cont.add(open +"\n" + word + "\n" + remind + "\n" + stop).growX().wrap().width(720).maxWidth(730).pad(4).row();
                dialog.cont.button(get, dialog::hide).size(100f, 50f);
                dialog.show();
            });
        });
    }
}
