package triangle.content;

import arc.Core;
import arc.graphics.Color;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.Fx;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.blocks.heat.HeatProducer;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.HeatCrafter;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawBlock;
import mindustry.world.draw.DrawFlame;
import mindustry.world.draw.DrawGlowRegion;
import mindustry.world.draw.DrawLiquidTile;
import mindustry.world.draw.DrawMulti;
import mindustry.world.draw.DrawRegion;
import triangle.black.MRFactory.MultiRecipeFactory;
import triangle.black.MRFactory.Recipe;
import triangle.black.MRFactory.StackedMultiRecipeFactory;

import static mindustry.type.ItemStack.with;

/**
 * 三角模组的工厂方块注册表。
 *
 * <p>普通工厂只在此创建空实例，具体数值与配方由
 * {@code assets/content/blocks/factory/**} 下的同名 JSON 覆盖；
 * 多配方工厂（{@link MultiRecipeFactory}）的配方则在 {@link #load()} 内用 Java 声明。
 * 石油化工线同样在 {@link #load()} 内以 {@link MultiRecipeFactory} 定义，不再使用 JSON。
 */
public class TriFactory {

    // ---- 常规单配方工厂（物品/液体加工） ----
    public static GenericCrafter
            electrolyticCell,oxygenReductionFurnace,IExtractor,IodinationPurificationFurnace,bauxiteElectrolyticCell,VBlastFurnace,VBlastFurnaceBlowing,VacuumAluminothermicGenerator,CoSulfationRoaster,
            FeBlastFurnace,TiRollingMill,SCombustionVessel,AcidReactionKettle,AcidResistantHeater,saltDistillationFurnace,SaltMixingPool,CuElectrolyticRefiningPool,NickelDiaphragmElectrolyzer,alkalineElectrolyzer,CopperConcentrator,CoElectrolyticCell,
            CrThermiteReactionChamber,armoredSinteringFurnace,Condenser;

    /** WC / 原版碳化物双配方碳化钨炉。 */
    public static MultiRecipeFactory WCarbideFurnace;
    /** WC / 原版碳化物双配方钨钢混合炉。 */
    public static MultiRecipeFactory WSteelMixingFurnace;

    // ---- 热量相关工厂（产热 / 耗热） ----
    public static HeatProducer SAbsorptionTower,FeMixingPool;
    public static HeatCrafter GreenVitriolRoastingFurnace,electricArcFurnaceFluidizedBedReactor,SiReductionFurnace,sandRefiningPool;

    // ---- 氮化工线 ----
    public static GenericCrafter NCollector,NCatalyticChamber,NOxidationPlantGroup,NAbsorptionTower,NClMixedPool,ExplosiveMixer;

    // ---- 石油化工线 ----
    public static GenericCrafter oilElectricDesaltingTank;
    public static MultiRecipeFactory oilPrimaryAtmosphericDistillationTower,oilSecondaryAtmosphericDistillationTower,oilVacuumDistillationColumn,
            oilCatalyticCrackingTower,oilHydrocrackingTower,oilCatalyticReformingTower,oilAromaticHydrocarbonComplex,oilCokingHydrogenationTower,oilSteamCrackingTower,oilTubularReactor;
    public static GenericCrafter USYHydrothermalReactor,USYMixer;

    // ---- 多配方工厂（配方在 Java 中声明） ----
    public static MultiRecipeFactory BulletFactory,liquidFillingMachine,liquidPourer;
    public static MultiRecipeFactory boiler;

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

        CuElectrolyticRefiningPool = new GenericCrafter("CuElectrolyticRefiningPool"){{}};
        SaltMixingPool = new GenericCrafter("SaltMixingPool"){{}};
        saltDistillationFurnace = new GenericCrafter("saltDistillationFurnace"){{}};
        sandRefiningPool = new HeatCrafter("sandRefiningPool"){{}};
        NickelDiaphragmElectrolyzer = new GenericCrafter("NickelDiaphragmElectrolyzer"){{}};
        alkalineElectrolyzer = new GenericCrafter("alkalineElectrolyzer"){{}};
        CoSulfationRoaster = new GenericCrafter("CoSulfationRoaster"){{}};
        CopperConcentrator = new GenericCrafter("CopperConcentrator"){{}};
        CoElectrolyticCell = new GenericCrafter("CoElectrolyticCell"){{}};
        CrThermiteReactionChamber = new GenericCrafter("CrThermiteReactionChamber"){{}};
        armoredSinteringFurnace = new GenericCrafter("armoredSinteringFurnace"){{}};
        Condenser = new GenericCrafter("Condenser"){{}};

