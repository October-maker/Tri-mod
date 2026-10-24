package triangle.content;

import arc.graphics.Color;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.blocks.heat.HeatProducer;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.HeatCrafter;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawFlame;
import mindustry.world.draw.DrawGlowRegion;
import mindustry.world.draw.DrawMulti;
import triangle.black.MRFactory.MultiRecipeFactory;
import triangle.black.MRFactory.Recipe;

import static mindustry.type.ItemStack.with;

/**
 * 三角模组的工厂方块注册表。
 *
 * <p>普通工厂只在此创建空实例，具体数值与配方由
 * {@code assets/content/blocks/factory/**} 下的同名 JSON 覆盖；
 * 多配方工厂（{@link MultiRecipeFactory}）的配方则在 {@link #load()} 内用 Java 声明。
 */
public class TriFactory {

    // ---- 常规单配方工厂（物品/液体加工） ----
    public static GenericCrafter
            electrolyticCell,oxygenReductionFurnace,IExtractor,IodinationPurificationFurnace,bauxiteElectrolyticCell,VBlastFurnace,VBlastFurnaceBlowing,VacuumAluminothermicGenerator,CoSulfationRoaster,WSteelMixingFurnace,WCarbideFurnace,
            FeBlastFurnace,TiRollingMill,SCombustionVessel,AcidReactionKettle,AcidResistantHeater,saltDistillationFurnace,SaltMixingPool,CuElectrolyticRefiningPool,NickelDiaphragmElectrolyzer,alkalineElectrolyzer,CopperConcentrator,CoElectrolyticCell,
            CrThermiteReactionChamber,armoredSinteringFurnace,Condenser;

    // ---- 热量相关工厂（产热 / 耗热） ----
    public static HeatProducer SAbsorptionTower,FeMixingPool;
    public static HeatCrafter GreenVitriolRoastingFurnace,electricArcFurnaceFluidizedBedReactor,SiReductionFurnace,sandRefiningPool;

    // ---- 氮化工线 ----
    public static GenericCrafter NCollector,NCatalyticChamber,NOxidationPlantGroup,NAbsorptionTower,NClMixedPool,ExplosiveMixer;

    // ---- 石油化工线 ----
    public static GenericCrafter oilElectricDesaltingTank,oilPrimaryAtmosphericDistillationTower,oilSecondaryAtmosphericDistillationTower,oilVacuumDistillationColumn,
            oilCatalyticCrackingTower,USYHydrothermalReactor,USYMixer,oilHydrocrackingTower,oilCatalyticReformingTower,oilAromaticHydrocarbonComplex,oilCokingHydrogenationTower,oilSteamCrackingTower,oilTubularReactor;

    // ---- 多配方工厂（配方在 Java 中声明） ----
    public static MultiRecipeFactory BulletFactory,liquidFillingMachine,liquidPourer,boiler;

