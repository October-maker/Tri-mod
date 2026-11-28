package triangle.black.draw;

import arc.graphics.g2d.TextureRegion;
import arc.struct.Seq;
import arc.util.Eachable;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.draw.DrawBlock;
import mindustry.world.draw.DrawDefault;

/**
 * 相位链：把若干原生 {@link DrawBlock} 与若干 {@link TriDrawLayer} 组合成一个 DrawBlock，
 * 直接赋给 {@code Block.drawer} 即可介入原版绘制链路。
 *
 * <p>执行顺序固定为：
 * <pre>
 * UNDER 层 → 原生 DrawBlock（按添加顺序）→ BODY 层 → OVERLAY 层
 * </pre>
 * {@code drawLight()} 只跑原生 DrawBlock 的发光与 LIGHT 层；
 * {@code drawPlan()} 只跑原生 DrawBlock 的预览与 PLAN 层；
 * {@code icons()/getRegionsToOutline()} 会汇总所有条目。
 *
 * <p>空链 + 无层 = 与原版 DrawDefault 等价的空壳；因此“默认行为”始终是
 * “不新增任何像素”，只有显式添加的层才会产生影响。
 */
public class TriDrawChain extends DrawBlock{
    /** 原生绘制组合，按添加顺序执行（通常就是原来的 DrawMulti）。 */
    public final Seq<DrawBlock> blocks = new Seq<>();
    /** 自定义相位层，按 {@link TriDrawLayer#phase} 调度。 */
    public final Seq<TriDrawLayer> layers = new Seq<>();

    public TriDrawChain(){
    }

    public TriDrawChain(DrawBlock... blocks){
        this.blocks.addAll(blocks);
    }

    /** 包装一个已有的 DrawBlock（不会重复包装：传入链时原样返回）。 */
    public static TriDrawChain of(DrawBlock base){
        if(base instanceof TriDrawChain chain) return chain;
        return new TriDrawChain(base == null ? new DrawDefault() : base);
    }

    public TriDrawChain add(DrawBlock block){
        if(block != null && block != this) blocks.add(block);
        return this;
    }

    public TriDrawChain add(TriDrawLayer layer){
        if(layer != null) layers.add(layer);
        return this;
    }

    @Override
    public void load(Block block){
        for(DrawBlock drawer : blocks) drawer.load(block);
        for(TriDrawLayer layer : layers) layer.load(block);
    }

    @Override
    public void draw(Building build){
        drawPhase(TriDrawPhase.UNDER, build);
        for(DrawBlock drawer : blocks) drawer.draw(build);
        drawPhase(TriDrawPhase.BODY, build);
        drawPhase(TriDrawPhase.OVERLAY, build);
    }

    @Override
    public void drawLight(Building build){
        for(DrawBlock drawer : blocks) drawer.drawLight(build);
        for(TriDrawLayer layer : layers){
            if(layer.phase == TriDrawPhase.LIGHT) layer.drawLight(build);
        }
    }

    @Override
    public void drawPlan(Block block, BuildPlan plan, Eachable<BuildPlan> plans){
        for(DrawBlock drawer : blocks) drawer.drawPlan(block, plan, plans);
        for(TriDrawLayer layer : layers){
            if(layer.phase == TriDrawPhase.PLAN) layer.drawPlan(block, plan, plans);
        }
    }

    @Override
    public void getRegionsToOutline(Block block, Seq<TextureRegion> out){
        for(DrawBlock drawer : blocks) drawer.getRegionsToOutline(block, out);
        for(TriDrawLayer layer : layers) layer.getRegionsToOutline(block, out);
    }

    @Override
    public TextureRegion[] icons(Block block){
        Seq<TextureRegion> out = new Seq<>();
        for(DrawBlock drawer : blocks){
            TextureRegion[] icons = drawer.icons(block);
            if(icons != null) out.addAll(icons);
        }
        for(TriDrawLayer layer : layers){
            TextureRegion[] icons = layer.icons(block);
            if(icons != null) out.addAll(icons);
        }
        return out.toArray(TextureRegion.class);
    }

    private void drawPhase(TriDrawPhase phase, Building build){
        for(TriDrawLayer layer : layers){
            if(layer.phase == phase) layer.draw(build);
        }
    }
}
