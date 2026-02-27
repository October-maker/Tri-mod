package triangle;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;
import triangle.content.*;

public class triangle extends Mod{

    @Override
    public void loadContent(){
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
        //listen for game load event
        Events.on(ClientLoadEvent.class, e -> {
            //show dialog upon startup
            String remind = Core.bundle.get("remind");
            String open = Core.bundle.get("began");
            String stop = Core.bundle.get("end");
            String word = Core.bundle.get("login");
            String get = Core.bundle.get("ok");
            Time.runTask(1f, () -> {
                BaseDialog dialog = new BaseDialog("Triangle");
                //mod sprites are prefixed with the mod name (this mod is called 'example-java-mod' in its config)
                dialog.cont.image(Core.atlas.find("logo")).pad(20f).row();
                dialog.cont.add(open +"\n" + word + "\n" + remind + "\n" + stop).growX().wrap().width(720).maxWidth(730).pad(4).row();
                dialog.cont.button(get, dialog::hide).size(100f, 50f);
                dialog.show();
            });
        });
    }
}
