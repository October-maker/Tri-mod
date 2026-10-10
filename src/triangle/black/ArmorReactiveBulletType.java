package triangle.black;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;

/**
 * 护甲反应型弹药。
 *
 * <p>实际伤害 = baseDamage + damageChangeRate × f(armor)，其中 f(armor) = -ln(armor / baseArmor)：
 * <ul>
 *   <li>装甲越小，加值越高；</li>
 *   <li>armor == baseArmor 时 f = 0（加值为零的临界点）；</li>
 *   <li>armor &lt; baseArmor 时为正加值（伤害提升），armor &gt; baseArmor 时为负加值（伤害降低）；</li>
 *   <li>damageChangeRate 为伤害随装甲变化的倍率：0=恒定 baseDamage，1=原始斜率，越大变化越剧烈。</li>
 * </ul>
 *
 * <p>防溢出处理：
 * <ul>
 *   <li>armor &lt;= 0 被钳制到严格为正的下限，杜绝 -ln(0)=+∞ 与 ln(负数)=NaN；</li>
 *   <li>正向加值可设上限 maxBonus，限制极端低甲时的数值；</li>
 *   <li>总伤害被钳制不低于 minTotalDamage，防止高护甲负加值把伤害变成“治疗”。</li>
 * </ul>
 *
 *       护甲反应型弹药示例：伤害 = baseDamage + damageChangeRate × f(armor)，f(armor) = -ln(armor / baseArmor)。
 *       装甲低于 baseArmor 时伤害提升、高于 baseArmor 时伤害降低；armor == baseArmor 时加值为 0。
 *       damageChangeRate 控制伤害随装甲的变化倍率（0=恒定，1=默认，越大变化越剧烈）。
 *
 */

public class ArmorReactiveBulletType extends BasicBulletType {

    /** 基础伤害（不含护甲加值）。 */
    public float baseDamage;
    /** 基准装甲：目标装甲低于此值→伤害提升，高于此值→伤害降低，等于此值→加值为 0。 */
    public float baseArmor = 1f;
    /** 伤害随装甲变化的倍率：0=恒定 baseDamage，1=原始斜率，越大变化越剧烈。 */
    public float damageChangeRate = 1f;
    /** 装甲计算下限：低于此值按此值计算，防止 ln(&lt;=0) 无定义。 */
    public float minArmor = 0.1f;
    /** 硬性安全下限：即使 minArmor 配置不当，也保证 ln 参数严格为正。 */
    public float safeFloor = 0.001f;
    /** 正向加值上限（&gt;0 生效，0 表示不限）。 */
    public float maxBonus = 0f;
    /** 总伤害下限，防止高护甲负加值使总伤害为负（避免变成治疗/溢出）。 */
    public float minTotalDamage = 0f;

    public ArmorReactiveBulletType(float baseDamage){
        this.baseDamage = baseDamage;
        this.damage = baseDamage; // 供统计/显示使用；实际结算以 hitEntity/hitTile 为准
        this.speed = 5f;
        this.lifetime = 55f;
        this.hitSize = 4f;
        this.collides = true;
        this.collidesGround = true;
        this.collidesAir = true;
        // 护甲已由公式计入，结算用无视护甲的伤害，避免被目标护甲二次减免
        this.pierceArmor = true;
    }

    /**
     * 无参构造器：供 JSON 内容解析使用。
     * 引擎(mindustry.mod.ContentParser)解析 JSON 子弹时通过 Class.getDeclaredConstructor()
     * 以无参构造实例化，再反射填充 public 字段；故必须提供无参构造。
     */
    public ArmorReactiveBulletType(){
        this(5f);
    }

    /** f(armor) = damageChangeRate × (-ln(armor / baseArmor))，带防溢出钳制。armor == baseArmor 时返回 0。 */
    public float armorBonus(float armor){
        float a = Math.max(armor, minArmor);
        a = Math.max(a, safeFloor); // 绝对保证 ln 参数(armor)严格为正
        float base = Math.max(baseArmor, safeFloor); // 防止 baseArmor<=0 时 armor/baseArmor 无定义/除零
        double bonus = -Math.log(a / base) * damageChangeRate; // 用 double 计算并乘变化倍率，避免极端值精度/溢出
        if(maxBonus > 0 && bonus > maxBonus) bonus = maxBonus;
        return (float)bonus;
    }

    /** 对指定护甲的实际总伤害 = baseDamage + f(armor)，已钳制到不低于 minTotalDamage。 */
    public float totalDamage(float armor){
        float total = baseDamage + armorBonus(armor);
        return Math.max(total, minTotalDamage);
    }

    private float armorOf(Hitboxc entity){
        if(entity instanceof Unit unit) return unit.armor();
        if(entity instanceof Building building) return building.block.armor;
        return 0f; // 未知目标按 0 甲处理，由 safeFloor 兜底，不会溢出
    }

    @Override
    public void hitEntity(Bullet b, Hitboxc entity, float health){
        // 实体（单位/建筑）命中：结算自定义伤害；弹头移除/穿透由引擎 Bullet.collision 处理
        if(entity instanceof Healthc h){
            float dmg = totalDamage(armorOf(entity));
            if(dmg > 0) h.damagePierce(dmg);
        }
    }

    @Override
    public void hitTile(Bullet b, Building build, float x, float y, float health, boolean apply){
        // 与默认 hitTile 一致的门控：仅对敌对建筑且 apply 时结算
        if(build.team != b.team && apply){
            hit(b, x, y); // 命中特效/音效
            float dmg = totalDamage(build.block.armor);
            if(dmg > 0) build.damagePierce(dmg);
        }
    }

    @Override
    public void draw(Bullet b){
        // 无贴图时用彩色圆形兜底，避免空 region 渲染异常
        if(frontRegion != null){
            super.draw(b);
        }else{
            Draw.color(hitColor == null ? Color.white : hitColor);
            Fill.circle(b.x, b.y, Math.max(hitSize, 2f));
            Draw.reset();
        }
    }
}
