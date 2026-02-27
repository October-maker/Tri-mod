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

        // 计算当前应该应用的伤害值
        float currentDamage = calculateCurrentDamage(accumulatedTime);

        // 直接应用动态计算出的伤害
        if (currentDamage > 0) {
            unit.damageContinuousPierce(currentDamage);
        } else if (currentDamage < 0) { // 如果是治疗效果
            unit.heal(-1f * currentDamage * Time.delta);
        }

        // 累积时间增加
        addAccumulatedTime(unit, Time.delta / 60f); // Time.delta是帧时间，除以60转为秒


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
        float currentTime = getAccumulatedTime(unit);
        float newTime = currentTime + deltaTime;
        accumulatedTimes.put(unit, newTime);
    }

    private void resetAccumulatedTime(Unit unit) {
        accumulatedTimes.remove(unit);
    }

    @Override
    public void onRemoved(Unit unit) {
        // 效果移除时清理累积时间数据
        resetAccumulatedTime(unit);
        super.onRemoved(unit);
    }

    @Override
    public void applied(Unit unit, float time, boolean extend) {
        super.applied(unit, time, extend);

        if (!extend) {
            // 首次应用时，初始化累积时间为0
            resetAccumulatedTime(unit); // 确保清空旧数据
        }
        // 如果是延长效果，保持现有的累积时间
    }

    @Override
    public void setStats() {
        // 首先调用父类的 setStats 方法以保留原有功能
        super.setStats();

        // 添加我们自定义的统计信息
        // 使用 Stat.damage 作为类别，然后添加自定义描述
        stats.add(Stat.damage, baseDamage * 60f + " [lightgray]->[] " + maxDamage * 60f );
    }
}


