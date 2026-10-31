package triangle.black.ArmorBullet;

import arc.graphics.Color;
import triangle.black.TriEffect;

/**
 * HESH（碎甲弹）专用弹药类型。
 *
 * <p>在构造时直接设置默认的命中/空爆效果，避免依赖内容加载后的全局扫描器：
 * <ul>
 *   <li>命中目标：只有后半球的短线条；</li>
 *   <li>未命中空爆：前半球长线条 + 后半球短线条。</li>
 * </ul>
 *
 * <p>默认颜色为橙色。可通过 {@link #hitColor}、{@link #hitEffect}、
 * {@link #despawnEffect} 覆盖；JSON 中也可以直接覆盖这些 public 字段。
 */
public class HeshBulletType extends ArmorReactiveBulletType{

    /** 默认小口径效果尺寸。 */
    public static final float DEFAULT_SMALL_SIZE = 26f;
    /** 默认大口径效果尺寸。 */
    public static final float DEFAULT_LARGE_SIZE = 52f;

    public HeshBulletType(float baseDamage, float effectSize){
        this(baseDamage, effectSize, TriEffect.DEFAULT_HESH_COLOR);
    }

    @SuppressWarnings("this-escape")
    public HeshBulletType(float baseDamage, float effectSize, Color color){
        super(baseDamage);
        applyHeshEffect(effectSize, color);
    }

    /** 供 JSON 解析使用的默认小口径版本。 */
    public HeshBulletType(){
        this(200f, DEFAULT_SMALL_SIZE);
    }

    /** 设置命中与空爆效果；可在子类/JSON 字段覆盖前调用。 */
    public void applyHeshEffect(float effectSize, Color color){
        Color effectColor = color == null ? TriEffect.DEFAULT_HESH_COLOR : color;
        hitColor = effectColor;
        hitEffect = TriEffect.piercingHit(effectSize, effectColor);
        despawnEffect = TriEffect.piercingAirburst(effectSize, effectColor);
    }
}
