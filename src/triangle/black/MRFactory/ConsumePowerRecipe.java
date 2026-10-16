package triangle.black.MRFactory;

import arc.func.Func;
import mindustry.gen.Building;
import mindustry.world.consumers.ConsumePower;

/**
 * 自定义电力消耗器，根据当前配方动态调整电力消耗。
 * 实际消耗由 requestedPower() 按配方 powerUse（每 tick）驱动；
 * 父类 usage 字段仅用于 powerUse 统计显示，而 MultiRecipeFactory.setStats()
 * 已移除该显示（改用 displayRecipes 自定义展示），故无需每帧同步 usage。
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
    public float efficiency(Building build) {
        // 欠压即停：当前配方低于需求电量（power.status < 1）时，该消费器视为零效率（不耗电、配合 shouldConsume 使生产完全停止）
        if (isUndervoltage(build)) return 0f;
        return super.efficiency(build);
    }
    
    /**
     * 欠压跳闸判定：当前配方需要电力（powerUse > 0）、设定“欠压即停”（stopUndervoltage），
     * 且供电不满足需求（power.status < 1）时返回 true。
     * 电力恢复供应并达到需求后自动返回 false，从而恢复生产（行为与热量的低温跳闸 stopLowTemperature 对称）。
     */
    public boolean isUndervoltage(Building build) {
        Recipe recipe = recipeProvider.get(build);
        return recipe != null && recipe.powerUse > 0 && recipe.stopUndervoltage
                && build.power != null && build.power.status < 1f;
    }
}
