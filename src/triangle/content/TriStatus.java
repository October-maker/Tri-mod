package triangle.content;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.entities.effect.MultiEffect;
import mindustry.type.StatusEffect;
import triangle.black.GrowingDamageStatusEffect;

import static mindustry.content.StatusEffects.freezing;
import static mindustry.content.StatusEffects.wet;

public class TriStatus {
    public static GrowingDamageStatusEffect AcidAttack;
    public static StatusEffect corrosion;
    public static void load(){
        AcidAttack = new GrowingDamageStatusEffect("AcidAttack") {{
            color = Color.valueOf("ffc455");
            baseDamage = 3.2f;
            maxDamage = 24f;
            timeToMax = 60f;
            effect = new MultiEffect(Fx.burning,Fx.smoke);
            transitionDamage = 14f;
            speedMultiplier = 0.5f;
            healthMultiplier = 0.7f;
            init(() -> opposite(wet, freezing));
        }};
        corrosion = new StatusEffect("corrosion"){{}};
    }
}
