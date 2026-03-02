package triangle.content;

import arc.graphics.Color;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.heat.HeatProducer;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.HeatCrafter;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawFlame;
import mindustry.world.draw.DrawMulti;
import triangle.black.MultiRecipeFactory;
import triangle.black.Recipe;

import static mindustry.type.ItemStack.with;

public class TriFactory {
    public static GenericCrafter
            electrolyticCell,oxygenReductionFurnace,IExtractor,IodinationPurificationFurnace,bauxiteElectrolyticCell,VBlastFurnace,VBlastFurnaceBlowing,VacuumAluminothermicGenerator,CoSulfationRoaster,WSteelMixingFurnace,WCarbideFurnace,
            FeBlastFurnace,TiRollingMill,SCombustionVessel,AcidReactionKettle,AcidResistantHeater,saltDistillationFurnace,SaltMixingPool,CuElectrolyticRefiningPool,NickelDiaphragmElectrolyzer,alkalineElectrolyzer,CopperConcentrator,CoElectrolyticCell;
    public static HeatProducer SAbsorptionTower,FeMixingPool;
    public static HeatCrafter GreenVitriolRoastingFurnace,electricArcFurnaceFluidizedBedReactor,SiReductionFurnace,sandRefiningPool;
    //N
    public static GenericCrafter NCollector,NCatalyticChamber,NOxidationPlantGroup,NAbsorptionTower,NClMixedPool,ExplosiveMixer;
    //oil
    public static GenericCrafter oilElectricDesaltingTank,oilPrimaryAtmosphericDistillationTower,oilSecondaryAtmosphericDistillationTower,oilVacuumDistillationColumn,
            oilCatalyticCrackingTower,USYHydrothermalReactor,USYMixer,oilHydrocrackingTower,oilCatalyticReformingTower,oilAromaticHydrocarbonComplex,oilCokingHydrogenationTower,oilSteamCrackingTower;
    //bullet
    public static MultiRecipeFactory BulletFactory;
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
        //bullet
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
                        powerUse = 60f;                    }}
//                    new Recipe() {{
//                        inputItem.add(new ItemStack(Items.thorium, 2));
//                        inputLiquid.add(new LiquidStack(TriLiquids.H2O2,0.2f));
//                        outputItem.add(new ItemStack(Items.plastanium, 1));
//                        craftTime = 120f;
//                    }}
            );
        }};
    }
}