    /** 创建全部工厂方块并声明多配方工厂的配方（由 {@code triangle.loadContent()} 调用）。 */
    public static void load(){
        oxygenReductionFurnace = new GenericCrafter("oxygenReductionFurnace"){{}};
        electrolyticCell = new GenericCrafter("electrolyticCell"){{}};
        IExtractor = new GenericCrafter("IExtractor"){{}};
        IodinationPurificationFurnace = new GenericCrafter("IodinationPurificationFurnace"){{}};
        bauxiteElectrolyticCell = new GenericCrafter("bauxiteElectrolyticCell"){{}};
        VBlastFurnace = new GenericCrafter("VBlastFurnace"){{}};
        VBlastFurnaceBlowing = new GenericCrafter("VBlastFurnaceBlowing"){{}};
        VacuumAluminothermicGenerator = new GenericCrafter("VacuumAluminothermicGenerator"){{}};
        FeBlastFurnace = new GenericCrafter("FeBlastFurnace"){{}};
        TiRollingMill = new GenericCrafter("TiRollingMill"){{}};
        SCombustionVessel = new GenericCrafter("SCombustionVessel"){{}};
        SAbsorptionTower = new HeatProducer("SAbsorptionTower"){{}};
        FeMixingPool = new HeatProducer("FeMixingPool"){{}};
        GreenVitriolRoastingFurnace = new HeatCrafter("GreenVitriolRoastingFurnace"){{}};
        AcidReactionKettle = new GenericCrafter("AcidReactionKettle"){{}};
        AcidResistantHeater = new GenericCrafter("AcidResistantHeater"){{}};
        electricArcFurnaceFluidizedBedReactor = new HeatCrafter("electricArcFurnaceFluidizedBedReactor"){{}};
        SiReductionFurnace = new HeatCrafter("SiReductionFurnace"){{}};

        // 氮化工线
        NCollector = new GenericCrafter("NCollector"){{}};
        NCatalyticChamber = new GenericCrafter("NCatalyticChamber"){{}};
        NOxidationPlantGroup = new GenericCrafter("NOxidationPlantGroup"){{}};
        NAbsorptionTower = new GenericCrafter("NAbsorptionTower"){{}};
        NClMixedPool = new GenericCrafter("NClMixedPool"){{}};
        ExplosiveMixer = new GenericCrafter("ExplosiveMixer"){{}};

        // 石油化工线
        oilElectricDesaltingTank = new GenericCrafter("oilElectricDesaltingTank"){{}};
        oilPrimaryAtmosphericDistillationTower = new GenericCrafter("oilPrimaryAtmosphericDistillationTower"){{}};
        oilSecondaryAtmosphericDistillationTower = new GenericCrafter("oilSecondaryAtmosphericDistillationTower"){{}};
        oilVacuumDistillationColumn = new GenericCrafter("oilVacuumDistillationColumn"){{}};
        oilCatalyticCrackingTower = new GenericCrafter("oilCatalyticCrackingTower"){{}};
        USYHydrothermalReactor = new GenericCrafter("USYHydrothermalReactor"){{}};
        USYMixer = new GenericCrafter("USYMixer"){{}};
        oilHydrocrackingTower = new GenericCrafter("oilHydrocrackingTower"){{}};
        oilCatalyticReformingTower = new GenericCrafter("oilCatalyticReformingTower"){{}};
        oilAromaticHydrocarbonComplex = new GenericCrafter("oilAromaticHydrocarbonComplex"){{}};
        oilCokingHydrogenationTower = new GenericCrafter("oilCokingHydrogenationTower"){{}};
        oilSteamCrackingTower = new GenericCrafter("oilSteamCrackingTower"){{}};
        oilTubularReactor = new GenericCrafter("oilTubularReactor"){{}};

        CuElectrolyticRefiningPool = new GenericCrafter("CuElectrolyticRefiningPool"){{}};
        SaltMixingPool = new GenericCrafter("SaltMixingPool"){{}};
        saltDistillationFurnace = new GenericCrafter("saltDistillationFurnace"){{}};
        sandRefiningPool = new HeatCrafter("sandRefiningPool"){{}};
        NickelDiaphragmElectrolyzer = new GenericCrafter("NickelDiaphragmElectrolyzer"){{}};
        alkalineElectrolyzer = new GenericCrafter("alkalineElectrolyzer"){{}};
        CoSulfationRoaster = new GenericCrafter("CoSulfationRoaster"){{}};
        CopperConcentrator = new GenericCrafter("CopperConcentrator"){{}};
        CoElectrolyticCell = new GenericCrafter("CoElectrolyticCell"){{}};
        WCarbideFurnace = new GenericCrafter("WCarbideFurnace"){{}};
        WSteelMixingFurnace = new GenericCrafter("WSteelMixingFurnace"){{}};
        CrThermiteReactionChamber = new GenericCrafter("CrThermiteReactionChamber"){{}};
        armoredSinteringFurnace = new GenericCrafter("armoredSinteringFurnace"){{}};
        Condenser = new GenericCrafter("Condenser"){{}};

        // ---- 多配方工厂 ----
        // 子弹工厂：四条弹药配方（LHEAT / thermite / WBullet / LHESH）
        BulletFactory = new MultiRecipeFactory("BulletFactory"){{
            size = 4;
            health = 20;
            itemCapacity = 20;
            liquidCapacity = 20;
            requirements(Category.crafting, with(TriItems.Cu,10,TriItems.TiAlloy,30,TriItems.FeSteel,20,TriItems.MonocrystallineSi,10));
            drawer = new DrawMulti(new DrawDefault(), new DrawFlame(Color.valueOf("E58760")));
            recipes.add(
                    // 说明：powerUse 单位为“每 tick（1/60 秒）”，每秒消耗 = powerUse × 60（如 20f/60f = 20/秒）
                    // 破甲弹 LHEAT：铜 + 钢 + TNT
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.Cu, 2));
                        inputItem.add(new ItemStack(TriItems.FeSteel,1));
                        inputItem.add(new ItemStack(TriItems.TNT,1));
                        outputItem.add(new ItemStack(TriItems.LHEAT, 4));
                        craftTime = 15f;
                        powerUse = 20f/60f;
                    }},
                    // 铝热剂：铝土矿 + 钢 + 氧化铁
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.bauxite, 1));
                        inputItem.add(new ItemStack(TriItems.FeSteel,1));
                        inputItem.add(new ItemStack(TriItems.Fe2O3,3));
                        outputItem.add(new ItemStack(TriItems.thermite, 6));
                        craftTime = 10f;
                        powerUse = 10f/60f;
                    }},
                    // 钨芯弹：钨钢 + 镍
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.WSteel,2));
                        inputItem.add(new ItemStack(TriItems.Ni,1));
                        outputItem.add(new ItemStack(TriItems.WBullet,3));
                        craftTime = 60f;
                        powerUse = 1f;
                    }},
                    // 碎甲弹 LHESH：聚乙烯 + 钢 + TNT（需研究）
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.PE,2));
                        inputItem.add(new ItemStack(TriItems.FeSteel,4));
                        inputItem.add(new ItemStack(TriItems.TNT,6));
                        outputItem.add(new ItemStack(TriItems.LHESH,4));
                        craftTime = 30f;
                        powerUse = 30f/60f;
                        requiresResearch = true;
                    }}
            );
        }};
        // 液体装桶机：水/卤水 + 玻璃 → 对应桶装物品
        liquidFillingMachine = new MultiRecipeFactory("liquidFillingMachine"){{
            size = 3;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            requirements(Category.crafting, with(TriItems.Cu,10,TriItems.TiAlloy,10,TriItems.FeSteel,20,Items.metaglass,10));
            drawer = new DrawMulti(
                    new DrawDefault(),
                    new DrawGlowRegion(){{
                        suffix = "-glow";
                        color = Color.valueOf("FFFFFF99");
                        layer = 110;
                    }}
            );
            recipes.add(
                    new Recipe() {{
                        inputItem.add(new ItemStack(Items.metaglass, 2));
                        inputLiquid.add(new LiquidStack(Liquids.water, 1));
                        outputItem.add(new ItemStack(TriItems.barrel, 5));
                        craftTime = 60f;
                        powerUse = 12f/60f;
                    }}, // water
                new Recipe() {{
                    inputItem.add(new ItemStack(Items.metaglass, 2));
                    inputLiquid.add(new LiquidStack(TriLiquids.salineWater, 1));
                    outputItem.add(new ItemStack(TriItems.saltwaterBucket, 5));
                    craftTime = 60f;
                    powerUse = 12f/60f;
                }}
            );
        }};
        // 液体倒桶机：桶装物品 → 对应液体，与装桶机互为逆过程
        liquidPourer = new MultiRecipeFactory("liquidPourer"){{
            size = 3;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            requirements(Category.crafting, with(TriItems.Cu,10,TriItems.TiAlloy,10,TriItems.FeSteel,15,Items.metaglass,15));
            drawer = new DrawMulti(
                    new DrawDefault(),
                    new DrawGlowRegion(){{
                        suffix = "-glow";
                        color = Color.valueOf("FFFFFF99");
                        layer = 110;
                    }}
            );
            recipes.add(
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.barrel, 5));
                        outputLiquid.add(new LiquidStack(Liquids.water, 1));
                        craftTime = 60f;
                        powerUse = 7f/60f;
                    }}, // water
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.saltwaterBucket, 5));
                        outputLiquid.add(new LiquidStack(TriLiquids.salineWater, 1));
                        craftTime = 60f;
                        powerUse = 7f/60f;
                    }}
            );
        }};
        // 锅炉：燃烧油品加热水生成蒸汽；最后一条为需要外部热量的纯水蒸发配方。
        // 热量需求 heatRequirement 单位与 HeatCrafter 一致，recipeMaxEfficiency 为超供热的效率上限。
        boiler = new MultiRecipeFactory("boiler"){{
            size = 5;
            health = 67;
            itemCapacity = 50;
            liquidCapacity = 320;
            requirements(Category.power , with(TriItems.TiAlloy,125,TriItems.FeSteel,105,TriItems.Cu,60,TriItems.MonocrystallineSi,30));
            drawer = new DrawMulti(
                    new DrawDefault(),
                    new DrawFlame()
            );
            recipes.add(
                    new Recipe() {{
                        inputLiquid.add(new LiquidStack(TriLiquids.oilGasoline,0.45f));
                        inputLiquid.add(new LiquidStack(Liquids.water,5.18f));
                        outputLiquid.add(new LiquidStack(TriLiquids.steam,5.18f));
                        craftTime = 60f;
                        powerUse = 105f/60f;
                    }},
                    new Recipe() {{
                        inputLiquid.add(new LiquidStack(TriLiquids.oilKerosene,0.36f));
                        inputLiquid.add(new LiquidStack(Liquids.water,4.15f));
                        outputLiquid.add(new LiquidStack(TriLiquids.steam,4.15f));
                        craftTime = 60f;
                        powerUse = 105f/60f;
                    }},
                    new Recipe() {{
                        inputLiquid.add(new LiquidStack(TriLiquids.oilDiesel,0.4f));
                        inputLiquid.add(new LiquidStack(Liquids.water,4.92f));
                        outputLiquid.add(new LiquidStack(TriLiquids.steam,4.92f));
                        craftTime = 60f;
                        powerUse = 105f/60f;
                    }},
                    new Recipe() {{
                        inputLiquid.add(new LiquidStack(TriLiquids.oilHeavy,0.5f));
                        inputLiquid.add(new LiquidStack(Liquids.water,5.38f));
                        outputLiquid.add(new LiquidStack(TriLiquids.steam,5.38f));
                        craftTime = 60f;
                        powerUse = 105f/60f;
                    }}
            );
            recipes.add(
                    new Recipe() {{
                        inputLiquid.add(new LiquidStack(Liquids.water,5.0f));
                        outputLiquid.add(new LiquidStack(TriLiquids.steam,5.0f));
                        craftTime = 60f;
                        powerUse = 105f/60f;
                        heatRequirement = 50f;
                        heatEnabled = true;
                        recipeMaxEfficiency = 2f;
                        //heatOutput = 3;
                    }}
//                    new Recipe() {{
//                        inputLiquid.add(new LiquidStack(Liquids.oil,5.0f));
//                        outputLiquid.add(new LiquidStack(TriLiquids.steam,5.0f));
//                        heatRequirement = 20f;
//                        heatEnabled = true;
//                        heatOutput = 5;
//                    }}
            );
        }};
        // ---- 已停用的旧方案（保留作配方数值参考，未注册进游戏） ----
        // 原“综合加工厂”草案：把碘化提纯、铝电解、钒渣冶炼等工序合并到一台 7×7 工厂中。
        // 因数值与工序耦合过强、维护困难而停用，后续若恢复可参考下方配方。
