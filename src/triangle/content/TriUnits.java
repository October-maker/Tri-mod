package triangle.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import mindustry.ai.types.BuilderAI;
import mindustry.ai.types.CommandAI;
import mindustry.content.Fx;
import mindustry.gen.Sounds;
import mindustry.gen.UnitEntity;
import mindustry.graphics.MultiPacker;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBoltBulletType;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.weapons.RepairBeamWeapon;

/**
 * 模组自定义单位。
 *
 * <p>目前实现：watchtowerCore 的核心机（玩家在该核心出生/操纵的单位）。
 * 数值来源：
 *   - 炮台数量与攻击伤害：基于原版赛普罗的 alpha（阿尔法）——1 门激光炮，伤害 11；
 *   - 建造速度：100%（buildSpeed = 1f）；
 *   - 修复类型：类似 Erekir 核心机（evoke/incite/emanate）的 RepairBeamWeapon。
 */
public class TriUnits {
    /** watchtowerCore 的核心机（JSON 中 watchtowerCore 的 unitType 指向此单位）。 */
    public static UnitType core;

    /**
     * watchtowerCore 的核心机单位类型。
     * 身体贴图暂置空（不渲染），待之后绘制 assets/sprites/units/coreUnit*.png 后自动生效；
     * 若已提供贴图，则 has(name) 为真、直接使用贴图，无需改动本类。
     */
    public static class CoreUnitType extends UnitType{
        public CoreUnitType(String name){
            super(name);
        }

        @Override
        public void load(){
            super.load();
            // 身体贴图置空：暂无该单位贴图时，把 body 区域置为空 region（found()=false，不渲染）；
            // 之后绘制好贴图（region 名与 name 一致）会自动取到真实贴图。
            if(!Core.atlas.has(name)){
                region = new TextureRegion();
            }
        }

        /**
         * 图标/描边生成会先于 Content.load()（即 load()）执行；本单位无贴图时各 region 字段
         * 此时仍为 null，super.createIcons() 在遍历描边序列时会因 outlineTarget==null 抛 NPE
         * （游戏加载崩溃）。这里把参与描边的区域置为空 region（found()=false）占位，既避免崩溃，
         * 又保持身体贴图为空；后续提供 coreUnit*.png 后会自动取到真实贴图。
         */
        @Override
        public void createIcons(MultiPacker packer){
            TextureRegion r = Core.atlas.has(name) ? Core.atlas.find(name) : new TextureRegion();
            region = jointRegion = footRegion = baseJointRegion = legRegion = treadRegion = r;
            super.createIcons(packer);
        }
    }

    public static void load(){
        core = new CoreUnitType("coreUnit"){{
            // ============ 核心机通用行为 ============
            // 与赛普罗核心单位（alpha/beta/gamma）一致：AI 时自动建造/修复，玩家控制时由玩家操作
            controller = u -> u.team.isAI() ? new BuilderAI(true, 400f) : new CommandAI();
            isEnemy = false;
            targetBuildingsMobile = false;
            lowAltitude = true;
            flying = true;
            fogRadius = 0f;
            alwaysUnlocked = true; // 出生单位无需研究即可使用
            constructor = UnitEntity::create; // createIcons 会校验该构造器非空，必须显式设置

            // ============ 建造速度 = 100%（buildSpeed 为倍率，1f 即 100%） ============
            buildSpeed = 1f;

            // ============ 采矿：挖矿能力 ============
            // mineTier=1 表示可挖 1 级矿（对应基础矿）；mineSpeed 为挖矿速度倍率，1f=100%
            mineTier = 1;
            mineSpeed = 1f; // 挖矿速度 100%（原版 alpha 为 6.5f=650%）
            // 注：本机为 BuilderAI（自动建造/修复），像原版核心单位一样，玩家下达挖矿指令时即可挖矿。

            // ============ 移动/机动（沿用 alpha） ============
            speed = 5f;
            rotateSpeed = 15f;
            accel = 0.08f;
            drag = 0.1f;

            // ============ 体量与生存（沿用 alpha） ============
            health = 150f;
            hitSize = 8f;
            itemCapacity = 30;
            engineOffset = 6f;
            wreckSoundVolume = 0.8f;
            deathSoundVolume = 0.7f;

            // ============ 攻击炮台：数量与伤害基于原版 alpha ============
            // alpha 有 1 门炮（small-basic-weapon），LaserBoltBulletType(2.5f, 11)，伤害 11、不打建筑
            weapons.add(new Weapon("small-basic-weapon"){{
                reload = 17f;          // 装填（与 alpha 一致）
                x = 2.75f;             // 挂载偏移（与 alpha 一致）
                y = 1f;
                top = false;
                shootSound = Sounds.shootAlpha;
                bullet = new LaserBoltBulletType(2.5f, 11){{ // 弹速 2.5，伤害 11（与 alpha 一致）
                    scaleKeepVelocity = true;
                    width = 1.5f;
                    height = 4.5f;
                    hitEffect = despawnEffect = Fx.hitBulletColor;
                    trailWidth = 1.2f;
                    trailLength = 3;
                    shootEffect = Fx.shootSmallColor;
                    smokeEffect = Fx.hitLaserColor;
                    backColor = trailColor = Pal.yellowBoltFront;
                    hitColor = Pal.yellowBoltFront;
                    frontColor = Color.white;
                    lightColor = Pal.yellowBoltFront;
                    lifetime = 60f;
                    buildingDamageMultiplier = 0f; // 对建筑无伤害（与 alpha 一致）
                    homingPower = 0.02f;
                }};
            }});

            // ============ 修复：类型类似 Erekir 核心机（RepairBeamWeapon） ============
            // 参考原版 evoke（Erekir 核心机）的 RepairBeamWeapon：只修建筑、修复光束
            weapons.add(new RepairBeamWeapon(){{
                widthSinMag = 0.11f;
                reload = 20f;
                x = 0f;
                y = 6.5f;
                rotate = false;
                shootY = 0f;
                beamWidth = 0.7f;
                repairSpeed = 3.1f;          // 修复速率（固定值）
                fractionRepairSpeed = 0.06f; // 按生命比例修复
                shootCone = 15f;
                mirror = false;
                targetUnits = false;         // 只修复建筑（与 Erekir 核心机一致）
                targetBuildings = true;
                autoTarget = false;
                controllable = true;
                laserColor = Pal.accent;
                healColor = Pal.accent;
                bullet = new BulletType(){{
                    maxRange = 60f;
                }};
            }});
        }};
    }
}
