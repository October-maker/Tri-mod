package triangle.black;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.units.StatusEntry;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;
import mindustry.world.meta.Stat;

public class GrowingDamageStatusEffect extends StatusEffect {

    // 基础伤害值
    public float baseDamage = 5f;
    // 最大伤害值
    public float maxDamage = 50f;
    // 达到最大伤害所需的时间（秒）
    public float timeToMax = 10f;
    // 用于存储每个单位的当前实际伤害值（随效果时长增长，封顶于 maxDamage）
    private static final java.util.Map<Unit, Float> currentDamages = new java.util.HashMap<>();

    public GrowingDamageStatusEffect(String name) {
        super(name);
    }

    public void update(Unit unit, StatusEntry entry) {
        // 获取当前的实际伤害值
        float currentDamage = getCurrentDamage(unit);

        // 核心优化：一旦实际伤害达到（或超过）maxDamage，锁定为 maxDamage 并停止增长
        if (currentDamage >= maxDamage) {
            // 锁死伤害值，防止浮点数精度导致的无限累加
            currentDamages.put(unit, maxDamage);

            // 直接造成最大伤害/治疗
            if (maxDamage > 0) {
                unit.damageContinuousPierce(maxDamage);
            } else if (maxDamage < 0) {
                unit.heal(-1f * maxDamage * Time.delta);
            }
        } else {
            // 未达到上限时，应用当前伤害
            if (currentDamage > 0) {
                unit.damageContinuousPierce(currentDamage);
            } else if (currentDamage < 0) {
                unit.heal(-1f * currentDamage * Time.delta);
            }

            // 线性增长：在 timeToMax 秒内从 baseDamage 增长到 maxDamage，按帧率缩放
            float step = (maxDamage - baseDamage) / (timeToMax * 60f) * Time.delta;
            currentDamages.put(unit, currentDamage + step);
        }

        // 处理特效
        if(effect != Fx.none && Mathf.chanceDelta(effectChance)){
            float range = (float)(Math.random() - 0.5) * unit.type.hitSize;
            float rangeY = (float)(Math.random() - 0.5) * unit.type.hitSize;
            effect.at(unit.x + range, unit.y + rangeY, 0, color, unit);
        }
    }

    private float getCurrentDamage(Unit unit) {
        return currentDamages.getOrDefault(unit, baseDamage);
    }

    private void resetCurrentDamage(Unit unit) {
        currentDamages.remove(unit);
    }

    @Override
    public void onRemoved(Unit unit) {
        // 效果移除时清理累积伤害数据，防止内存泄漏
        resetCurrentDamage(unit);
        super.onRemoved(unit);
    }

    @Override
    public void applied(Unit unit, float time, boolean extend) {
        super.applied(unit, time, extend);

        if (!extend) {
            // 首次应用时，从基础伤害开始
            currentDamages.put(unit, baseDamage);
        }
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.damage, baseDamage * 60f + " [lightgray]----" + timeToMax + "s--->[] " + maxDamage * 60f + " + " );
    }
}
