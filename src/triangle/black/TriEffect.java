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
 *   <li>{@link #piercingAirburst(float, Color)}：前后方向的线条（朝前 / 朝后各向左右偏摆）+ 垂直弹道的两侧外喷线条，用于空爆；</li>
 *   <li>{@link #piercingHit(float, Color)}：仅朝后方向的短线条 + 垂直弹道的两侧外喷线条，用于命中目标。</li>
 * </ul>
 *
 * <p>线条数量、角度和长度使用效果实例 id 作为随机种子，因此同一个效果实例
 * 每帧形状稳定，不同命中点形状不同。效果使用 additive 混合产生发光感。
 *
 * <h3>基准尺寸（由 size 推导，调整常量即可改基准）</h3>
 * <pre>
 * 基准线宽      = max(1.2, size * 0.085)
 * 朝前线条基准  = size * 0.68
 * 朝后线条基准  = size * 0.34
 * 侧向线条基准  = size * 0.80（更长，左右两段在中心附近接上）
 * 侧向线宽倍数  = 0.85（相对基准线宽，更细）
 * 侧向条数      = 每侧 4 条（左右分开绘制）
 * 侧向起点      = 效果原点 (0,0)，不额外外延
 * 侧向外延速度  = 0.60 ~ 1.40 倍（逐条随机，正值）
 * </pre>
 *
 * <h3>对外调控变量（都是「相对基准的倍数」，1.0 = 基准，不是绝对像素）</h3>
 * <pre>
 * lineWidthScale  = 1.0 → 实际线宽 = 基准线宽 × lineWidthScale
 * lineLengthScale = 1.0 → 实际长度 = 各方向基准长度 × lineLengthScale
 * spreadAngle     = 45f → 朝前 / 朝后各向左右偏 45°
 * sideAngleSpread = 3f → 侧向线条在垂直方向左右各偏 3° 内随机
 * </pre>
 */
public final class TriEffect{

    /** 破甲弹药默认橙色；可在调用有 Color 参数的重载时覆盖。 */
    public static final Color DEFAULT_HESH_COLOR = Color.valueOf("FF8974");

    /** 小口径效果尺寸。 */
    public static final float DEFAULT_SMALL_SIZE = 26f;
    /** 大口径效果尺寸。 */
    public static final float DEFAULT_LARGE_SIZE = 52f;

    // ---- 对外调控变量：都是相对基准的倍数（<= 0 或未指定时按 1.0 基准处理） ----

    /** 线宽倍数：实际线宽 = 基准线宽("max(1.2, size*0.085)") × 本值。1.0 = 基准，>1 更粗，<1 更细。 */
    public static float lineWidthScale = 1.0f;
    /** 长度倍数：实际长度 = 各方向基准长度(朝前 size*0.68 / 朝后 size*0.34 / 侧向 size*0.50) × 本值。 */
    public static float lineLengthScale = 1.0f;
    /** 朝前 / 朝后线条的左右偏摆角度范围（度）：45f = 线条朝前、朝后各向左右偏 45°。 */
    public static float spreadAngle = 45f;
    /** 侧向（垂直弹道）线条的发射角度随机范围（度）：3f = 在垂直方向左右各偏 3° 内随机。 */
    public static float sideAngleSpread = 3f;

    // ---- 基准推导比例（改这里即可改「基准」，再用上面的倍数微调） ----
    /** 基准线宽 / size（比旧值 0.12 更细）。 */
    private static final float WIDTH_RATIO = 0.085f;
    /** 基准线宽下限（像素）。 */
    private static final float MIN_WIDTH = 1.2f;
    /** 朝前线条基准长度 / size。 */
    private static final float FORWARD_LENGTH_RATIO = 0.68f;
    /** 朝后线条基准长度 / size（约为朝前的一半）。 */
    private static final float BACK_LENGTH_RATIO = 0.34f;
    /** 侧向线条基准长度 / size（比朝前线条更长，保证左右两段能在中间搭接）。 */
    private static final float SIDE_LENGTH_RATIO = 0.80f;
    /** 侧向线条相对基准线宽的倍数（<1 表示比主线条更细）。 */
    private static final float SIDE_WIDTH_SCALE = 0.85f;
    /** 每一侧（左 / 右）的侧向延展线条固定条数。 */
    private static final int SIDE_LINE_COUNT = 4;
    /** 侧向线条向外延伸速度的随机范围（倍数，乘在 size*0.9*sqrt(fin) 基准生长上）。 */
    private static final float SIDE_SPEED_MIN = 0.60f;
    private static final float SIDE_SPEED_MAX = 1.40f;

    private TriEffect(){}

    /**
     * 空爆效果：前后方向（各向左右偏 {@link #spreadAngle}）的线条 + 垂直于弹道的两侧外喷线条，
     * 并叠加圆形冲击波与原版核心爆炸。
     *
     * @param size 整体尺寸；建议 24~64
     */
    public static Effect piercingAirburst(float size){
        return piercingAirburst(size, DEFAULT_HESH_COLOR);
    }

    /**
     * 空爆效果，并指定线条颜色。
     *
     * @param size  整体尺寸
     * @param color 线条颜色；null 时使用默认橙色
     */
    public static Effect piercingAirburst(float size, Color color){
        return piercingAirburst(size, color, -1f, -1f);
    }

    /**
     * 空爆效果，并按实例指定线宽倍数与长度倍数（相对 size 基准的倍数，不是绝对像素）。
     *
     * @param size        整体尺寸（决定基准线宽、基准长度、线条起点外移量与光照半径）
     * @param color       线条颜色；null 时使用默认橙色
     * @param widthScale  线宽倍数；<= 0 时用 {@link #lineWidthScale}
     * @param lengthScale 长度倍数；<= 0 时用 {@link #lineLengthScale}
     */
    public static Effect piercingAirburst(float size, Color color, float widthScale, float lengthScale){
        Color effectColor = color == null ? DEFAULT_HESH_COLOR : color;

        float widthMul = widthScale > 0f ? widthScale : lineWidthScale;
        float lengthMul = lengthScale > 0f ? lengthScale : lineLengthScale;

        float baseWidth = Math.max(MIN_WIDTH, size * WIDTH_RATIO) * widthMul;
        float forwardLen = size * FORWARD_LENGTH_RATIO * lengthMul;
        float backLen = size * BACK_LENGTH_RATIO * lengthMul;
        float sideLen = size * SIDE_LENGTH_RATIO * lengthMul;

        Effect lines = new Effect(22f, size * 2.2f, e -> {
            float alpha = 1f - e.fin();
            if(alpha <= 0f) return;

            long seed = e.id;
            int count = Mathf.randomSeed(seed, 12, 22);
            float thickness = 1.0f - 0.45f * e.fin(); // 从始至终递减（比旧值更细）

            // 使用原版光照发光，替代额外的宽线光晕绘制。
            Drawf.light(e.x, e.y, size * 1.8f, effectColor, alpha * 0.8f);
            Draw.blend(Blending.additive);
            Draw.color(effectColor.r, effectColor.g, effectColor.b, alpha);

            // 第一层：朝前 / 朝后方向的线条（各自向左右偏 spreadAngle）
            Lines.stroke(baseWidth * thickness);
            drawDirectionalLines(e, seed, count, size, forwardLen, backLen);

            // 第二层：垂直于弹道方向（左 / 右）外喷的线条，左右各 4 条，更细更长
            Lines.stroke(baseWidth * SIDE_WIDTH_SCALE * thickness);
            drawSideLines(e, seed, size, sideLen);

            Draw.blend(Blending.normal);
            Draw.reset();
        }).layer(110f);

        // 叠加原版圆形冲击波与原版核心爆炸。
        return new MultiEffect(Fx.shockwave, lines);
    }

    /**
     * 命中效果：仅朝后方向（向左右偏 {@link #spreadAngle}）的短线条 + 垂直弹道的两侧外喷线条，
     * 并叠加圆形冲击波与原版核心爆炸。
     *
     * @param size 整体尺寸；建议 20~48
     */
    public static Effect piercingHit(float size){
        return piercingHit(size, DEFAULT_HESH_COLOR);
    }

    /**
     * 命中效果，并指定线条颜色。
     *
     * @param size  整体尺寸
     * @param color 线条颜色；null 时使用默认橙色
     */
    public static Effect piercingHit(float size, Color color){
        return piercingHit(size, color, -1f, -1f);
    }

    /**
     * 命中效果，并按实例指定线宽倍数与长度倍数（相对 size 基准的倍数，不是绝对像素）。
     *
     * @param size        整体尺寸
     * @param color       线条颜色；null 时使用默认橙色
     * @param widthScale  线宽倍数；<= 0 时用 {@link #lineWidthScale}
     * @param lengthScale 长度倍数；<= 0 时用 {@link #lineLengthScale}
     */
    public static Effect piercingHit(float size, Color color, float widthScale, float lengthScale){
        Color effectColor = color == null ? DEFAULT_HESH_COLOR : color;

        float widthMul = widthScale > 0f ? widthScale : lineWidthScale;
        float lengthMul = lengthScale > 0f ? lengthScale : lineLengthScale;

        float baseWidth = Math.max(MIN_WIDTH, size * WIDTH_RATIO) * widthMul;
        // 命中版后向线略长于空爆后向线（+0.10×size），以便命中看起来更实
        float backLen = (size * BACK_LENGTH_RATIO + size * 0.10f) * lengthMul;
        float sideLen = size * SIDE_LENGTH_RATIO * lengthMul;

        Effect lines = new Effect(15f, size * 1.8f, e -> {
            float alpha = 1f - e.fin();
            if(alpha <= 0f) return;

            long seed = e.id;
            int count = Mathf.randomSeed(seed, 8, 15);
            float thickness = 1.0f - 0.45f * e.fin(); // 与空爆（未命中）效果保持一致，后向线宽相同

            Drawf.light(e.x, e.y, size * 1.6f, effectColor, alpha * 0.8f);
            Draw.blend(Blending.additive);
            Draw.color(effectColor.r, effectColor.g, effectColor.b, alpha);

            // 第一层：朝后方向的线条（向左右偏 spreadAngle）
            Lines.stroke(baseWidth * thickness);
            drawBackLines(e, seed, count, size, backLen);

            // 第二层：垂直于弹道方向（左 / 右）外喷的线条，左右各 4 条，更细更长
            Lines.stroke(baseWidth * SIDE_WIDTH_SCALE * thickness);
            drawSideLines(e, seed, size, sideLen);

            Draw.blend(Blending.normal);
            Draw.reset();
        }).layer(110f);

        // 叠加原版圆形冲击波与原版核心爆炸。
        return new MultiEffect(Fx.shockwave, lines);
    }

    /** 朝前 / 朝后两个方向簇：各自在本方向两侧 ±{@link #spreadAngle} 内随机偏摆。 */
    private static void drawDirectionalLines(Effect.EffectContainer e, long seed, int count, float size, float forwardLen, float backLen){
        float forwardOutward = size * 1.4f * Mathf.sqrt(e.fin());
        float backOutward = forwardOutward * 0.45f;
        float lengthFactor = 1.15f - 0.65f * e.fin(); // 初始长，末段短
        for(int i = 0; i < count; i++){
            long lineSeed = seed * 1103515245L + i * 12345L;
            float distanceFactor = Mathf.randomSeed(lineSeed + 53, 0.65f, 1.35f);
            boolean front = Mathf.randomSeed(lineSeed, 0, 1) == 1;

            // 基准方向：朝前 = rotation，朝后 = rotation + 180；再叠加 ±spreadAngle 的左右偏摆
            float baseAngle = front ? e.rotation : e.rotation + 180f;
            float angle = baseAngle + Mathf.randomSeed(lineSeed + 17, -spreadAngle, spreadAngle);

            float length = (front ? forwardLen : backLen) * Mathf.randomSeed(lineSeed + 31, 0.85f, 1.15f);
            float lineOutward = (front ? forwardOutward : backOutward) * distanceFactor;
            drawOutwardLine(e, angle, lineOutward, length * lengthFactor);
        }
    }

    /** 仅朝后方向簇：向左右偏 {@link #spreadAngle} 内随机分布。 */
    private static void drawBackLines(Effect.EffectContainer e, long seed, int count, float size, float backLen){
        float outward = size * 1.4f * Mathf.sqrt(e.fin()) * 0.45f;
        float lengthFactor = 1.15f - 0.65f * e.fin();
        for(int i = 0; i < count; i++){
            long lineSeed = seed * 1103515245L + i * 12345L;
            float distanceFactor = Mathf.randomSeed(lineSeed + 53, 0.65f, 1.35f);
            float angle = e.rotation + 180f + Mathf.randomSeed(lineSeed + 17, -spreadAngle, spreadAngle);
            float length = backLen * Mathf.randomSeed(lineSeed + 31, 0.85f, 1.15f);
            drawOutwardLine(e, angle, outward * distanceFactor, length * lengthFactor);
        }
    }

    /**
     * 垂直于弹道方向外喷的线条总入口：左右两侧使用两段独立代码分别绘制，
     * 每侧固定 {@link #SIDE_LINE_COUNT} 条。
     */
    private static void drawSideLines(Effect.EffectContainer e, long seed, float size, float sideLen){
        drawSideLinesLeft(e, seed, size, sideLen);
        drawSideLinesRight(e, seed, size, sideLen);
    }

    /** 左侧（弹道方向 +90°）的 {@link #SIDE_LINE_COUNT} 条延展线。 */
    private static void drawSideLinesLeft(Effect.EffectContainer e, long seed, float size, float sideLen){
        for(int i = 0; i < SIDE_LINE_COUNT; i++){
            long lineSeed = seed * 1073741789L + i * 7919L;
            drawSingleSideLine(e, lineSeed, size, sideLen, 90f);
        }
    }

    /** 右侧（弹道方向 -90°）的 {@link #SIDE_LINE_COUNT} 条延展线。 */
    private static void drawSideLinesRight(Effect.EffectContainer e, long seed, float size, float sideLen){
        for(int i = 0; i < SIDE_LINE_COUNT; i++){
            long lineSeed = seed * 1073741789L + 577L + i * 7919L;
            drawSingleSideLine(e, lineSeed, size, sideLen, -90f);
        }
    }

    /**
     * 绘制单条侧向延展线。
     *
     * <p>角度：以垂直弹道方向（left=true 为 +90°、false 为 -90°）为基准，
     * 叠加 ±{@link #sideAngleSpread} 的小角度随机；
     * 向外延伸速度逐条随机（正值）；绘制起点固定为效果原点 (0, 0)。
     *
     * @param baseAngle 该侧的基准角度（+90° = 左，-90° = 右）
     */
    private static void drawSingleSideLine(Effect.EffectContainer e, long lineSeed, float size, float sideLen, float baseAngle){
        float lengthFactor = 1.15f - 0.65f * e.fin();
        float angle = baseAngle + e.rotation
            + Mathf.randomSeed(lineSeed + 11, -sideAngleSpread, sideAngleSpread);

        // 向外延伸速度：逐条随机（正值），乘在 size*0.9*sqrt(fin) 的基准生长上
        float speedFactor = Mathf.randomSeed(lineSeed + 71, SIDE_SPEED_MIN, SIDE_SPEED_MAX);

        float outward = size * 0.9f * Mathf.sqrt(e.fin()) * speedFactor;
        float length = sideLen * Mathf.randomSeed(lineSeed + 31, 0.85f, 1.15f) * lengthFactor;

        // 绘制起点固定为效果原点（0），从原点向外延伸
        float dx = Mathf.cosDeg(angle);
        float dy = Mathf.sinDeg(angle);
        Lines.line(e.x, e.y,
            e.x + dx * (outward + length), e.y + dy * (outward + length));
    }

    /** 从中心向外偏移后绘制短线段。 */
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
