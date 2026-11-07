package triangle.black.TriMiniGame;

import arc.Core;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * 主菜单“预设模式”入口：在进入游戏前逐项选择词条，再点击开始游戏。
 */
public class TriPresetDialog extends BaseDialog{

    private static TriPresetDialog instance;

    public static void open(){
        if(instance == null) instance = new TriPresetDialog();
        instance.show();
    }

    private TriPresetDialog(){
        super(bundle("tri.preset.title"));

        cont.defaults().pad(8f);
        cont.add(bundle("tri.preset.desc"))
                .width(560f)
                .wrap()
                .center()
                .row();

        Table modifiers = new Table();
        for(int i = 0; i < TriPresetMode.modifierCount(); i++){
            final int index = i;
            TextButton button = new TextButton(TriPresetMode.modifierLabel(i), Styles.defaultt);
            button.clicked(() -> {
                TriPresetMode.cycleModifier(index);
                button.setText(TriPresetMode.modifierLabel(index));
            });
            modifiers.add(button).size(300f, 46f).pad(4f);
            if((i + 1) % 2 == 0) modifiers.row();
        }
        cont.add(modifiers).row();

        TextButton start = new TextButton(bundle("tri.preset.start"), Styles.defaultt);
        start.clicked(() -> {
            hide();
            TriPresetMode.start();
        });
        cont.add(start).size(300f, 54f).padTop(16f).row();

        addCloseButton();
    }

    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
