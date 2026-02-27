package triangle.content;

import arc.graphics.Color;
import mindustry.type.Liquid;

public class TriLiquids {
    public static Liquid salineWater,H2O2,VBearingHotMetal,SO3,H2SO4,hydrochloricAcid,HCl,SiHCl3,NO2,liquidAmmonia,HNO3,aquaRegia,Cl;
    //oil
    public  static  Liquid oilCrude,oilHeavy,oilDiesel,oilMixedGas,oilGasoline,oilKerosene,oilPetroleumGas,oilVGO,oilVacuumResidue,oilNaphtha,oilReformate,oilArene,oilWax,oilEthylene;
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
        Cl = new Liquid("Cl",Color.valueOf("80A00090")){{}};
        //oil
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
