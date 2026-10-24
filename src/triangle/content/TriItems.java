package triangle.content;

import arc.graphics.*;
import mindustry.type.Item;

/**
 * 三角模组的物品注册表。
 *
 * <p>物品实例在此创建；显示名与描述来自 {@code assets/bundles}，物性参数（硬度、爆炸性、
 * 研究前置等）由 {@code assets/content/items/*.json} 按内容名覆盖，贴图按内容名从
 * {@code assets/sprites/item/} 自动加载。构造函数中的颜色仅作为图标底色/占位。
 *
 * <p>命名沿用化学符号或工艺缩写，常见对照：
 * I=碘，Ti=钛（纯钛板），Al=铝，Cu=精炼铜，FeSteel=碳素钢，SSteel=特种钢，TiAlloy=钛合金，
 * WSteel=钨钢，WC=碳化钨，V=钒，V2O5=五氧化二钒，VTiFe=钒钛磁铁矿，Cr=铬，chromite=铬铁矿，
 * Ni=镍，NiOre=镍原矿，Co=钴，copperCo=铜钴矿，CoPrecipitation=钴沉淀，Pt=铂，
 * bauxite=铝土矿，kaolin=高岭土，Fe2O3=氧化铁，FeSO4=绿矾（七水硫酸亚铁），
 * NaHSO4=硫酸氢钠，NaCl=盐（氯化钠），NaOH=烧碱（氢氧化钠），S=硫磺，TNT=三硝基甲苯，
 * SiSand=石英砂，MonocrystallineSi=单晶硅棒，USY=超稳 Y 型分子筛，USYmolecularSieve=USY 分子筛，
 * PE=聚乙烯，chip4004=基本集成电路。
 */
public class TriItems {

    // ---- 矿物、中间产物与合金 ----
    public static Item I,Ti,chip4004,S,VTiFe,NaHSO4,bauxite,Al,Fe2O3,FeSteel,SSteel,FeSO4,V2O5,V,TiAlloy,WSteel,SiSand,MonocrystallineSi,Pt,kaolin,USYmolecularSieve,USY,TNT,NaCl,Cu,Co,Cr,copperCo,NaOH,Ni,NiOre,CoPrecipitation,WC,chromite,PE;

    // ---- 弹药与爆炸物（供炮塔及多配方工厂使用） ----
    // LHESH=小口径碎甲弹，LHEAT=小口径破甲弹，BHE=大口径高爆弹，BHEAT=大口径破甲弹，
    // BHESH=大口径碎甲弹，TGW=炮射导弹，EMPB=电磁脉冲弹，WBullet=轨道钨芯弹，thermite=铝热剂。
    public static Item LHESH,LHEAT,thermite,BHE,BHEAT,BHESH,TGW,EMPB,WBullet;

    // ---- 桶装液体（装桶/倒桶配方的中间物品） ----
    public static Item barrel,saltwaterBucket;

    /** 预留的物品节点，当前未使用（保留给后续科技树/数据库内容）。 */
    public static Item SerTri;

    /** 创建全部物品（由 {@code triangle.loadContent()} 调用）。 */
    public static void load(){
        I = new Item("I" , Color.valueOf("380066")){{}};
        Ti = new Item("Ti",Color.valueOf("0073FF")){{}};
        chip4004 = new Item("chip4004",Color.valueOf("2900FF")){{}};
        S = new Item("S",Color.valueOf("FFFF00")){{}};
        VTiFe = new Item("VTiFe",Color.valueOf("B37556")){{}};
        NaHSO4 = new Item("NaHSO4",Color.valueOf("FFFFFF")){{}};
        bauxite = new Item("bauxite",Color.valueOf("B23B00")){{}};
        Al = new Item("Al",Color.valueOf("DFDFDF")){{}};
        Fe2O3 = new Item("Fe2O3",Color.valueOf("B74624")){{}};
        FeSteel = new Item("FeSteel",Color.valueOf("A1805E")){{}};
        SSteel = new Item("SSteel",Color.valueOf("737373")){{}};
        FeSO4 = new Item("FeSO4",Color.valueOf("D0FF80")){{}};
        LHESH = new Item("LHESH",Color.valueOf("FF8000")){{}};
        LHEAT = new Item("LHEAT",Color.valueOf("FF8000")){{}};
        thermite = new Item("thermite",Color.valueOf("75787B")){{}};
        V2O5 = new Item("V2O5",Color.valueOf("FFAA00")){{}};
        BHE = new Item("BHE",Color.valueOf("FF8000")){{}};
        BHEAT = new Item("BHEAT",Color.valueOf("FF8000")){{}};
        BHESH = new Item("BHESH",Color.valueOf("FF8000")){{}};
        TGW = new Item("TGW",Color.valueOf("A0AAB8")){{}};
        EMPB = new Item("EMPB",Color.valueOf("00FBFF")){{}};
        V = new Item("V",Color.valueOf("9F9F9F")){{}};
        TiAlloy = new Item("TiAlloy",Color.valueOf("9F9F9F")){{}};
        WSteel = new Item("WSteel",Color.valueOf("9F9F9F")){{}};
        WBullet = new Item("WBullet",Color.valueOf("9F9F9F")){{}};
        SiSand = new Item("SiSand",Color.valueOf("DFDFDF")){{}};
        MonocrystallineSi = new Item("MonocrystallineSi",Color.valueOf("808080")){{}};
        Pt = new Item("Pt",Color.valueOf("E0E0E0")){{}};
        kaolin = new Item("kaolin",Color.valueOf("F0F0F0")){{}};
        USYmolecularSieve = new Item("USYmolecularSieve",Color.valueOf("E0C0A0")){{}};
        USY = new Item("USY",Color.valueOf("F0F0F0")){{}};
        TNT = new Item("TNT",Color.valueOf("FF0000")){{}};
        NaCl = new Item("NaCl",Color.valueOf("FFFFFF")){{}};
        Cu = new Item("Cu",Color.valueOf("FF8E38")){{}};
        Co = new Item("Co",Color.valueOf("29b2ff")){{}};
        Cr = new Item("Cr",Color.valueOf("A0A0A0")){{}};
        copperCo = new Item("copperCo",Color.valueOf("F0F0F0")){{}};
        NaOH = new Item("NaOH",Color.valueOf("F0F0F0")){{}};
        Ni = new Item("Ni",Color.valueOf("A0A0A0")){{}};
        NiOre = new Item("NiOre",Color.valueOf("F0C0A0")){{}};
        CoPrecipitation = new Item("CoPrecipitation",Color.valueOf("9F9F9F")){{}};
        WC = new Item("WC",Color.valueOf("9F9F9F")){{}};
        chromite = new Item("chromite",Color.valueOf("707070")){{}};
        PE = new Item("PE",Color.valueOf("E0E0E0")){{}};

        // 桶装液体
        barrel = new Item("barrel",Color.valueOf("FFFFFF")){{}};
        saltwaterBucket = new Item("saltwaterBucket",Color.valueOf("FFFFFF")){{}};
    };
}
