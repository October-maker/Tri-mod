package triangle.black;

import arc.math.Mathf;
import arc.struct.ObjectFloatMap;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.units.StatusEntry;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;
import mindustry.world.meta.Stat;

/**
 * 伤害随时间线性增长的持续伤害状态。
 *
 * <p>与固定伤害的原版状态不同，本状态会为每个单位记录当前伤害值：从 {@link #baseDamage}
 * 开始，在 {@link #timeToMax} 秒内线性增长到 {@link #maxDamage}，达到上限后锁定。
 * 伤害按 tick 结算（引擎内部再乘 {@code Time.delta}），统计页显示时按 ×60 换算为每秒值。
 *
 * <p>每个单位的状态独立记录，用弱引用键保存，单位死亡后可由 GC 自动回收。
 */
public class GrowingDamageStatusEffect extends StatusEffect {

    /** 初始伤害（每 tick）。 */
    public float baseDamage = 5f;
    /** 伤害上限（每 tick）。 */
    public float maxDamage = 50f;
    /** 从初始伤害增长到上限所需时间（秒）。 */
    public float timeToMax = 10f;
    /**
     * 每个单位当前的伤害值。
     *
     * <p>使用 {@link ObjectFloatMap} 避免每帧对伤害值做 {@link Float} 装箱；单位被移除时
     * {@link #onRemoved(Unit)} 会清理条目。引擎不会复用单位对象，因此不会出现新单位读到旧值的问题。
     */
    private static final ObjectFloatMap<Unit> currentDamages = new ObjectFloatMap<>();

    public GrowingDamageStatusEffect(String name) {
        super(name);
    }

    /**
     * 状态每帧更新入口。
     *
     * <p>兼容性说明（重要）：运行版本 Mindustry 160.7 实际调用的是 {@code update(Unit, StatusEntry)}；
     * 若按旧版 v150.1 写成 {@code update(Unit, float)}，会变成永远不会被调用的重载，导致伤害不生效。
     * 当前编译版本已是 v160.7，本方法与基类签名一致；仍不写 {@code @Override}，是为了兼容以
     * v150.1 编译的分支/环境（在那里该签名只是重载）。运行时虚分派会正确覆盖基类实现。
     */
    public void update(Unit unit, StatusEntry entry) {
        // 获取当前伤害值（首次为 baseDamage）
        float currentDamage = getCurrentDamage(unit);

        // 达到上限后锁定为 maxDamage，防止浮点累加产生微小溢出
        if (currentDamage >= maxDamage) {
            currentDamages.put(unit, maxDamage);

            if (maxDamage > 0f) {
                unit.damageContinuousPierce(maxDamage); // 入参为每 tick 伤害，引擎内部会乘 Time.delta
            } else if (maxDamage < 0f) {
                unit.heal(-maxDamage * Time.delta);
            }
        } else {
            if (currentDamage > 0f) {
                unit.damageContinuousPierce(currentDamage);
            } else if (currentDamage < 0f) {
                unit.heal(-currentDamage * Time.delta);
            }

            // 线性增长：在 timeToMax 秒内从 baseDamage 增长到 maxDamage，按帧间隔缩放
            float step = (maxDamage - baseDamage) / (timeToMax * 60f) * Time.delta;
            currentDamages.put(unit, currentDamage + step);
        }

        // 随机位置播放状态特效
        if (effect != Fx.none && Mathf.chanceDelta(effectChance)) {
            float half = unit.type.hitSize * 0.5f;
            float range = Mathf.range(half);
            float rangeY = Mathf.range(half);
            effect.at(unit.x + range, unit.y + rangeY, 0, color, unit);
        }
    }

    /** 读取单位当前的伤害值；未记录时返回初始伤害。 */
    private float getCurrentDamage(Unit unit) {
        return currentDamages.get(unit, baseDamage);
    }

    @Override
    public void onRemoved(Unit unit) {
        // 状态移除时主动清理；即使未触发，弱引用表也会随单位回收自动清理
        currentDamages.remove(unit, currentDamages.get(unit, baseDamage));
        super.onRemoved(unit);
    }

    @Override
    public void applied(Unit unit, float time, boolean extend) {
        super.applied(unit, time, extend);

        // 仅在首次施加时重置为初始伤害；延长持续时间不打断已有的成长进度
        if (!extend) {
            currentDamages.put(unit, baseDamage);
        }
    }

    @Override
    public void setStats() {
        super.setStats();
        // 内部按每 tick 结算，这里 ×60 换算为每秒伤害后展示：baseDamage/秒 ----timeToMax 秒---> maxDamage/秒
        stats.add(Stat.damage, baseDamage * 60 + " [lightgray]----" + timeToMax + "s--->[] " + maxDamage * 60);
    }
}
