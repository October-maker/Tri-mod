package triangle.black;

import arc.struct.Seq;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Posc;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;

/**
 * 近防炮（CIWS）：可在普通目标与敌方弹药之间切换索敌的 ItemTurret。
 *
 * <p>原版 {@link ItemTurret} 的索敌只考虑单位和建筑；本类在此基础上增加
 * {@code Groups.bullet} 中的敌方子弹目标，并仍然保留 ItemTurret 的弹药、弹种、
 * 弹药容量与装填机制。
 *
 * <p>默认同时具备两类索敌能力：优先搜索敌方弹药（可通过 {@link #prioritizeBullets} 关闭），
 * 没有合适弹药目标时回退到原版普通目标索敌（可通过 {@link #targetNormalTargets} 关闭）。
 *
 * <p>配合 {@link PIBulletType} 使用时可形成“直接命中弹 + 近炸弹”组合：
 * 普通 {@code InterceptorBulletType} 走弹道碰撞，近炸弹在目标附近空炸并清理周围来袭子弹。
 *
 * <p>注意：只有 {@code InterceptorBulletType} 及其子类会读取 {@code bullet.data} 中的目标；
 * 普通弹药仍会按常规方式与单位/建筑碰撞，不能拦截子弹。
 */
public class TriCIWS extends ItemTurret {

    /** 是否允许索敌普通目标（敌方单位、敌方建筑等）；false 时只拦截弹药。 */
    public boolean targetNormalTargets = true;
    /** 是否优先索敌敌方弹药；false 时普通目标优先，弹药作为后备目标。 */
    public boolean prioritizeBullets = true;
    /** 目标评分中“子弹伤害”的权重：分数 = 距离² - 伤害 × 权重，分数越低越优先。 */
    public float damageTargetWeight = 10f;
    /** 是否只拦截正在接近炮台的子弹（按子弹速度与“子弹→炮台”方向的点积判断）。 */
    public boolean onlyIncoming = true;
    /** 是否忽略已被护盾吸收的子弹。 */
    public boolean ignoreAbsorbed = true;
    /** 是否允许锁定 {@code hittable = false} 的子弹（激光/瞬发类等，默认不锁定）。 */
    public boolean targetNonHittable = false;
    /** 最小拦截距离（世界单位，8 = 1 格）；小于该距离的子弹不锁定，避免炮塔贴脸乱转。 */
    public float minInterceptRange = 0f;

    public TriCIWS(String name){
        super(name);
        // 默认允许普通单位/建筑目标；是否实际优先打子弹由 prioritizeBullets 控制
        targetAir = true;
        targetGround = true;
        predictTarget = true;
        targetInterval = 5f;
        newTargetInterval = 5f;
    }

    @Override
    public void setStats(){
        super.setStats();
        // 仅当明确禁用普通目标时，才移除“对空/对地”统计
        if(!targetNormalTargets){
            stats.remove(Stat.targetsAir);
            stats.remove(Stat.targetsGround);
        }
    }

    public class TriCIWSBuild extends ItemTurretBuild {

        /**
         * 索敌顺序：
         * <ol>
         *   <li>{@link #prioritizeBullets} = false 且允许普通目标时，先找普通目标；</li>
         *   <li>再找敌方弹药；</li>
         *   <li>没有弹药目标时，若允许普通目标则回退到原版索敌。</li>
         * </ol>
         */
        @Override
        protected Posc findEnemy(float range){
            Posc normal = null;
            boolean normalSearched = false;

            if(targetNormalTargets && !prioritizeBullets){
                normal = super.findEnemy(range);
                normalSearched = true;
                if(normal != null) return normal;
            }

            Bullet bullet = findBulletEnemy(range);
            if(bullet != null) return bullet;

            if(targetNormalTargets){
                return normalSearched ? normal : super.findEnemy(range);
            }
            return null;
        }

        /** 从射程内的敌方子弹中选出评分最低的目标。 */
        private Bullet findBulletEnemy(float range){
            Seq<Bullet> candidates = Groups.bullet.intersect(x - range, y - range, range * 2f, range * 2f);
            return candidates.min(b -> isValidBulletTarget(b, range), b -> bulletScore(b));
        }

        /** 目标存活、仍在射程内且类型有效时才继续交战。 */
        @Override
        protected boolean validateTarget(){
            if(target instanceof Bullet b){
                return isValidBulletTarget(b, range());
            }
            return targetNormalTargets && super.validateTarget();
        }

        /** 让拦截弹知道要拦截哪颗子弹，供 InterceptorBulletType 做弹道碰撞。 */
        @Override
        protected void handleBullet(Bullet bullet, float offsetX, float offsetY, float angleOffset){
            super.handleBullet(bullet, offsetX, offsetY, angleOffset);
            if(target instanceof Bullet b && b.isAdded()){
                bullet.data = b;
            }
        }

        private boolean isValidBulletTarget(Bullet b, float range){
            if(b == null || !b.isAdded() || b.type() == null) return false;
            if(b.team == team) return false;
            if(ignoreAbsorbed && b.absorbed) return false;
            if(!targetNonHittable && !b.type().hittable) return false;

            float dst2 = b.dst2(this);
            if(dst2 > range * range) return false;
            if(minInterceptRange > 0f && dst2 < minInterceptRange * minInterceptRange) return false;
            if(onlyIncoming && !isIncoming(b)) return false;

            return true;
        }

        /** 子弹是否朝炮台方向飞行；静止或远离的子弹不锁定。 */
        private boolean isIncoming(Bullet b){
            float dx = x - b.x;
            float dy = y - b.y;
            return b.vel.x * dx + b.vel.y * dy > 0f;
        }

        /** 距离越近、伤害越高的子弹评分越低，优先拦截。 */
        private float bulletScore(Bullet b){
            return b.dst2(this) - b.damage * damageTargetWeight;
        }
    }
}
