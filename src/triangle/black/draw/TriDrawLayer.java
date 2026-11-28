package triangle.black.draw;

import arc.graphics.g2d.TextureRegion;
import arc.struct.Seq;
import arc.util.Eachable;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.world.Block;

/**
 * 相位链里的一条自定义绘制层。
 *
 * <p>与原生 {@code DrawBlock} 的区别：
 * <ul>
 *   <li>层只声明自己的 {@link #phase}，由 {@link TriDrawChain} 统一排序与调度；</li>
 *   <li>绘制入口只拿 {@link Building}，状态从建筑字段读取，不在层里缓存建筑；</li>
 *   <li>{@code load / drawPlan / drawLight / icons / getRegionsToOutline} 都是可选覆写，
 *       默认行为 = 什么都不画（因此未注册的层不会改变任何现有外观）。</li>
 * </ul>
 *
 * <p>着色器等底层操作允许在 {@link #draw(Building)} 里使用，但调用方必须自行恢复
 * {@code Draw} 的全局状态（shader / blend / color），否则会污染后续所有方块的绘制。
 */
public abstract class TriDrawLayer{
    /** 该层在链中的执行相位。 */
    public final TriDrawPhase phase;

    protected TriDrawLayer(TriDrawPhase phase){
        this.phase = phase;
    }

    /** 方块加载时调用：解析贴图、校验资源是否存在。默认无操作。 */
    public void load(Block block){
    }

    /** 主绘制入口；只有声明为 UNDER / BODY / OVERLAY 的层会被调用。 */
    public void draw(Building build){
    }

    /** 发光绘制入口；只有声明为 {@link TriDrawPhase#LIGHT} 的层会被调用。 */
    public void drawLight(Building build){
    }

    /** 蓝图预览入口；只有声明为 {@link TriDrawPhase#PLAN} 的层会被调用。 */
    public void drawPlan(Block block, BuildPlan plan, Eachable<BuildPlan> plans){
    }

    /** 把本层需要描边的贴图追加到 out。默认不追加。 */
    public void getRegionsToOutline(Block block, Seq<TextureRegion> out){
    }

    /** UI 图标组成；返回 null 表示本层不参与图标拼合。 */
    public TextureRegion[] icons(Block block){
        return null;
    }
}
