package triangle.content;

import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.TechTree;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import triangle.black.MRFactory.MultiRecipeFactory;

/**
 * 把 Java 注册的方块接入赛普罗（Serpulo）科技树。
 *
 * <p>模组方块大多没有在 JSON 中声明 {@code research}，因此需要在此手动挂载。
 * 挂载必须等到 {@link EventType.ContentInitEvent}：此时 JSON 内容与其生成的科技树节点、
 * 以及完整科技树均已加载完成，能可靠地找到父节点。
 */
public class SerTriTechTree {

    /** 延迟到本类所有 addNode 事件之后再挂载的节点。 */
    private static final Seq<Runnable> pendingNodes = new Seq<>();
    /** ContentInitEvent 是否已经执行完毕；之后的新节点直接挂载。 */
    private static boolean contentInitDone = false;

    /** 注册科技树节点（由 {@code triangle.loadContent()} 调用）。 */
    public static void load(){
        // 将 child 挂载到赛普罗科技树必须延迟到 ContentInitEvent：
        // 此时 JSON 内容（含模组 json 经 research 生成的科技树节点）与完整科技树均已加载完成，
        // 可避免在 loadContent 阶段直接调用 addNode 时因父节点尚未生成而被跳过。

        // ---- 基础工业与多配方工厂 ----
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.TiRollingMill, TriFactory.boiler));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.TiRollingMill, TriFactory.BulletFactory));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.boiler, TriFactory.Condenser));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(Blocks.wave, TriTurret.solubilize));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(Blocks.salvo, TriTurret.ciws430));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.saltDistillationFurnace, TriFactory.liquidFillingMachine));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.liquidFillingMachine, TriFactory.liquidPourer));

        // ---- 石油化工 ----
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

        // 多配方工厂的堆叠研究节点：必须在工厂自身节点已由上面的 addNode 挂载后再挂载。
        Events.on(EventType.ContentInitEvent.class, e -> {
            for(Runnable pending : pendingNodes){
                pending.run();
            }
            pendingNodes.clear();
            contentInitDone = true;
        });
    }

    /**
     * 延迟挂载 Java 定义内容的科技树节点。
     * 多配方工厂本身通过本类 addNode 挂载，因此其堆叠研究节点不能在同一个
     * ContentInitEvent 监听器中立即挂载；这里排到本类末尾统一执行。
     */
    public static void addNodeLater(UnlockableContent parent, UnlockableContent child){
        // 本类 flush 监听器注册得较早，配方节点的事件可能在其之后才注册。
        // 此时不能再入队，否则不会再有 flush，节点会从科技树里消失。
        if(contentInitDone){
            addNode(parent, child);
        }else{
            pendingNodes.add(() -> addNode(parent, child));
        }
    }

    /**
     * 把子内容挂到指定父内容之后。
     *
     * @param content 已在科技树中的父内容
     * @param child   要挂载的 Java 子内容
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
        if(child instanceof MultiRecipeFactory.RecipeResearch research
            && research.unlockCondition != null){
            node.objectives.add(new MultiRecipeFactory.UnlockConditionObjective(
                research.unlockCondition, research.unlockConditionText));
        }
        context.children.add(node);
        node.parent = context;
        node.planet = context.planet;
    }
}
