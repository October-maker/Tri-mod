package triangle.tmi;

import arc.struct.Seq;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.type.PayloadStack;
import mindustry.world.Block;
import org.jetbrains.annotations.NotNull;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeParser;
import tmi.recipe.RecipeType;
import tmi.recipe.parser.GenericCrafterParser;
import tmi.recipe.types.HeatMark;
import tmi.recipe.types.PowerMark;
import tmi.recipe.types.RecipeItemType;
import triangle.black.MultiRecipeFactory;

/**
 * 为多配方工厂 {@link MultiRecipeFactory} 向 TMI(TooManyItems) 注册配方。
 *
 * <p>由于 {@code MultiRecipeFactory} 继承自 {@code GenericCrafter}，本解析器需要将 TMI 默认的
 * {@link GenericCrafterParser} 排除，避免该方块被重复/错误地解析成原版单配方。</p>
 */
public class TriFactoryParser extends RecipeParser<MultiRecipeFactory> {

    // MultiRecipeFactory 继承自 GenericCrafter，需排除 TMI 默认的 GenericCrafterParser，
    // 使该类型方块只由本解析器处理。
    @Override
    public @NotNull Seq<Class<? extends RecipeParser<?>>> getExcludes() {
        Seq<Class<? extends RecipeParser<?>>> excludes = new Seq<>();
        excludes.add(GenericCrafterParser.class);
        return excludes;
    }

    @Override
    public boolean isTarget(@NotNull Block content) {
        return content instanceof MultiRecipeFactory;
    }

    @Override
    public @NotNull Seq<Recipe> parse(@NotNull MultiRecipeFactory factory) {
        Seq<Recipe> out = new Seq<>();

        // 为该工厂声明的每个自定义配方生成一个 TMI 配方
        for (triangle.black.Recipe r : factory.recipes) {
            Recipe recipe = new Recipe(RecipeType.getFactory(), getWrap(factory), r.craftTime);

            // 输入物品（每周期消耗，整数显示）
            for (ItemStack stack : r.inputItem) {
                recipe.addMaterialInteger(getWrap(stack.item), stack.amount);
            }

            // 输入液体（每秒消耗）
            for (LiquidStack stack : r.inputLiquid) {
                recipe.addMaterialPersec(getWrap(stack.liquid), stack.amount);
            }

            // 输入载荷（每周期消耗）
            for (PayloadStack stack : r.inputPayload) {
                recipe.addMaterialInteger(getWrap(stack.item), stack.amount);
            }

            // 电力消耗（每秒，POWER 计算区）
            if (r.powerUse > 0) {
                recipe.addMaterialPersec(PowerMark.INSTANCE, r.powerUse).setType(RecipeItemType.POWER);
            }

            // 热量需求（与 TMI 原生 HeatCrafter 解析惯例一致：HeatMark 归入 POWER 区）
            if (r.heatEnabled && r.heatRequirement > 0) {
                recipe.addMaterial(HeatMark.INSTANCE, r.heatRequirement)
                        .setType(RecipeItemType.POWER)
                        .floatFormat();
            }

            // 输出物品
            for (ItemStack stack : r.outputItem) {
                recipe.addProductionInteger(getWrap(stack.item), stack.amount);
            }

            // 输出液体（每秒）
            for (LiquidStack stack : r.outputLiquid) {
                recipe.addProductionPersec(getWrap(stack.liquid), stack.amount);
            }

            // 输出载荷
            for (PayloadStack stack : r.outputPayload) {
                recipe.addProductionInteger(getWrap(stack.item), stack.amount);
            }

            // 热量产出（与 TMI 原生 HeatProducer 解析惯例一致）
            if (r.heatEnabled && r.heatOutput > 0) {
                recipe.addProduction(HeatMark.INSTANCE, r.heatOutput)
                        .setType(RecipeItemType.POWER)
                        .floatFormat();
            }

            out.add(recipe);
        }

        return out;
    }
}
