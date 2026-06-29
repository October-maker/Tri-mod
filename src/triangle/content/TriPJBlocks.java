package triangle.content;

import mindustry.type.Category;
import triangle.black.projection.TriPJBlock;

import static mindustry.type.ItemStack.with;

public class TriPJBlocks {
    public static TriPJBlock Triblock;
    public static void load() {
        Triblock = new TriPJBlock("Triblock"){{
            size = 4;
            health = 20;
            itemCapacity = 20;
            liquidCapacity = 20;
            requirements(Category.crafting, with(TriItems.Cu,10,TriItems.TiAlloy,30,TriItems.FeSteel,20,TriItems.MonocrystallineSi,10));
        }};
    }
}
