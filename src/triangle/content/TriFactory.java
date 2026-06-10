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
import triangle.black.MultiRecipeFactory;
import triangle.black.Recipe;

import static mindustry.type.ItemStack.with;

public class TriFactory {
    public static GenericCrafter
            electrolyticCell,oxygenReductionFurnace,IExtractor,IodinationPurificationFurnace,bauxiteElectrolyticCell,VBlastFurnace,VBlastFurnaceBlowing,VacuumAluminothermicGenerator,CoSulfationRoaster,WSteelMixingFurnace,WCarbideFurnace,
            FeBlastFurnace,TiRollingMill,SCombustionVessel,AcidReactionKettle,AcidResistantHeater,saltDistillationFurnace,SaltMixingPool,CuElectrolyticRefiningPool,NickelDiaphragmElectrolyzer,alkalineElectrolyzer,CopperConcentrator,CoElectrolyticCell,
            CrThermiteReactionChamber,armoredSinteringFurnace;
    public static HeatProducer SAbsorptionTower,FeMixingPool;
    public static HeatCrafter GreenVitriolRoastingFurnace,electricArcFurnaceFluidizedBedReactor,SiReductionFurnace,sandRefiningPool;
    //N
    public static GenericCrafter NCollector,NCatalyticChamber,NOxidationPlantGroup,NAbsorptionTower,NClMixedPool,ExplosiveMixer;
    //oil
    public static GenericCrafter oilElectricDesaltingTank,oilPrimaryAtmosphericDistillationTower,oilSecondaryAtmosphericDistillationTower,oilVacuumDistillationColumn,
            oilCatalyticCrackingTower,USYHydrothermalReactor,USYMixer,oilHydrocrackingTower,oilCatalyticReformingTower,oilAromaticHydrocarbonComplex,oilCokingHydrogenationTower,oilSteamCrackingTower;
    //special
    public static MultiRecipeFactory BulletFactory,liquidFillingMachine,liquidPourer,ComprehensiveProcessingFactory;
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
        //N
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
        WCarbideFurnace = new GenericCrafter("WCarbideFurnace"){{}};
        WSteelMixingFurnace = new GenericCrafter("WSteelMixingFurnace"){{}};
        CrThermiteReactionChamber = new GenericCrafter("CrThermiteReactionChamber"){{}};
        armoredSinteringFurnace = new GenericCrafter("armoredSinteringFurnace"){{}};
        //oil
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
        //special
        BulletFactory = new MultiRecipeFactory("BulletFactory"){{
            size = 4;
            health = 20;
            itemCapacity = 20;
            liquidCapacity = 20;
            requirements(Category.crafting, with(TriItems.Cu,10,TriItems.TiAlloy,30,TriItems.FeSteel,20,TriItems.MonocrystallineSi,10));
            drawer = new DrawMulti(new DrawDefault(), new DrawFlame(Color.valueOf("E58760")));
            recipes.add(
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.Cu, 2));
                        inputItem.add(new ItemStack(TriItems.FeSteel,1));
                        inputItem.add(new ItemStack(TriItems.TNT,1));
                        outputItem.add(new ItemStack(TriItems.LHEAT, 4));
                        craftTime = 15f;
                        powerUse = 20f;
                    }},
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.bauxite, 1));
                        inputItem.add(new ItemStack(TriItems.FeSteel,1));
                        inputItem.add(new ItemStack(TriItems.Fe2O3,3));
                        outputItem.add(new ItemStack(TriItems.thermite, 6));
                        craftTime = 10f;
                        powerUse = 10f;
                    }},
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.WSteel,2));
                        inputItem.add(new ItemStack(TriItems.Ni,1));
                        outputItem.add(new ItemStack(TriItems.WBullet,3));
                        craftTime = 60f;
                        powerUse = 60f;
                    }}
//                    new Recipe() {{
//                        inputItem.add(new ItemStack(Items.thorium, 2));
//                        inputLiquid.add(new LiquidStack(TriLiquids.H2O2,0.2f));
//                        outputItem.add(new ItemStack(Items.plastanium, 1));
//                        craftTime = 120f;
//                    }}
            );
        }};
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
                        powerUse = 12f;
                    }},//water
                new Recipe() {{
                    inputItem.add(new ItemStack(Items.metaglass, 2));
                    inputLiquid.add(new LiquidStack(TriLiquids.salineWater, 1));
                    outputItem.add(new ItemStack(TriItems.saltwaterBucket, 5));
                    craftTime = 60f;
                    powerUse = 12f;
                }}
            );
        }};
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
                        powerUse = 7f;
                    }},//water
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.saltwaterBucket, 5));
                        outputLiquid.add(new LiquidStack(TriLiquids.salineWater, 1));
                        craftTime = 60f;
                        powerUse = 7f;
                    }}
            );
        }};
        ComprehensiveProcessingFactory = new MultiRecipeFactory("ComprehensiveProcessingFactory"){{
            size = 7;
            health = 30;
            itemCapacity = 850;
            liquidCapacity = 500;
            requirements(Category.crafting, with(TriItems.Cu,50,TriItems.TiAlloy,80,TriItems.FeSteel,30,TriItems.Ni,45,TriItems.chip4004,25));
            recipes.add(
                    new Recipe() {{
                        inputItem.add(new ItemStack(Items.titanium, 15));
                        outputItem.add(new ItemStack(TriItems.Ti, 13));
                        craftTime = 60;
                        powerUse = 320;
                    }},//IodinationPurificationFurnace
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.bauxite, 20));
                        outputItem.add(new ItemStack(TriItems.Al, 12));
                        outputLiquid.add(new LiquidStack(Liquids.ozone, 0.8f));
                        craftTime = 60;
                        powerUse = 480;
                    }},//bauxiteElectrolyticCell
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.VTiFe, 15));
                        inputItem.add(new ItemStack(Items.coal, 8));
                        outputLiquid.add(new LiquidStack(TriLiquids.VBearingHotMetal, 1.2f));
                        outputItem.add(new ItemStack(Items.scrap, 1));
                        craftTime = 60;
                        powerUse = 60;
                    }},//VBlastFurnace
                    new Recipe() {{
                        inputLiquid.add(new LiquidStack(TriLiquids.VBearingHotMetal, 1.2f));
                        inputItem.add(new ItemStack(Items.coal, 3));
                        outputItem.add(new ItemStack(TriItems.V2O5, 12));
                        outputItem.add(new ItemStack(TriItems.FeSteel, 14));
                        craftTime = 60;
                        powerUse = 192;
                    }}//VBlastFurnaceBlowing
            );
            recipes.add(
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.Al, 16));
                        inputItem.add(new ItemStack(TriItems.V2O5, 12));
                        outputItem.add(new ItemStack(TriItems.V, 8));
                        outputItem.add(new ItemStack(TriItems.bauxite, 20));
                        craftTime = 60;
                        powerUse = 192;
                    }},//VacuumAluminothermicGenerator
                    new Recipe() {{
                        inputItem.add(new ItemStack(TriItems.Ti, 10));
                        inputItem.add(new ItemStack(TriItems.Al, 6));
                        inputItem.add(new ItemStack(TriItems.V, 4));
                        outputItem.add(new ItemStack(TriItems.TiAlloy, 18));
                        craftTime = 60;
                        powerUse = 105;
                    }}//TiRollingMill
            );
        }};
    }
}
