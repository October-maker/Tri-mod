package triangle.content;

import arc.Events;
import arc.util.Log;
import mindustry.content.Planets;
import mindustry.content.TechTree;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.type.ItemStack;
import static mindustry.content.TechTree.node;
import static mindustry.content.TechTree.nodeRoot;

public class SerTriTechTree {
    public static void load(){
        TechTree.TechNode root = nodeRoot("SerTriTechTree", TriItems.SerTri, () -> {
        	node(TriFactory.BulletFactory, ItemStack.with(), () -> {
                node(TriFactory.liquidFillingMachine, ItemStack.with(), () -> {});
                node(TriFactory.liquidPourer, ItemStack.with(), () -> {});
            });
            node(TriTurret.solubilize, ItemStack.with(), () -> {});
        });

        root.planet = Planets.serpulo;
        root.children.each(c -> c.planet = Planets.serpulo);

        // 将 boiler 挂载到 TiRollingMill 之后（Serpulo 科技树）。
        // 必须延迟到 ContentInitEvent：此时 JSON 内容（含 TiRollingMill 经 research 生成的科技树节点）
        // 与完整科技树均已加载完成；若在 loadContent 阶段直接调用 addNode，父节点尚未生成会被跳过。
        Events.on(EventType.ContentInitEvent.class, e -> addNode(TriFactory.TiRollingMill, TriFactory.boiler));
    }

    /**
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
        context.children.add(node);
        node.parent = context;
        node.planet = context.planet;
    }
}
