package triangle.black.MRFactory;

import mindustry.gen.Building;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;

/**
 * MultiRecipeFactory 的输出辅助逻辑。
 *
 * <p>从 {@code MultiRecipeFactoryBuild} 中抽出的纯输出计算，负责：
 * <ul>
 *   <li>判断当前配方是否还有输出空间（物品/液体各自“全满才停”）；</li>
 *   <li>按进度写入未满的液体输出，跳过已满或无效项；</li>
 *   <li>把超过容量的物品输出钳制到容量上限。</li>
 * </ul>
 *
 * <p>不持有方块/建筑状态，所有容量与库存均由调用方传入，便于单独检查和测试。
 */
final class MultiRecipeOutput{

    private MultiRecipeOutput(){}

    /** 当前配方是否仍有输出空间；物品与液体分别按“全部同类输出都满”判定。 */
    static boolean canOutput(Building build, Recipe recipe, boolean checkItems, boolean checkLiquids){
        if(checkItems && !recipe.outputItem.isEmpty()){
            boolean allFull = true;
            for(int i = 0; i < recipe.outputItem.size; i++){
                ItemStack stack = recipe.outputItem.get(i);
                if(build.items.get(stack.item) < build.block.itemCapacity){
                    allFull = false;
                    break;
                }
            }
            if(allFull) return false;
        }

        if(checkLiquids && !recipe.outputLiquid.isEmpty()){
            boolean allFull = true;
            for(int i = 0; i < recipe.outputLiquid.size; i++){
                LiquidStack stack = recipe.outputLiquid.get(i);
                if(build.liquids.get(stack.liquid) < build.block.liquidCapacity - 0.001f){
                    allFull = false;
                    break;
                }
            }
            if(allFull) return false;
        }

        return true;
    }

    /** 按当前进度向所有未满的液体输出写入；已满/非法输出直接跳过。 */
    static void outputLiquids(Building build, Recipe recipe, float inc){
        if(!Float.isFinite(inc) || inc <= 0f) return;

        for(int i = 0; i < recipe.outputLiquid.size; i++){
            LiquidStack stack = recipe.outputLiquid.get(i);
            float space = build.block.liquidCapacity - build.liquids.get(stack.liquid);
            if(!Float.isFinite(space) || space <= 0f) continue;

            float amount = stack.amount * inc;
            if(!Float.isFinite(amount) || amount <= 0f) continue;

            build.handleLiquid(build, stack.liquid, Math.min(amount, space));
        }
    }

    /** 物品输出达到容量时钳制，避免原版/UI 读取出超过 itemCapacity 的数值。 */
    static void clampItems(Building build, Recipe recipe){
        for(int i = 0; i < recipe.outputItem.size; i++){
            ItemStack stack = recipe.outputItem.get(i);
            if(build.items.get(stack.item) >= build.block.itemCapacity){
                build.items.set(stack.item, build.block.itemCapacity);
            }
        }
    }
}
