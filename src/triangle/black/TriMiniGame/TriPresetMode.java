package triangle.black.TriMiniGame;

import arc.Core;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.game.Rules;
import mindustry.maps.Map;

/**
 * “预设模式”的启动入口。
 *
 * <p>进入游戏前逐项选择词条式 Rules 修改；进入游戏后不再动态切换。
 * 地图文件位于 {@code assets/maps/battleplan-planetaryTerminal.msav}。
 */
public final class TriPresetMode{

    public static final String MAP_NAME = "battleplan-planetaryTerminal";
    public static final String MAP_TITLE = "Planetary Launch Terminal";

    /** 一个可独立切换的词条。level 从 0 开始循环。 */
    private static final class Modifier{
        final String nameKey;
        final int maxLevel;
        int level;

        Modifier(String nameKey, int maxLevel){
            this.nameKey = nameKey;
            this.maxLevel = maxLevel;
        }
    }

    /**
     * 每个按钮只控制自己对应的 Rules 项：
     * - 出怪倍率：waveSpacing 除以倍率；
     * - 战争迷雾：fog/staticFog；
     * - 敌人强化：unitHealthMultiplier/unitDamageMultiplier；
     * - 快速建造：buildSpeedMultiplier/buildCostMultiplier。
     */
    private static final Seq<Modifier> modifiers = Seq.with(
            new Modifier("tri.preset.mod.waveRate", 2),
            new Modifier("tri.preset.mod.fog", 1),
            new Modifier("tri.preset.mod.enemy", 1),
            new Modifier("tri.preset.mod.build", 1)
    );

    private TriPresetMode(){}

    /** 兼容旧入口；Rules 不再在游戏内轮换。 */
    public static void init(){
    }

    public static int modifierCount(){
        return modifiers.size;
    }

    public static String modifierLabel(int index){
        Modifier modifier = modifiers.get(index);
        String value;
        if(index == 0){
            value = switch(modifier.level){
                case 1 -> "1.5x";
                case 2 -> "2x";
                default -> "1x";
            };
        }else{
            value = bundle(modifier.level > 0 ? "tri.preset.value.on" : "tri.preset.value.off");
        }
        return bundle(modifier.nameKey) + ": " + value;
    }

    public static void cycleModifier(int index){
        Modifier modifier = modifiers.get(index);
        modifier.level = (modifier.level + 1) % (modifier.maxLevel + 1);
    }

    /** 以当前词条状态新建一局自定义游戏。 */
    public static void start(){
        Map map = Vars.maps == null ? null : Vars.maps.moddedMaps()
                .find(m -> MAP_NAME.equals(m.file.nameWithoutExtension())
                        || MAP_TITLE.equals(m.name()));

        if(map == null){
            Vars.ui.showInfo(bundle("tri.preset.missingMap"));
            return;
        }

        Rules rules = map.applyRules(Gamemode.survival).copy();
        applyModifiers(rules);
        Vars.control.playMap(map, rules, false);
    }

    private static void applyModifiers(Rules rules){
        // 出怪倍率：1x / 1.5x / 2x。保持 initialWaveSpacing 不变，只影响后续波次。
        int waveLevel = modifiers.get(0).level;
        if(waveLevel > 0){
            rules.waveSpacing /= (1f + waveLevel * 0.5f);
        }

        // 战争迷雾。
        if(modifiers.get(1).level > 0){
            rules.fog = true;
            rules.staticFog = true;
        }

        // 敌人强化。
        if(modifiers.get(2).level > 0){
            rules.unitHealthMultiplier *= 1.25f;
            rules.unitDamageMultiplier *= 1.15f;
        }

        // 快速建造。
        if(modifiers.get(3).level > 0){
            rules.buildSpeedMultiplier *= 1.5f;
            rules.buildCostMultiplier *= 0.8f;
        }
    }

    private static String bundle(String key){
        return Core.bundle.get(key, key);
    }
}
