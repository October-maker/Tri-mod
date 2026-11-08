package triangle.black;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.units.StatusEntry;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;
import mindustry.world.meta.Stat;

/**
 * 伤害随时间线性增长的持续伤害状态。
 *
 * <p>成长计时使用 {@link StatusEntry#damageTime}，每次状态被施加时由原版状态系统
 * 初始化为 0；这样不需要额外维护 Unit→伤害值的全局表，也不会因为单位复用或
 * 状态重复施加而读到旧进度。
 *
 * <p>伤害按 tick 结算（引擎内部再乘 {@code Time.delta}），统计页显示时按 ×60
 * 换算为每秒值。
 */
public class GrowingDamageStatusEffect extends StatusEffect {

    /** 初始伤害（每 tick）。 */
    public float baseDamage = 5f;
    /** 伤害上限（每 tick）。 */
    public float maxDamage = 50f;
    /** 从初始伤害增长到上限所需时间（秒）。 */
    public float timeToMax = 10f;

    public GrowingDamageStatusEffect(String name){
        super(name);
    }

    /**
     * 状态每帧更新入口。
     *
     * <p>v160.7 实际调用 {@code update(Unit, StatusEntry)}；这里使用
     * {@code StatusEntry.damageTime} 作为成长计时
     * 把状态生命周期数据放在 StatusEntry 上，避免全局静态缓存。
     */
    @Override
    public void update(Unit unit, StatusEntry entry){
        float durationTicks = Math.max(timeToMax * 60f, 0.0001f);
        entry.damageTime += Time.delta;

        float progress = Mathf.clamp(entry.damageTime / durationTicks);
        float currentDamage = baseDamage + (maxDamage - baseDamage) * progress;

        if(currentDamage > 0f){
            unit.damageContinuousPierce(currentDamage);
        }else if(currentDamage < 0f){
            unit.heal(-currentDamage * Time.delta);
        }

        if(effect != Fx.none && Mathf.chanceDelta(effectChance)){
            float half = unit.type.hitSize * 0.5f;
            effect.at(unit.x + Mathf.range(half), unit.y + Mathf.range(half), 0, color, unit);
        }
    }

    @Override
    public void setStats(){
        super.setStats();
        // 内部按每 tick 结算，这里 ×60 换算为每秒伤害后展示。
        stats.add(Stat.damage, baseDamage * 60 + " [lightgray]----" + timeToMax + "s--->[] " + maxDamage * 60);
    }
}
