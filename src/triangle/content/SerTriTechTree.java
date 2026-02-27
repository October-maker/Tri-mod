package triangle.content;

import mindustry.content.Planets;
import mindustry.content.TechTree;
import mindustry.type.ItemStack;
import static mindustry.content.TechTree.node;
import static mindustry.content.TechTree.nodeRoot;

public class SerTriTechTree {
    public static void load(){
        TechTree.TechNode root = nodeRoot("SerTriTechTree", Planets.serpulo, () -> {
        	node(TriFactory.BulletFactory, ItemStack.with(), () -> {});
            node(TriTurret.solubilize, ItemStack.with(), () -> {});
        });

        root.planet = Planets.serpulo;
        root.children.each(c -> c.planet = Planets.serpulo);
    }
}
