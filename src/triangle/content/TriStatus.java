package triangle.content;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.entities.effect.MultiEffect;
import mindustry.type.StatusEffect;
import triangle.black.GrowingDamageStatusEffect;

import static mindustry.content.StatusEffects.freezing;
import static mindustry.content.StatusEffects.wet;

/**
 * 三角模组的状态效果注册表。
 *
 * <p>包含两类状态：
 * <ul>
 *   <li>{@link #AcidAttack}：自定义“成长伤害”酸蚀，伤害随持续时间从 {@code baseDamage} 线性增长到
 *       {@code maxDamage}，同时降低移速与生命上限；</li>
 *   <li>{@link #corrosion}：通用的腐蚀标记，伤害等具体效果由施加它的弹药/方块决定。</li>
 * </ul>
 */
public class TriStatus {

    /** 酸蚀：伤害随时间线性增长，并与“潮湿”“冻结”互斥。 */
    public static GrowingDamageStatusEffect AcidAttack;
    /** 腐蚀：通用减益标记（数值由来源内容配置）。 */
    public static StatusEffect corrosion;

    /** 创建状态效果（由 {@code triangle.loadContent()} 调用）。 */
    public static void load(){
        AcidAttack = new GrowingDamageStatusEffect("AcidAttack") {{
            color = Color.valueOf("ffc455");
            baseDamage = 3.2f;
            maxDamage = 24f;
            timeToMax = 10f;
            effect = new MultiEffect(Fx.burning,Fx.smoke);
            transitionDamage = 14f;
            speedMultiplier = 0.5f;
            healthMultiplier = 0.7f;
            init(() -> opposite(wet, freezing));
        }};
        corrosion = new StatusEffect("corrosion"){{}};
    }
}
