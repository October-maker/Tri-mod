package triangle.tmi;

import arc.util.Log;
import tmi.RecipeEntry;
import tmi.TooManyItems;

/**
 * TMI(TooManyItems) 第三方配方入口。
 *
 * <p>仅在用户同时安装了 TMI 与本模组时，由 TMI 通过 {@code recipeEntry} 元信息或
 * {@code @RecipeEntryPoint} 注解自动加载并调用本类；未安装 TMI 时本类不会被加载。</p>
 */
public class TriRecipeEntry implements RecipeEntry {

    @Override
    public void init() {
        // 注册多配方工厂的配方解析器
        TooManyItems.recipesManager.registerParser(new TriFactoryParser());
        Log.info("Triangle: TMI recipe entry init");
    }

    @Override
    public void afterInit() {
        // 可选的后初始化，暂无操作
    }
}
