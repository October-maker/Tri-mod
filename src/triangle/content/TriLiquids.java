package triangle.content;

import arc.graphics.Color;
import mindustry.type.Liquid;

/**
 * 三角模组的液体注册表。
 *
 * <p>液体实例在此创建；显示名与描述来自 {@code assets/bundles}，状态效果、爆炸性等参数由
 * {@code assets/content/liquids/*.json} 按内容名覆盖，贴图按内容名从 {@code assets/sprites/liquid/}
 * 自动加载。构造函数中的颜色支持 8 位 RRGGBBAA，末两位为透明度
 * （如 {@code 40D0EDFF} 为不透明，{@code FFFFFF40} 为半透明）。
 *
 * <p>常见缩写对照：H2O2=过氧化氢，H2SO4=硫酸，HCl=氯化氢，SiHCl3=三氯氢硅，
 * SO3=三氧化硫，NO2=二氧化氮，HNO3=硝酸，aquaRegia=王水，Cl=氯气，steam=蒸汽。
 */
public class TriLiquids {

    // ---- 基础化工液体 ----
    public static Liquid salineWater,H2O2,VBearingHotMetal,SO3,H2SO4,hydrochloricAcid,HCl,SiHCl3,NO2,liquidAmmonia,HNO3,aquaRegia,Cl,steam,weakSteam;

    // ---- 石油化工液体（原油及馏分） ----
    // oilCrude=原油，oilHeavy=重油，oilDiesel=柴油，oilGasoline=汽油，oilKerosene=煤油，
    // oilMixedGas=混合气，oilPetroleumGas=石油气，oilVGO=减压蜡油，oilVacuumResidue=减压渣油，
    // oilNaphtha=石脑油，oilReformate=重整油，oilArene=芳烃，oilWax=蜡，oilEthylene=乙烯。
    public static Liquid oilCrude,oilHeavy,oilDiesel,oilMixedGas,oilGasoline,oilKerosene,oilPetroleumGas,oilVGO,oilVacuumResidue,oilNaphtha,oilReformate,oilArene,oilWax,oilEthylene;

    /** 创建全部液体（由 {@code triangle.loadContent()} 调用）。 */
    public static void load(){
        salineWater = new Liquid("salineWater",Color.valueOf("97BFD0")){{}};
        H2O2 = new Liquid("H2O2",Color.valueOf("40D0EDFF")){{}};
        VBearingHotMetal = new Liquid("VBearingHotMetal",Color.valueOf("FF8000")){{}};
        SO3 = new Liquid("SO3",Color.valueOf("FFFF00")){{}};
        H2SO4 = new Liquid("H2SO4",Color.valueOf("FFFF00C8")){{}};
        hydrochloricAcid = new Liquid("hydrochloricAcid",Color.valueOf("A2FF00")){{}};
        HCl = new Liquid("HCl",Color.valueOf("A2FF0082")){{}};
        SiHCl3 = new Liquid("SiHCl3",Color.valueOf("FFFF80D2")){{}};
        liquidAmmonia = new Liquid("liquidAmmonia",Color.valueOf("C2B0FF90")){{}};
        NO2 = new Liquid("NO2",Color.valueOf("A03333E0")){{}};
        HNO3 = new Liquid("HNO3",Color.valueOf("F0F0A0")){{}};
        aquaRegia = new Liquid("aquaRegia",Color.valueOf("B7472BA0")){{}};
        Cl = new Liquid("Cl",Color.valueOf("D7FFC590")){{}};
        steam = new Liquid("steam",Color.valueOf("FFFFFFF0")){{}};
        weakSteam = new Liquid("weakSteam",Color.valueOf("FFFFFF40")){{}};

        // 石油化工
        oilCrude = new Liquid("oilCrude",Color.valueOf("000000A0")){{}};
        oilHeavy = new Liquid("oilHeavy",Color.valueOf("000000A0")){{}};
        oilDiesel = new Liquid("oilDiesel",Color.valueOf("D89A59A0")){{}};
        oilMixedGas = new Liquid("oilMixedGas",Color.valueOf("000000F0")){{}};
        oilGasoline = new Liquid("oilGasoline",Color.valueOf("D89A59A0")){{}};
        oilKerosene = new Liquid("oilKerosene",Color.valueOf("D89A59A0")){{}};
        oilPetroleumGas = new Liquid("oilPetroleumGas",Color.valueOf("000000F0")){{}};
        oilVGO = new Liquid("oilVGO",Color.valueOf("D89A59A0")){{}};
        oilVacuumResidue = new Liquid("oilVacuumResidue",Color.valueOf("000000A0")){{}};
        oilNaphtha = new Liquid("oilNaphtha",Color.valueOf("D8B693A0")){{}};
        oilReformate = new Liquid("oilReformate",Color.valueOf("FF9A59A0")){{}};
        oilArene = new Liquid("oilArene",Color.valueOf("D8B69370")){{}};
        oilWax = new Liquid("oilWax",Color.valueOf("D8B69350")){{}};
        oilEthylene = new Liquid("oilEthylene",Color.valueOf("000000F0")){{}};
    };
}
