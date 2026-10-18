package triangle.content;

import arc.Events;
import arc.util.Log;
import mindustry.content.Blocks;
import mindustry.content.TechTree;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;

public class SerTriTechTree {
    public static void load(){
        // 将 child 挂载到 Serpulo 科技树必须延迟到 ContentInitEvent：
        // 此时 JSON 内容（含模组 json 经 research 生成的科技树节点）与完整科技树均已加载完成，
        // 可避免在 loadContent 阶段直接调用 addNode 时因父节点尚未生成而被跳过。
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.TiRollingMill, TriFactory.boiler));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.TiRollingMill, TriFactory.BulletFactory));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.boiler, TriFactory.Condenser));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(Blocks.wave, TriTurret.solubilize));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.saltDistillationFurnace, TriFactory.liquidFillingMachine));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.liquidFillingMachine, TriFactory.liquidPourer));

        // 石油化工
        // 注意依赖顺序：addNode 要求父节点已在科技树中
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.IodinationPurificationFurnace, TriFactory.oilElectricDesaltingTank));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilElectricDesaltingTank, TriFactory.USYHydrothermalReactor));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.USYHydrothermalReactor, TriFactory.USYMixer));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilElectricDesaltingTank, TriFactory.oilPrimaryAtmosphericDistillationTower));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilPrimaryAtmosphericDistillationTower, TriFactory.oilSecondaryAtmosphericDistillationTower));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilPrimaryAtmosphericDistillationTower, TriFactory.oilVacuumDistillationColumn));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilVacuumDistillationColumn, TriFactory.oilCatalyticCrackingTower));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilVacuumDistillationColumn, TriFactory.oilCokingHydrogenationTower));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilCokingHydrogenationTower, TriFactory.oilSteamCrackingTower));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilSteamCrackingTower, TriFactory.oilTubularReactor));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilVacuumDistillationColumn, TriFactory.oilHydrocrackingTower));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilHydrocrackingTower, TriFactory.oilCatalyticReformingTower));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilCatalyticReformingTower, TriFactory.oilAromaticHydrocarbonComplex));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.oilAromaticHydrocarbonComplex, TriFactory.ExplosiveMixer));
    }

    /*
     * content 已在科技树中的父内容
     * child   要挂载的 Java 子内容
     */
    public static void addNode(UnlockableContent content, UnlockableContent child){
        TechTree.TechNode context = TechTree.all.find(t -> t.content == content);
        if(context == null){
            Log.warn("[triangle] addNode: parent '@' not found in tech tree; child '@' not attached.", content.name, child.name);
            return;
        }

        // 去重：若该子内容已挂在同一父节点下，直接复用并同步星球，避免重复
        for(TechTree.TechNode existing : context.children){
            if(existing.content == child){
                existing.planet = context.planet;
                return;
            }
        }

        // 创建并挂载。构造函数会自动把节点注册进 TechTree.all 并设置 child.techNode；
        // 因以 null 父级创建，需手动补全 parent 与继承父级 planet。
        TechTree.TechNode node = new TechTree.TechNode(null, child, child.researchRequirements());
        context.children.add(node);
        node.parent = context;
        node.planet = context.planet;
    }
}
