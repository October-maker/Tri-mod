package triangle.black.MRFactory;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.world.Block;

/**
 * Cross-factory stacking whitelist.
 *
 * <p>默认只允许相同方块互相堆叠；不同多配方工厂需要在这里显式登记。
 * 该列表独立于工厂实例，避免把兼容关系散落在各方块配置里。
 */
public final class StackCompatibility{

    /** 明确允许互相堆叠的方块对（对称）。 */
    private static final Seq<Block[]> compatiblePairs = new Seq<>();
    /** 允许互相堆叠的工厂类。 */
    private static final ObjectSet<Class<?>> compatibleClasses = new ObjectSet<>();

    private StackCompatibility(){}

    /** 允许两个具体方块互相堆叠。 */
    public static void allow(Block a, Block b){
        if(a == null || b == null) return;
        if(a == b) return;
        for(Block[] pair : compatiblePairs){
            if((pair[0] == a && pair[1] == b) || (pair[0] == b && pair[1] == a)) return;
        }
        compatiblePairs.add(new Block[]{a, b});
    }

    /** 允许两个工厂类互相堆叠（方块实例级仍受同组限制）。 */
    public static void allow(Class<? extends Block> a, Class<? extends Block> b){
        if(a != null) compatibleClasses.add(a);
        if(b != null) compatibleClasses.add(b);
    }

    /** 两个方块是否可以出现在同一堆叠中。 */
    public static boolean canStack(Block base, Block other){
        if(base == null || other == null) return false;
        if(base == other) return true;
        for(Block[] pair : compatiblePairs){
            if((pair[0] == base && pair[1] == other) || (pair[0] == other && pair[1] == base)) return true;
        }
        return compatibleClasses.contains(base.getClass()) && compatibleClasses.contains(other.getClass());
    }
}
