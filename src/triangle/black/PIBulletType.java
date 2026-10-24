package triangle.black;

import arc.audio.Sound;
import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.InterceptorBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;

/**
 * 近炸引信拦截弹（Proximity Interceptor）。
 *
 * <p>旧类名：{@code ProximityInterceptorBulletType}。为精简命名改为 {@code PIBulletType}；
 * 如旧 JSON 仍引用旧名称，可在 {@code triangle.java} 的 ClassMap 中注册别名。
 *
 * <p>在 {@link InterceptorBulletType} 的弹道碰撞基础上增加“近炸”能力：拦截弹飞到目标
 * {@link #fuseRadius} 内时起爆，对 {@link #splashRadius} 内的敌方子弹按距离衰减结算伤害，
 * 适合模拟 AHEAD/可编程空炸类近防炮弹药。
 *
 * <p>目标由 {@link TriCIWS} 在发射时写入 {@code bullet.data}；本类只负责飞行、近炸与范围清理。
 * 默认关闭与地形/单位/建筑的碰撞，避免拦截弹在命中子弹前先撞到地面或单位。
 */
public class PIBulletType extends InterceptorBulletType {

    /** 近炸触发半径（世界单位，8 = 1 格）。 */
    public float fuseRadius = 12f;
    /** 破片杀伤半径（世界单位）；&lt;= 0 时只伤害直接目标。 */
    public float splashRadius = 18f;
    /** 起爆保险距离：离发射点小于该距离时不触发，避免贴脸自爆。 */
    public float armingDistance = 6f;
    /** 单次近炸最多伤害的敌方子弹数；&lt;= 0 表示不限制。 */
    public int maxTargets = 3;
    /** 近炸伤害倍率（相对拦截弹当前伤害）。 */
    public float fuseDamageMultiplier = 1f;
    /** 是否按距离衰减近炸伤害。 */
    public boolean damageFalloff = true;
    /** 是否允许近炸伤害 {@code hittable = false} 的子弹。 */
    public boolean targetNonHittable = false;
    /** 是否忽略已被护盾吸收的子弹。 */
    public boolean ignoreAbsorbed = true;
    /** 近炸特效；为 null 时不播放。 */
    public Effect fuseEffect = Fx.flakExplosion;
    /** 近炸音效；为 null 时不播放。 */
    public Sound fuseSound = Sounds.explosion;

    public PIBulletType(){
        super();
        initInterceptor();
    }

    public PIBulletType(float speed, float damage){
        super(speed, damage);
        initInterceptor();
    }

    private void initInterceptor(){
        // 拦截弹只与敌方子弹结算，避免被地形/单位/建筑提前拦下
        collides = false;
        collidesAir = false;
        collidesGround = false;
        collidesTiles = false;
    }

    @Override
    public void update(Bullet b){
        super.update(b);
        if(!b.isAdded()) return;

        Bullet target = b.data instanceof Bullet other ? other : null;
        if(target != null && (!target.isAdded() || target.type() == null)){
            b.data = null;
            target = null;
        }

        if(target == null) return;
        if(b.dst(b.originX, b.originY) < armingDistance) return;

        if(b.dst(target) <= fuseRadius){
            detonate(b);
        }
    }

    /** 在拦截弹当前位置起爆，清理周围敌方子弹。 */
    protected void detonate(Bullet b){
        if(!b.isAdded()) return;

        Bullet target = b.data instanceof Bullet other && other.isAdded() ? other : null;
        int hits = 0;

        // 直接目标优先：即使 splashRadius 配置得很小，也应保证近炸对目标有效
        if(target != null && canHit(b, target)){
            damageBullet(target, b.damage() * fuseDamageMultiplier);
            hits++;
        }

        if(splashRadius > 0f && (maxTargets <= 0 || hits < maxTargets)){
            float radius = splashRadius;
            for(Bullet other : Groups.bullet.intersect(b.x - radius, b.y - radius, radius * 2f, radius * 2f)){
                if(other == b || other == target) continue;
                if(maxTargets > 0 && hits >= maxTargets) break;
                if(!canHit(b, other)) continue;

                float dst = b.dst(other);
                if(dst > radius) continue;

                float dmg = b.damage() * fuseDamageMultiplier;
                if(damageFalloff){
                    dmg *= Mathf.clamp(1f - dst / radius);
                }
                if(dmg <= 0f) continue;

                damageBullet(other, dmg);
                hits++;
            }
        }

        if(fuseEffect != null){
            fuseEffect.at(b.x, b.y, b.rotation());
        }
        if(fuseSound != null){
            fuseSound.at(b.x, b.y);
        }
        b.remove();
    }

    private boolean canHit(Bullet b, Bullet other){
        if(other == null || !other.isAdded() || other.type() == null) return false;
        if(other == b || other.team == b.team) return false;
        if(ignoreAbsorbed && other.absorbed) return false;
        if(!targetNonHittable && !other.type().hittable) return false;
        return true;
    }

    private void damageBullet(Bullet target, float damage){
        if(damage <= 0f) return;
        if(target.damage() > damage){
            target.damage(target.damage() - damage);
        }else{
            target.remove();
        }
    }
}
