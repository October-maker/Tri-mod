package triangle.content;

import arc.Events;
import arc.util.Log;
import mindustry.content.Blocks;
import mindustry.content.TechTree;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;

public class SerTriTechTree {
    public static void load(){
        // 将child挂载到SerContent之后（Serpulo科技树）必须延迟到ContentInitEvent：此时JSON内容（含模组json内容经research生成的科技树节点）与完整科技树均已加载完成；以达到避免在loadContent阶段直接调用addNode时发生因为父节点尚未生成导致的跳过。
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.TiRollingMill, TriFactory.boiler));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.TiRollingMill, TriFactory.BulletFactory));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.boiler, TriFactory.Condenser));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(Blocks.wave, TriTurret.solubilize));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.saltDistillationFurnace, TriFactory.liquidFillingMachine));
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.liquidFillingMachine, TriFactory.liquidPourer));
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
