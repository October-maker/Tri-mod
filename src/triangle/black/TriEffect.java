package triangle.black;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.effect.MultiEffect;
import mindustry.graphics.Drawf;

/**
 * 破甲类弹药专用效果。
 *
 * <p>两种基础效果：
 * <ul>
 *   <li>{@link #piercingAirburst(float, Color)}：前后半球的随机线条，用于空爆；</li>
 *   <li>{@link #piercingHit(float, Color)}：后半球随机短线条，用于命中目标。</li>
 * </ul>
 *
 * <p>线条数量、角度和长度使用效果实例 id 作为随机种子，因此同一个效果实例
 * 每帧形状稳定，不同命中点形状不同。效果使用 additive 混合产生发光感。
 */
public final class TriEffect{

    /** 破甲弹药默认橙色；可在调用有 Color 参数的重载时覆盖。 */
    public static final Color DEFAULT_HESH_COLOR = Color.valueOf("FF3300");

    /** 小口径效果尺寸。 */
    public static final float DEFAULT_SMALL_SIZE = 26f;
    /** 大口径效果尺寸。 */
    public static final float DEFAULT_LARGE_SIZE = 52f;

    private TriEffect(){}

    /**
     * 空爆效果：前后半球随机数量、角度、长度的线条，前半球长、后半球短。
     *
     * @param size 整体尺寸；建议 24~64
     */
    public static Effect piercingAirburst(float size){
        return piercingAirburst(size, DEFAULT_HESH_COLOR);
    }

    /**
     * 空爆效果，并指定线条颜色。
     *
     * @param size 整体尺寸
     * @param color 线条颜色；null 时使用默认亮黄色
     */
    public static Effect piercingAirburst(float size, Color color){
        Color effectColor = color == null ? DEFAULT_HESH_COLOR : color;
        Effect lines = new Effect(22f, size * 2.2f, e -> {
            float alpha = 1f - e.fin();
            if(alpha <= 0f) return;

            long seed = e.id;
            int count = Mathf.randomSeed(seed, 12, 22);
            float thickness = 1.35f - 0.6f * e.fin(); // 从始至终递减
            float width = Math.max(1.5f, size * 0.12f) * thickness;

            // 使用原版光照发光，替代额外的宽线光晕绘制。
            Drawf.light(e.x, e.y, size * 1.8f, effectColor, alpha * 0.8f);
            Draw.blend(Blending.additive);
            Draw.color(effectColor.r, effectColor.g, effectColor.b, alpha);
            Lines.stroke(width);
            drawBurstLines(e, seed, count, size);

            Draw.blend(Blending.normal);
            Draw.reset();
        }).layer(110f);

        // 叠加原版圆形冲击波。
        return new MultiEffect(Fx.shockwave, lines);
    }

    /**
     * 命中效果：仅后半球随机短线条。
     *
     * @param size 整体尺寸；建议 20~48
     */
    public static Effect piercingHit(float size){
        return piercingHit(size, DEFAULT_HESH_COLOR);
    }

    /**
     * 命中效果，并指定线条颜色。
     *
     * @param size 整体尺寸
     * @param color 线条颜色；null 时使用默认亮黄色
     */
    public static Effect piercingHit(float size, Color color){
        Color effectColor = color == null ? DEFAULT_HESH_COLOR : color;
        Effect lines = new Effect(15f, size * 1.8f, e -> {
            float alpha = 1f - e.fin();
            if(alpha <= 0f) return;

            long seed = e.id;
            int count = Mathf.randomSeed(seed, 8, 15);
            float thickness = 1.35f - 1.0f * e.fin(); // 从始至终递减
            float width = Math.max(1.5f, size * 0.13f) * thickness;

            Drawf.light(e.x, e.y, size * 1.6f, effectColor, alpha * 0.8f);
            Draw.blend(Blending.additive);
            Draw.color(effectColor.r, effectColor.g, effectColor.b, alpha);
            Lines.stroke(width);
            drawBackHalf(e, seed, count, size);

            Draw.blend(Blending.normal);
            Draw.reset();
        }).layer(110f);

        // 叠加原版圆形冲击波。
        return new MultiEffect(Fx.shockwave, lines);
    }

    /** 前/后半球随机均匀分布。 */
    private static void drawBurstLines(Effect.EffectContainer e, long seed, int count, float size){
        float outward = size * 1.4f * Mathf.sqrt(e.fin());
        float backOutward = outward * 0.45f;
        float lengthFactor = 1.15f - 0.65f * e.fin(); // 初始长，末段短
        for(int i = 0; i < count; i++){
            long lineSeed = seed * 1103515245L + i * 12345L;
            float distanceFactor = Mathf.randomSeed(lineSeed + 53, 0.65f, 1.35f);
            boolean front = Mathf.randomSeed(lineSeed, 0, 1) == 1;
            float angle = front
                ? e.rotation - 90f + Mathf.randomSeed(lineSeed + 17, 0f, 180f)
                : e.rotation + 90f + Mathf.randomSeed(lineSeed + 17, 0f, 180f);

            float length;
            if(front){
                length = size * Mathf.randomSeed(lineSeed + 31, 0.56f, 0.78f);
            }else{
                length = size * Mathf.randomSeed(lineSeed + 31, 0.24f, 0.42f);
            }

            float lineOutward = (front ? outward : backOutward) * distanceFactor;
            drawOutwardLine(e, angle, lineOutward, length * lengthFactor);
        }
    }

    /** 仅后半球随机均匀分布。 */
    private static void drawBackHalf(Effect.EffectContainer e, long seed, int count, float size){
        float outward = size * 1.4f * Mathf.sqrt(e.fin()) * 0.45f;
        float lengthFactor = 1.15f - 0.65f * e.fin();
        for(int i = 0; i < count; i++){
            long lineSeed = seed * 1103515245L + i * 12345L;
            float distanceFactor = Mathf.randomSeed(lineSeed + 53, 0.65f, 1.35f);
            float angle = e.rotation + 90f + Mathf.randomSeed(lineSeed + 17, 0f, 180f);
            float length = size * Mathf.randomSeed(lineSeed + 31, 0.27f, 0.48f);
            drawOutwardLine(e, angle, outward * distanceFactor, length * lengthFactor);
        }
    }

    /** 从中心向外偏移后绘制短粗线段。 */
    private static void drawOutwardLine(Effect.EffectContainer e, float angle, float outward, float length){
        float dx = Mathf.cosDeg(angle);
        float dy = Mathf.sinDeg(angle);
        float sx = e.x + dx * outward;
        float sy = e.y + dy * outward;
        Lines.line(sx, sy, sx + dx * length, sy + dy * length);
    }

    // 供 JSON / 静态引用使用的固定尺寸版本。
    public static final Effect piercingAirburstSmall = piercingAirburst(DEFAULT_SMALL_SIZE);
    public static final Effect piercingAirburstLarge = piercingAirburst(DEFAULT_LARGE_SIZE);
}