//        ComprehensiveProcessingFactory = new MultiRecipeFactory("ComprehensiveProcessingFactory"){{
//            size = 7;
//            health = 30;
//            itemCapacity = 850;
//            liquidCapacity = 500;
//            requirements(Category.crafting, with(TriItems.Cu,50,TriItems.TiAlloy,80,TriItems.FeSteel,30,TriItems.Ni,45,TriItems.chip4004,25));
//            recipes.add(
//                    new Recipe() {{
//                        inputItem.add(new ItemStack(Items.titanium, 15));
//                        outputItem.add(new ItemStack(TriItems.Ti, 13));
//                        craftTime = 60;
//                        powerUse = 320;
//                    }},//IodinationPurificationFurnace
//                    new Recipe() {{
//                        inputItem.add(new ItemStack(TriItems.bauxite, 20));
//                        outputItem.add(new ItemStack(TriItems.Al, 12));
//                        outputLiquid.add(new LiquidStack(Liquids.ozone, 0.8f));
//                        craftTime = 60;
//                        powerUse = 480;
//                    }},//bauxiteElectrolyticCell
//                    new Recipe() {{
//                        inputItem.add(new ItemStack(TriItems.VTiFe, 15));
//                        inputItem.add(new ItemStack(Items.coal, 8));
//                        outputLiquid.add(new LiquidStack(TriLiquids.VBearingHotMetal, 1.2f));
//                        outputItem.add(new ItemStack(Items.scrap, 1));
//                        craftTime = 60;
//                        powerUse = 60;
//                    }},//VBlastFurnace
//                    new Recipe() {{
//                        inputLiquid.add(new LiquidStack(TriLiquids.VBearingHotMetal, 1.2f));
//                        inputItem.add(new ItemStack(Items.coal, 3));
//                        outputItem.add(new ItemStack(TriItems.V2O5, 12));
//                        outputItem.add(new ItemStack(TriItems.FeSteel, 14));
//                        craftTime = 60;
//                        powerUse = 192;
//                    }}//VBlastFurnaceBlowing
//            );
//            recipes.add(
//                    new Recipe() {{
//                        inputItem.add(new ItemStack(TriItems.Al, 16));
//                        inputItem.add(new ItemStack(TriItems.V2O5, 12));
//                        outputItem.add(new ItemStack(TriItems.V, 8));
//                        outputItem.add(new ItemStack(TriItems.bauxite, 20));
//                        craftTime = 60;
//                        powerUse = 192;
//                    }},//VacuumAluminothermicGenerator
//                    new Recipe() {{
//                        inputItem.add(new ItemStack(TriItems.Ti, 10));
//                        inputItem.add(new ItemStack(TriItems.Al, 6));
//                        inputItem.add(new ItemStack(TriItems.V, 4));
//                        outputItem.add(new ItemStack(TriItems.TiAlloy, 18));
//                        craftTime = 60;
//                        powerUse = 105;
//                    }}//TiRollingMill
//            );
//        }};
    }
}
