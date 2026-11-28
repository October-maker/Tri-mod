package triangle.black.MRFactory;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.struct.Seq;
import mindustry.gen.Building;
import triangle.black.draw.TriDrawLayer;
import triangle.black.draw.TriDrawPhase;

/**
 * MRF 状态叠加层（方案 1）：在方块左上角画状态色块，
 * 由 {@link triangle.black.draw.TriDrawChain} 作为一个独立的 OVERLAY 条目调度。
 *
 * <p>它只读取建筑状态，不修改任何逻辑；默认行为是“正常生产时什么都不画”，
 * 只有异常态才出现标记，因此关掉该层（或方块不挂链）时外观与原版完全一致。
 *
 * <p>颜色语义：
 * <ul>
 *   <li>灰：停用</li>
 *   <li>青：断电 / 欠压跳闸</li>
 *   <li>橙：当前配方输出已满（下一周期放不下）</li>
 *   <li>紫：低温导致该配方停机（仅 stopLowTemperature=true 的配方）</li>
 *   <li>红：缺少输入（物品/液体/载荷）</li>
 * </ul>
 *
 * <p>堆叠体按层绘制：整列贴着左上角，最下方的色块是第 1 层，往上依次是第 2、3…层；
 * 整栋级别的状态（停用/断电）只画一个色块，位于左上角最上方。
 */
public class RecipeStateLayer extends TriDrawLayer{
    /** 状态色块边长（世界像素）。 */
    public float dotSize = 3f;
    /** 逐层色块间距。 */
    public float dotGap = 1f;
    /** 距方块边缘的留白。 */
    public float margin = 2f;

    public Color disabledColor = Color.valueOf("8A8F98");
    public Color powerColor = Color.valueOf("66CCFF");
    public Color fullColor = Color.valueOf("FF9F43");
    public Color heatColor = Color.valueOf("B36BFF");
    public Color inputColor = Color.valueOf("FF5555");

    /** 状态码：0 = 正常，不画。 */
    private static final int NONE = 0, DISABLED = 1, POWER = 2, FULL = 3, HEAT = 4, INPUT = 5;

    private TextureRegion white;

    public RecipeStateLayer(){
        super(TriDrawPhase.OVERLAY);
    }

    @Override
    public void draw(Building build){
        if(!(build instanceof MultiRecipeFactory.MultiRecipeFactoryBuild mrf)) return;

        // 整栋状态：停用 / 断电，画在左上角。
        int global = globalState(mrf);
        if(global != NONE){
            dot(build, globalX(build), globalY(build), colorOf(global), 0.9f);
            Draw.reset();
            return;
        }

        if(build instanceof StackedMultiRecipeFactory.StackedBuild stacked){
            drawStacked(stacked);
        }else{
            // 刚建成时自动模式可能还没选中任何配方（库存为空时严格检索无结果），
            // 此时回退到面板展示的第一条配方，保证“缺输入”能立刻显示。
            Recipe recipe = mrf.getRecipe();
            if(recipe == null) recipe = mrf.getDisplayRecipe();
            if(recipe == null) return;
            int state = recipeState(mrf, recipe);
            if(state != NONE){
                dot(build, layerX(build), layerY(build, 0, 1), colorOf(state), 0.9f);
            }
        }
        Draw.reset();
    }

    /** 堆叠体逐层：每层按自己的配方判定输出满/低温/缺输入。 */
    private void drawStacked(StackedMultiRecipeFactory.StackedBuild build){
        StackedMultiRecipeFactory block = (StackedMultiRecipeFactory)build.block;
        Seq<Recipe> recipes = ((MultiRecipeFactory)build.block).recipes;
        int count = Math.min(build.layers.size, block.layerCount);
        for(int i = 0; i < count; i++){
            StackedMultiRecipeFactory.LayerState layer = build.layers.get(i);
            int index = layer.auto ? layer.recipeIndex : layer.selectedIndex;
            // 与单层一致：新建成/未选中时用第一条配方占位，避免“缺输入”不显示。
            Recipe recipe = index >= 0 && index < recipes.size
                ? recipes.get(index)
                : (recipes.isEmpty() ? null : recipes.first());
            if(recipe == null) continue;

            int state = recipeState(build, recipe);
            if(state != NONE){
                dot(build, layerX(build), layerY(build, i, count), colorOf(state), 0.9f);
            }
        }
    }


    /** 整栋状态：停用优先于断电。 */
    private int globalState(MultiRecipeFactory.MultiRecipeFactoryBuild build){
        if(!build.enabled) return DISABLED;
        if(build.undervoltageLock > 0f) return POWER;
        if(build.power != null && build.power.status <= 0f) return POWER;
        return NONE;
    }

    /** 单配方状态：输出满 > 低温停机 > 缺输入。 */
    private int recipeState(MultiRecipeFactory.MultiRecipeFactoryBuild build, Recipe recipe){
        if(!build.canOutputForRecipe(recipe)) return FULL;
        if(recipe.heatEnabled && recipe.heatRequirement > 0 && recipe.stopLowTemperature
            && build.rawHeat < recipe.heatRequirement) return HEAT;
        if(!recipe.inputsAvailable(build)) return INPUT;
        return NONE;
    }

    private Color colorOf(int state){
        return switch(state){
            case DISABLED -> disabledColor;
            case POWER -> powerColor;
            case FULL -> fullColor;
            case HEAT -> heatColor;
            case INPUT -> inputColor;
            default -> Color.white;
        };
    }

    private void dot(Building build, float x, float y, Color color, float alpha){
        // 前一个 DrawBlock 可能留下 additive blend / 自定义着色器状态，先归位再画标记。
        Draw.reset();
        Draw.color(color, alpha);
        Draw.rect(white(), x, y, dotSize, dotSize);
    }

    private TextureRegion white(){
        if(white == null) white = Core.atlas.find("white");
        return white;
    }

    private float layerX(Building build){
        return left(build) + margin + dotSize / 2f;
    }

    /**
     * 左上角竖向排列：最下方是第 1 层，最上方是最高层。
     * count 用于把整列贴住方块上边缘，避免层数多时超出方块。
     */
    private float layerY(Building build, int layer, int count){
        float topY = top(build) - margin - dotSize / 2f;
        return topY - (Math.max(1, count) - 1 - layer) * (dotSize + dotGap);
    }

    private float globalX(Building build){
        return left(build) + margin + dotSize / 2f;
    }

    private float globalY(Building build){
        return top(build) - margin - dotSize / 2f;
    }

    private float left(Building build){
        return build.x - build.block.size * 4f;
    }

    private float top(Building build){
        // Mindustry 世界坐标 y 向上；左上角 = x - 半宽, y + 半高。
        return build.y + build.block.size * 4f;
    }
}
