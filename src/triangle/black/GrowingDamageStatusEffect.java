package triangle.black;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.units.StatusEntry;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;
import mindustry.world.meta.Stat;

public class GrowingDamageStatusEffect extends StatusEffect {

    // 基础伤害值（每秒）
    public float baseDamage = 5f;
    // 最大伤害值（每秒）
    public float maxDamage = 50f;
    // 达到最大伤害所需的时间（秒）
    public float timeToMax = 10f;
    // 存储每个单位当前的累计实际伤害值（随效果时长增长，封顶于 maxDamage）。
    // 用弱引用键：单位死亡/被移除后，条目随 GC 自动清理，避免静态强引用 Map 泄漏；
    // 引擎不池化复用单位对象（remove() 仅置空实体槽位、新单位是新对象），因此不存在“新单位读到旧值”的串值问题。
    private static final java.util.WeakHashMap<Unit, Float> currentDamages = new java.util.WeakHashMap<>();

    public GrowingDamageStatusEffect(String name) {
        super(name);
    }

    // 覆盖引擎实际调用的签名 update(Unit, StatusEntry)。
    // 重要：游戏运行版本为 Mindustry 160.7，其 StatusEffect 的 update 签名为 (Unit, StatusEntry)；
    // 此前按 v150.1 引擎写成 update(Unit, float) 在 160.7 中是无效果的“重载”（引擎从不调用），导致成长伤害从未生效。
    // 注意：不能加 @Override —— build.gradle 仍以 v150.1 编译，该签名在 v150.1 中只是重载，加 @Override 会编译失败；
    // 运行时虚分派会正确调用本方法（覆盖基类）。
    public void update(Unit unit, StatusEntry entry) {
        // 获取当前的实际伤害值（首次为 baseDamage）
        float currentDamage = getCurrentDamage(unit);

        // 一旦实际伤害达到（或超过）maxDamage，锁定为 maxDamage 并停止增长
        if (currentDamage >= maxDamage) {
            currentDamages.put(unit, maxDamage); // 锁死，防止浮点累加导致的微小溢出

            if (maxDamage > 0f) {
                unit.damageContinuousPierce(maxDamage); // 入参为“每秒伤害”，引擎内部会乘 Time.delta
            } else if (maxDamage < 0f) {
                unit.heal(-maxDamage * Time.delta);
            }
        } else {
            if (currentDamage > 0f) {
                unit.damageContinuousPierce(currentDamage);
            } else if (currentDamage < 0f) {
                unit.heal(-currentDamage * Time.delta);
            }

            // 线性增长：在 timeToMax 秒内从 baseDamage 增长到 maxDamage，按帧率缩放
            float step = (maxDamage - baseDamage) / (timeToMax * 60f) * Time.delta;
            currentDamages.put(unit, currentDamage + step);
        }

        // 处理特效
        if (effect != Fx.none && Mathf.chanceDelta(effectChance)) {
            float range = (float)(Math.random() - 0.5) * unit.type.hitSize;
            float rangeY = (float)(Math.random() - 0.5) * unit.type.hitSize;
            effect.at(unit.x + range, unit.y + rangeY, 0, color, unit);
        }
    }

    private float getCurrentDamage(Unit unit) {
        return currentDamages.getOrDefault(unit, baseDamage);
    }

    @Override
    public void onRemoved(Unit unit) {
        // 效果移除时清理（防御性；即便此处未触发，弱引用表也会随单位回收自动清理）
        currentDamages.remove(unit);
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
        // 伤害为“每秒”口径（damageContinuousPierce 入参为每秒），直接显示 baseDamage → maxDamage，不再乘 60
        stats.add(Stat.damage, baseDamage * 60 + " [lightgray]----" + timeToMax + "s--->[] " + maxDamage * 60);
    }
}