        // ---- 多配方工厂 ----
        WCarbideFurnace = new MultiRecipeFactory("WCarbideFurnace"){{
            size = 3;
            health = 120;
            itemCapacity = 40;
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            drawer = new DrawMulti(
                    new DrawDefault(),
                    glow("FFFFFF99"),
                    new DrawFlame()
            );
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Cu, 15,
                    Items.graphite, 35,
                    TriItems.FeSteel, 15,
                    TriItems.MonocrystallineSi, 18
            ));
            recipes.add(new Recipe(){{
                inputItem.add(new ItemStack(Items.tungsten, 6));
                inputItem.add(new ItemStack(Items.coal, 3));
                outputItem.add(new ItemStack(TriItems.WC, 10));
                craftTime = 60f;
                powerUse = 4f;
            }});
            // 使用原版碳化物作为中间产物，避免与模组 WC 的命名/定义争议。
            recipes.add(new Recipe(){{
                inputItem.add(new ItemStack(Items.tungsten, 6));
                inputItem.add(new ItemStack(Items.coal, 3));
                outputItem.add(new ItemStack(Items.carbide, 5));
                craftTime = 60f;
                powerUse = 4f;
            }});
        }};
        WSteelMixingFurnace = new MultiRecipeFactory("WSteelMixingFurnace"){{
            size = 5;
            health = 120;
            itemCapacity = 40;
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            drawer = new DrawMulti(
                    new DrawDefault(),
                    glow("FFFFFF99"),
                    new DrawFlame()
            );
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Cu, 20,
                    TriItems.TiAlloy, 45,
                    TriItems.FeSteel, 25,
                    TriItems.MonocrystallineSi, 18
            ));
            recipes.add(new Recipe(){{
                inputItem.add(new ItemStack(TriItems.WC, 10));
                inputItem.add(new ItemStack(TriItems.Co, 4));
                outputItem.add(new ItemStack(TriItems.WSteel, 10));
                craftTime = 60f;
                powerUse = 4f;
            }});
            // 使用原版碳化物作为中间产物，避免与模组 WC 的命名/定义争议。
            recipes.add(new Recipe(){{
                inputItem.add(new ItemStack(Items.carbide, 5));
                inputItem.add(new ItemStack(TriItems.Co, 4));
                outputItem.add(new ItemStack(TriItems.WSteel, 10));
                craftTime = 60f;
                powerUse = 4f;
            }});
        }};
        // 弹药加工厂
        BulletFactory = new MultiRecipeFactory("BulletFactory"){{
            size = 4;
            health = 20;
            itemCapacity = 20;
            liquidCapacity = 20;
            stackLimit = 3;
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
                    // 钨芯弹：钨钢 + 镍（需研究）
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.WSteel,2));
                        inputItem.add(new ItemStack(TriItems.Ni,1));
                        outputItem.add(new ItemStack(TriItems.WBullet,3));
                        craftTime = 60f;
                        powerUse = 1f;
                        requiresResearch = true;
                        unlockCondition = Recipe.tech(TriItems.WSteel);
                        unlockConditionText = Core.bundle.get("tri.recipe.unlock.manufactureWSteel");
                    }},
                    // 碎甲弹 LHESH：聚乙烯 + 钢 + TNT
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.PE,2));
                        inputItem.add(new ItemStack(TriItems.FeSteel,4));
                        inputItem.add(new ItemStack(TriItems.TNT,6));
                        outputItem.add(new ItemStack(TriItems.LHESH,4));
                        craftTime = 30f;
                        powerUse = 30f/60f;
                    }}
            );
            recipes.add(
                    // 碎甲弹 BHE：钢 + TNT（需研究）
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.FeSteel,5));
                        inputItem.add(new ItemStack(TriItems.TNT,12));
                        outputItem.add(new ItemStack(TriItems.BHE,1));
                        craftTime = 60f;
                        powerUse = 120f/60f;
                        requiresResearch = true;
                    }},
                    // 破甲弹 BHEAT：铜 + 钢 + TNT
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.Cu, 5));
                        inputItem.add(new ItemStack(TriItems.FeSteel,4));
                        inputItem.add(new ItemStack(TriItems.TNT,7));
                        outputItem.add(new ItemStack(TriItems.BHEAT, 1));
                        craftTime = 90f;
                        powerUse = 220f/60f;
                        requiresResearch = true;
                    }},
                    // 碎甲弹 LHESH：聚乙烯 + 钢 + TNT
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.PE,4));
                        inputItem.add(new ItemStack(TriItems.FeSteel,4));
                        inputItem.add(new ItemStack(TriItems.TNT,6));
                        outputItem.add(new ItemStack(TriItems.BHESH,1));
                        craftTime = 90f;
                        powerUse = 180f/60f;
                        requiresResearch = true;
                    }}
            );
        }};
        // 液体装罐器：水/卤水 + 玻璃 → 对应桶装物品
        liquidFillingMachine = new MultiRecipeFactory("liquidFillingMachine"){{
            size = 3;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            stackLimit = 5;
            verticalStackPanels = true;
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
        // 液体排倒器：桶装物品 → 对应液体，与装罐器互为逆过程
        liquidPourer = new MultiRecipeFactory("liquidPourer"){{
            size = 3;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            stackLimit = 5;
            verticalStackPanels = true;
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
        // ---- 可堆叠变体：基础工厂仍只有一层，覆盖放置时替换为 2..stackLimit 层变体 ----
        BulletFactory.stackable = true;
        BulletFactory.stackUpgrade(3, with(
                TriItems.Al, 120,
                TriItems.TiAlloy, 150
        ));
        liquidFillingMachine.stackable = true;
        liquidFillingMachine.stackUpgrade(5, with(
                TriItems.Al, 90,
                TriItems.TiAlloy, 80
        ));
        liquidPourer.stackable = true;
        liquidPourer.stackUpgrade(5, with(
                TriItems.Al, 90,
                TriItems.TiAlloy, 80
        ));
        StackedMultiRecipeFactory.registerVariants(BulletFactory);
        StackedMultiRecipeFactory.registerVariants(liquidFillingMachine);
        StackedMultiRecipeFactory.registerVariants(liquidPourer);

        // 石油化工线（Java 定义的 MultiRecipeFactory）
        oilElectricDesaltingTank = new GenericCrafter("oilElectricDesaltingTank"){{
            size = 2;
            health = 150;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = false;
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = new DrawMulti(
                    new DrawRegion(){{ suffix = "-bottom"; }},
                    new DrawLiquidTile(Liquids.oil){{ padding = 1f; }},
                    new DrawDefault(),
                    glow("FFFFFF20")
            );
            requirements(Category.crafting, with(
                    TriItems.Al, 15,
                    TriItems.MonocrystallineSi, 5,
                    TriItems.FeSteel, 20,
                    TriItems.TiAlloy, 15
            ));
            // 原 JSON 的 GenericCrafter 配方；效率提升为 4 倍（craftTime 30 → 7.5）。
            craftTime = 30f;
            outputItem = new ItemStack(Items.scrap, 1);
            outputLiquid = new LiquidStack(TriLiquids.oilCrude, 2f);
            consumePower(4f);
            consumeLiquid(Liquids.oil, 2.8f);
            consumeLiquid(Liquids.water, 1.6f);
            updateEffect = Fx.none;
            databaseTag = "Oil";
        }};

        oilPrimaryAtmosphericDistillationTower = new MultiRecipeFactory("oilPrimaryAtmosphericDistillationTower"){{
            size = 5;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = true;
            liquidOutputDirections = new int[]{1, 2, 3};
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = baseGlow("FFFFFF99");
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 20,
                    TriItems.MonocrystallineSi, 15,
                    TriItems.FeSteel, 50,
                    TriItems.TiAlloy, 15
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilCrude, 1f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilDiesel, 0.2f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilMixedGas, 0.3f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilHeavy, 0.5f));
                craftTime = 30f;
                powerUse = 15f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilCrude, 1f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilDiesel, 0.2f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilMixedGas, 0.3f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilHeavy, 0.5f));
                craftTime = 30f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 10f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        oilSecondaryAtmosphericDistillationTower = new MultiRecipeFactory("oilSecondaryAtmosphericDistillationTower"){{
            size = 3;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = true;
            liquidOutputDirections = new int[]{1, 2, 3};
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = baseGlow("FFFFFF99");
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 25,
                    TriItems.MonocrystallineSi, 15,
                    TriItems.FeSteel, 50,
                    TriItems.TiAlloy, 15
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilMixedGas, 0.3f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilKerosene, 0.12f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilPetroleumGas, 0.03f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilGasoline, 0.15f));
                craftTime = 30f;
                powerUse = 12f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilMixedGas, 0.3f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilKerosene, 0.12f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilPetroleumGas, 0.03f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilGasoline, 0.15f));
                craftTime = 30f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 10f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        oilVacuumDistillationColumn = new MultiRecipeFactory("oilVacuumDistillationColumn"){{
            size = 5;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = true;
            liquidOutputDirections = new int[]{1, 3};
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = baseGlow("FFFFFF99");
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 35,
                    TriItems.MonocrystallineSi, 35,
                    TriItems.FeSteel, 50,
                    TriItems.TiAlloy, 15
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilHeavy, 0.5f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilVGO, 0.2f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilVacuumResidue, 0.3f));
                craftTime = 30f;
                powerUse = 12f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilHeavy, 0.5f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilVGO, 0.2f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilVacuumResidue, 0.3f));
                craftTime = 30f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 10f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        oilCatalyticCrackingTower = new MultiRecipeFactory("oilCatalyticCrackingTower"){{
            size = 3;
            health = 250;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = true;
            liquidOutputDirections = new int[]{1, 2, 3};
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = baseGlow("FFFFFF99");
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 10,
                    TriItems.MonocrystallineSi, 5,
                    TriItems.FeSteel, 10,
                    TriItems.TiAlloy, 15,
                    TriItems.USY, 5
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilVGO, 0.2f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilDiesel, 0.06f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilPetroleumGas, 0.03f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilGasoline, 0.1f));
                craftTime = 30f;
                powerUse = 7.2f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilVGO, 0.2f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilDiesel, 0.06f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilPetroleumGas, 0.03f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilGasoline, 0.1f));
                craftTime = 30f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 5f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        oilHydrocrackingTower = new MultiRecipeFactory("oilHydrocrackingTower"){{
            size = 3;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = true;
            liquidOutputDirections = new int[]{1, 2, 3};
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = baseGlow("FFFFFF99");
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 15,
                    TriItems.MonocrystallineSi, 15,
                    TriItems.FeSteel, 50,
                    TriItems.TiAlloy, 25,
                    TriItems.USY, 3
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilVGO, 0.2f));
                inputLiquid.add(new LiquidStack(Liquids.hydrogen, 0.2f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilDiesel, 0.07f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilNaphtha, 0.16f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilKerosene, 0.07f));
                craftTime = 30f;
                powerUse = 9.8f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilVGO, 0.2f));
                inputLiquid.add(new LiquidStack(Liquids.hydrogen, 0.2f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilDiesel, 0.07f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilNaphtha, 0.16f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilKerosene, 0.07f));
                craftTime = 30f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 7f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        oilCatalyticReformingTower = new MultiRecipeFactory("oilCatalyticReformingTower"){{
            size = 3;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = true;
            liquidOutputDirections = new int[]{1, 3};
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = baseGlow("FFFFFF99");
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 15,
                    TriItems.MonocrystallineSi, 15,
                    TriItems.FeSteel, 50,
                    TriItems.TiAlloy, 15,
                    TriItems.Pt, 5
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilNaphtha, 0.16f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilReformate, 0.15f));
                outputLiquid.add(new LiquidStack(Liquids.hydrogen, 0.07f));
                craftTime = 30f;
                powerUse = 7.2f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilNaphtha, 0.16f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilReformate, 0.15f));
                outputLiquid.add(new LiquidStack(Liquids.hydrogen, 0.07f));
                craftTime = 30f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 5f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        oilAromaticHydrocarbonComplex = new MultiRecipeFactory("oilAromaticHydrocarbonComplex"){{
            size = 5;
            health = 30;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = false;
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = baseGlow("FFFFFF99");
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 25,
                    TriItems.MonocrystallineSi, 10,
                    TriItems.FeSteel, 50,
                    TriItems.TiAlloy, 20
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilReformate, 0.15f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilArene, 0.15f));
                craftTime = 30f;
                powerUse = 6f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilReformate, 0.15f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilArene, 0.15f));
                craftTime = 30f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 3f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        oilCokingHydrogenationTower = new MultiRecipeFactory("oilCokingHydrogenationTower"){{
            size = 5;
            health = 20;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = true;
            liquidOutputDirections = new int[]{1, 2, 3};
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = baseGlow("FFFFFF99");
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 20,
                    TriItems.MonocrystallineSi, 28,
                    TriItems.FeSteel, 40,
                    TriItems.TiAlloy, 15
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilVacuumResidue, 0.3f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilGasoline, 0.1f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilWax, 0.1f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilDiesel, 0.1f));
                outputItem.add(new ItemStack(mindustry.content.Items.scrap, 1));
                craftTime = 30f;
                powerUse = 9f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilVacuumResidue, 0.3f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilGasoline, 0.1f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilWax, 0.1f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilDiesel, 0.1f));
                outputItem.add(new ItemStack(mindustry.content.Items.scrap, 1));
                craftTime = 30f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 7f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        oilSteamCrackingTower = new MultiRecipeFactory("oilSteamCrackingTower"){{
            size = 3;
            health = 20;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = true;
            liquidOutputDirections = new int[]{1, 3};
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = baseGlow("FFFFFF99");
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 10,
                    TriItems.MonocrystallineSi, 18,
                    TriItems.TiAlloy, 25,
                    TriItems.FeSteel, 30
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(Liquids.water, 0.1f));
                inputLiquid.add(new LiquidStack(TriLiquids.oilWax, 0.1f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilEthylene, 0.14f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilGasoline, 0.06f));
                craftTime = 30f;
                powerUse = 8f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(Liquids.water, 0.1f));
                inputLiquid.add(new LiquidStack(TriLiquids.oilWax, 0.1f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilEthylene, 0.14f));
                outputLiquid.add(new LiquidStack(TriLiquids.oilGasoline, 0.06f));
                craftTime = 30f;
                powerUse = 5f;
                heatEnabled = true;
                heatRequirement = 20f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        oilTubularReactor = new MultiRecipeFactory("oilTubularReactor"){{
            size = 3;
            health = 105;
            itemCapacity = 40;
            liquidCapacity = 80;
            squareSprite = true;
            rotate = false;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 1.2f;
            hasLiquids = true;
            drawer = new DrawMulti(new DrawDefault(), glow("FF707090"));
            updateEffect = Fx.pulverizeMedium;
            requirements(Category.crafting, with(
                    TriItems.TiAlloy, 45,
                    TriItems.Cu, 10,
                    TriItems.MonocrystallineSi, 5,
                    TriItems.FeSteel, 20
            ));
            recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilEthylene, 0.14f));
                outputItem.add(new ItemStack(TriItems.PE, 14));
                craftTime = 60f;
                powerUse = 7.5f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilEthylene, 0.14f));
                outputItem.add(new ItemStack(TriItems.PE, 14));
                craftTime = 60f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 5f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
            }});
        }};

        USYHydrothermalReactor = new GenericCrafter("USYHydrothermalReactor"){{
            size = 5;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
            rotate = false;
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = new DrawMulti(new DrawDefault(), glow("FFFFFF99"));
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 20,
                    TriItems.MonocrystallineSi, 15,
                    TriItems.FeSteel, 45,
                    TriItems.TiAlloy, 15
            ));
            craftTime = 60f;
            consumePower(7.5f);
            consumeItems(
                    new ItemStack(TriItems.bauxite, 1),
                    new ItemStack(TriItems.SiSand, 4),
                    new ItemStack(TriItems.NaOH, 1)
            );
            consumeLiquid(TriLiquids.liquidAmmonia, 0.1f);
            consumeLiquid(Liquids.water, 0.4f);
            outputItem = new ItemStack(TriItems.USYmolecularSieve, 5);
            databaseTag = "Oil";
        }};

        USYMixer = new GenericCrafter("USYMixer"){{
            size = 3;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            rotate = false;
            squareSprite = true;
            researchCostMultiplier = 0.75f;
            buildCostMultiplier = 0.8f;
            hasLiquids = true;
            drawer = new DrawMulti(new DrawDefault(), glow("FFFFFF99"));
            updateEffect = Fx.none;
            requirements(Category.crafting, with(
                    TriItems.Al, 20,
                    TriItems.MonocrystallineSi, 5,
                    TriItems.FeSteel, 35,
                    TriItems.TiAlloy, 15
            ));
            craftTime = 60f;
            consumePower(3.2f);
            consumeItems(
                    new ItemStack(TriItems.USYmolecularSieve, 1),
                    new ItemStack(TriItems.kaolin, 5)
            );
            outputItem = new ItemStack(TriItems.USY, 5);
            databaseTag = "Oil";
        }};

        // 高热配方已在各工厂中直接通过 recipes.add 声明。
        MultiRecipeFactory[] oilFactories = {
            oilPrimaryAtmosphericDistillationTower,
            oilSecondaryAtmosphericDistillationTower,
            oilVacuumDistillationColumn,
            oilCatalyticCrackingTower,
            oilHydrocrackingTower,
            oilCatalyticReformingTower,
            oilAromaticHydrocarbonComplex,
            oilCokingHydrogenationTower,
            oilSteamCrackingTower,
            oilTubularReactor
        };

        // 所有石油化工配方欠压即跳闸。
        for(MultiRecipeFactory factory : oilFactories){
            factory.recipes.each(recipe -> recipe.stopUndervoltage = true);
        }

        // 保留原 JSON 的数据库分类标签。
        oilElectricDesaltingTank.databaseTag = "Oil";
        oilPrimaryAtmosphericDistillationTower.databaseTag = "Oil";
        oilSecondaryAtmosphericDistillationTower.databaseTag = "Oil";
        oilVacuumDistillationColumn.databaseTag = "Oil";
        oilCatalyticCrackingTower.databaseTag = "Oil";
        oilHydrocrackingTower.databaseTag = "Oil";
        oilCatalyticReformingTower.databaseTag = "Oil";
        oilAromaticHydrocarbonComplex.databaseTag = "Oil";
        oilCokingHydrogenationTower.databaseTag = "Oil";
        oilSteamCrackingTower.databaseTag = "Oil";
        oilTubularReactor.databaseTag = "Oil";
        USYHydrothermalReactor.databaseTag = "Oil";
        USYMixer.databaseTag = "Oil";
    }
    private static DrawBlock glow(String hex){
        return new DrawGlowRegion(){{
            color = Color.valueOf(hex);
            layer = 60f;
            suffix = "-glow";
            glowIntensity = 0.3f;
        }};
    }

    private static DrawBlock baseGlow(String color){
        return new DrawMulti(
            new DrawRegion(){{ suffix = "-base"; }},
            glow(color)
        );
    }
}
