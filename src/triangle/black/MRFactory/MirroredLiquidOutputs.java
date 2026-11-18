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
 * 原版 {@link DrawLiquidOutputs} 的镜像感知版：沿用原版机制（DrawBlock 挂 drawer、按
 * {@code (方向 + rotation) % 4} 取面、{@link Draw#rect(TextureRegion, float, float, float)} 画面图标），
 * 但取的是这台 MRF **当前配方**的输出液体，并按建筑的镜像位翻转方向。
 *
 * <p>本体（{@link #draw(Building)}）与悬停/放置虚影（{@link #drawPlan(Block, BuildPlan, Eachable)}）
 * 走同一份计算：虚影如果落在已放置的 MRF 上，直接读那台建筑的配方、rotation 与镜像位，
 * 所以虚影显示的液体种类、方向与实际倒液完全一致；空地上放蓝图时才回落到 {@code plan.config}。
 *
 * <p>贴图仍按原版命名 {@code <block.name>-<liquid.name>-output1/2} 查找；
 * 找不到时回退成该液体的 {@code fullIcon}，贴在对应面中心，避免出现 ohno 贴图。
 */
public class MirroredLiquidOutputs extends DrawLiquidOutputs{

    /** 是否已经落到 error 贴图（mod 没有画 output 贴图时）。 */
    private boolean fallback;

    @Override
    public void load(Block block){
        super.load(block);
        fallback = liquidOutputRegions == null
            || liquidOutputRegions.length == 0
            || liquidOutputRegions[0].length == 0
            || !Core.atlas.isFound(liquidOutputRegions[0][0]);
    }

    @Override
    public void drawPlan(Block block, BuildPlan plan, Eachable<BuildPlan> list){
        if(!(block instanceof MultiRecipeFactory factory)) return;

        // 虚影落在已放置的 MRF 上（悬停预览）：直接读那台建筑的对象状态，
        // 这样显示的液体种类、方向、镜像位与实际倒液完全一致。
        Building hovered = Vars.world == null ? null : Vars.world.build(plan.x, plan.y);
        if(hovered instanceof MultiRecipeFactory.MultiRecipeFactoryBuild mr && hovered.block == block){
            drawOutputs(factory, factory.previewOutputLiquids(mr), mr.liquidMirror,
                mr.rotation, plan.drawx(), plan.drawy(), block.size);
            return;
        }

        // 空地上的蓝图：配方与镜像位只能从 config 里解。
        drawOutputs(factory, factory.previewOutputLiquids(plan), MultiRecipeFactory.mirrorMaskOf(plan.config),
            plan.rotation, plan.drawx(), plan.drawy(), block.size);
    }

    /** 按“输出液体 + 方向数组 + 镜像位 + 朝向”统一绘制一组输出面。 */
    private void drawOutputs(MultiRecipeFactory factory, Seq<LiquidStack> outputs, int mask,
                             int rotation, float x, float y, int size){
        if(outputs == null || outputs.size == 0) return;
        for(int i = 0; i < outputs.size; i++){
            int dir = factory.previewOutputDirection(i);
            if(dir == -1) continue;
            int side = Mathf.mod(MultiRecipeFactory.mirrorDirection(dir, mask) + rotation, 4);
            drawFace(outputs.get(i), i, x, y, side, size);
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
