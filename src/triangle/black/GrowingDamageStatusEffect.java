package triangle.black;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.units.StatusEntry;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

public class GrowingDamageStatusEffect extends StatusEffect {

    // 基础伤害值
    public float baseDamage = 5f;
    // 最大伤害值
    public float maxDamage = 50f;
    // 达到最大伤害所需的时间（秒）
    public float timeToMax = 10f;
    // 用于存储每个单位的累积时间
    private static final java.util.Map<Unit, Float> accumulatedTimes = new java.util.HashMap<>();

    public GrowingDamageStatusEffect(String name) {
        super(name);
    }

    public void update(Unit unit, StatusEntry entry) {
        // 获取累积的应用时间
        float accumulatedTime = getAccumulatedTime(unit);

        // 核心优化：如果已经达到或超过最大时间，直接应用最大伤害并跳过后续累加
        if (accumulatedTime >= timeToMax) {
            // 锁死时间，防止浮点数精度导致的无限累加
            accumulatedTimes.put(unit, timeToMax);

            // 直接造成最大伤害/治疗
            if (maxDamage > 0) {
                unit.damageContinuousPierce(maxDamage);
            } else if (maxDamage < 0) {
                unit.heal(-1f * maxDamage * Time.delta);
            }
        } else {
            // 未达到上限时，计算伤害
            float currentDamage = calculateCurrentDamage(accumulatedTime);
            if (currentDamage > 0) {
                unit.damageContinuousPierce(currentDamage);
            } else if (currentDamage < 0) {
                unit.heal(-1f * currentDamage * Time.delta);
            }

            // 累积时间增加
            addAccumulatedTime(unit, Time.delta / 60f);
        }

        // 处理特效
        if(effect != Fx.none && Mathf.chanceDelta(effectChance)){
            float range = (float)(Math.random() - 0.5) * unit.type.hitSize;
            float rangeY = (float)(Math.random() - 0.5) * unit.type.hitSize;
            effect.at(unit.x + range, unit.y + rangeY, 0, color, unit);
        }
    }

    private float calculateCurrentDamage(float time) {
        // 计算当前时间比例（0 到 1）
        float progress = Math.min(time / timeToMax, 1f);
        // 根据时间比例线性插值计算当前伤害
        return baseDamage + (maxDamage - baseDamage) * progress;
    }

    private float getAccumulatedTime(Unit unit) {
        return accumulatedTimes.getOrDefault(unit, 0f);
    }

    private void addAccumulatedTime(Unit unit, float deltaTime) {
        // 使用 Java 8 的 merge 方法，一行代码安全地完成“获取并累加”操作
        accumulatedTimes.merge(unit, deltaTime, Float::sum);
    }

    private void resetAccumulatedTime(Unit unit) {
        accumulatedTimes.remove(unit);
    }

    @Override
    public void onRemoved(Unit unit) {
        // 效果移除时清理累积时间数据，防止内存泄漏
        resetAccumulatedTime(unit);
        super.onRemoved(unit);
    }

    @Override
    public void applied(Unit unit, float time, boolean extend) {
        super.applied(unit, time, extend);

        if (!extend) {
            // 首次应用时，初始化累积时间为0
            resetAccumulatedTime(unit);
        }
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.damage, baseDamage * 60f + " [lightgray]----" + timeToMax + "s--->[] " + maxDamage * 60f + " + " );
    }
}