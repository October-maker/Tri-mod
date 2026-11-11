package triangle.content;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawLiquidTile;
import mindustry.world.draw.DrawMulti;
import mindustry.world.draw.DrawRegion;
import triangle.black.MRFactory.MultiRecipeFactory;
import triangle.black.MRFactory.Recipe;

import static mindustry.type.ItemStack.with;
import static triangle.content.TriFactory.baseGlow;
import static triangle.content.TriFactory.glow;

/**
 * 石油化工线工厂注册表。
 *
 * <p>从 {@link TriFactory} 拆出的独立内容类，集中声明原油电脱盐、常减压、
 * 催化裂化、加氢裂化、重整、芳烃联合、焦化加氢、蒸汽裂解、管式反应器
 * 以及 USY 分子筛相关工厂。由 {@link TriFactory#load()} 调用 {@link #load()}。
 */
public class TriOilFactory{

    public static GenericCrafter oilElectricDesaltingTank,USYHydrothermalReactor,USYMixer;
    public static MultiRecipeFactory oilPrimaryAtmosphericDistillationTower,oilSecondaryAtmosphericDistillationTower,oilVacuumDistillationColumn,
            oilCatalyticCrackingTower,oilHydrocrackingTower,oilCatalyticReformingTower,oilAromaticHydrocarbonComplex,oilCokingHydrogenationTower,oilSteamCrackingTower,oilTubularReactor;

    /** 创建石油化工线全部工厂并补充分类/欠压配置。 */
    public static void load(){
        // 石油化工线（Java 定义的 MultiRecipeFactory）
        oilElectricDesaltingTank = new GenericCrafter("oilElectricDesaltingTank"){{
            size = 2;
            health = 150;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
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
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 5f;
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
                outputItem.add(new ItemStack(TriItems.PE, 4));
                craftTime = 60f;
                powerUse = 7.5f;
            }});

        // 高热配方
        recipes.add(new Recipe(){{
                inputLiquid.add(new LiquidStack(TriLiquids.oilEthylene, 0.14f));
                outputItem.add(new ItemStack(TriItems.PE, 4));
                craftTime = 60f;
                powerUse = 2f;
                heatEnabled = true;
                heatRequirement = 5f;
                recipeMaxEfficiency = 4f;
                stopLowTemperature = true;
                stopUndervoltage = true;
                efficiencyCurvePower = 1.5f;
            }});
        }};

        USYHydrothermalReactor = new GenericCrafter("USYHydrothermalReactor"){{
            size = 5;
            health = 50;
            itemCapacity = 40;
            liquidCapacity = 120;
            outputsLiquid = true;
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
}
