package triangle.black.MRFactory;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Eachable;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.draw.DrawBlock;
import mindustry.world.draw.DrawLiquidOutputs;

/**
 * 原版 {@link DrawLiquidOutputs} 的镜像感知版：完全沿用原版机制——
 * 作为 {@link DrawBlock} 挂在方块 drawer 里、按 {@code (方向 + rotation) % 4} 取面、
 * 用 {@link Draw#rect(TextureRegion, float, float, float)} 画输出面图标，
 * 唯一区别是方向先按建筑的镜像位（{@code #f<mask>}）翻转。
 *
 * <p>贴图仍按原版命名 {@code <block.name>-<liquid.name>-output1/2} 查找；
 * 找不到时回退成该液体的 {@code fullIcon}，贴在对应面中心，避免出现 ohno 贴图。
 */
public class MirroredLiquidOutputs extends DrawLiquidOutputs{

    /** 该面是否已经落到 error 贴图（mod 没有画 output 贴图时）。 */
    private boolean fallback;

    @Override
    public void load(Block block){
        super.load(block);
        fallback = liquidOutputRegions == null
            || liquidOutputRegions.length == 0
            || liquidOutputRegions[0].length == 0
            || !Core.atlas.isFound(liquidOutputRegions[0][0]);
    }

    private static int maskOf(Building build){
        return build instanceof MultiRecipeFactory.MultiRecipeFactoryBuild mr ? mr.liquidMirror : 0;
    }

    @Override
    public void draw(Building build){
        if(!(build.block instanceof MultiRecipeFactory factory)) return;
        Seq<LiquidStack> outputs = factory.previewOutputLiquids(build);
        if(outputs == null || outputs.size == 0) return;

        int mask = maskOf(build);
        for(int i = 0; i < outputs.size; i++){
            int dir = factory.previewOutputDirection(i);
            if(dir == -1) continue;
            int side = Mathf.mod(MultiRecipeFactory.mirrorDirection(dir, mask) + build.rotation, 4);
            drawFace(outputs.get(i), i, build.x, build.y, side, build.block.size);
        }
    }

    @Override
    public void drawPlan(Block block, BuildPlan plan, Eachable<BuildPlan> list){
        if(!(block instanceof MultiRecipeFactory factory)) return;

        // 悬停已放置方块时的虚影：脚下降落点通常就是那台建筑本身，
        // 优先读它真实的配方与镜像位（plan.config 在悬停预览里可能是 null 或不带 #f）。
        Building hovered = Vars.world == null ? null : Vars.world.build(plan.x, plan.y);
        MultiRecipeFactory.MultiRecipeFactoryBuild target =
            hovered instanceof MultiRecipeFactory.MultiRecipeFactoryBuild mr && hovered.block == block ? mr : null;

        Seq<LiquidStack> outputs = target != null ? factory.previewOutputLiquids(target) : factory.previewOutputLiquids(plan);
        if(outputs == null || outputs.size == 0) return;

        int mask = target != null ? target.liquidMirror : MultiRecipeFactory.mirrorMaskOf(plan.config);
        for(int i = 0; i < outputs.size; i++){
            int dir = factory.previewOutputDirection(i);
            if(dir == -1) continue;
            int side = Mathf.mod(MultiRecipeFactory.mirrorDirection(dir, mask) + plan.rotation, 4);
            drawFace(outputs.get(i), i, plan.drawx(), plan.drawy(), side, block.size);
        }
    }

    /** 有原版 output 贴图就按原版方式画；没有就用液体图标贴到面中心。 */
    private void drawFace(LiquidStack output, int index, float x, float y, int side, int size){
        if(output == null || output.liquid == null) return;
        if(!fallback && liquidOutputRegions != null
            && liquidOutputRegions[side > 1 ? 1 : 0].length > index
            && liquidOutputRegions[side > 1 ? 1 : 0][index] != null){
            Draw.rect(liquidOutputRegions[side > 1 ? 1 : 0][index], x, y, side * 90f);
            return;
        }
        float off = size * 4f;
        float iconX = x + (side == 0 ? off : side == 2 ? -off : 0f);
        float iconY = y + (side == 1 ? off : side == 3 ? -off : 0f);
        Draw.rect(output.liquid.fullIcon, iconX, iconY);
    }
}
