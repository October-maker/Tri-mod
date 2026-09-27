package triangle.black;

import arc.func.Func;
import mindustry.gen.Building;
import mindustry.world.consumers.ConsumePower;

/**
 * 自定义电力消耗器，根据当前配方动态调整电力消耗。
 * 每次 update 时同步 usage 字段，确保父类 ConsumePower 的电力消耗逻辑正确执行。
 */
public class ConsumePowerRecipe extends ConsumePower {
    private final Func<Building, Recipe> recipeProvider;
    
    public ConsumePowerRecipe(Func<Building, Recipe> recipeProvider) {
        super(0f, 0f, false);
        this.recipeProvider = recipeProvider;
    }
    
    @Override
    public float requestedPower(Building build) {
        Recipe recipe = recipeProvider.get(build);
        return recipe == null ? 0f : recipe.powerUse;
    }
    
    @Override
    public void update(Building build) {
        // 每次更新前同步 usage 为当前配方的 powerUse
        Recipe recipe = recipeProvider.get(build);
        usage = recipe == null ? 0f : recipe.powerUse;
        super.update(build);
    }
}
