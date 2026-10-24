package triangle.content;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.bullet.LiquidBulletType;
import mindustry.entities.part.RegionPart;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.world.draw.DrawMulti;
import mindustry.world.draw.DrawTurret;
import triangle.black.PIBulletType;
import triangle.black.TriCIWS;

import static mindustry.type.ItemStack.with;

/**
 * 三角模组炮塔注册表。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link #solubilize}：酸液炮塔；</li>
 *   <li>{@link #ciws430}：原 {@code 430.json} 迁移而来的近防炮，使用 {@link TriCIWS}
 *       索敌敌方子弹，并发射 {@link PIBulletType} 近炸拦截弹。</li>
 * </ul>
 */
public class TriTurret {

    /** 溶解塔：3×3 液体炮塔，射程 210，可发射四种酸性液体。 */
    public static LiquidTurret solubilize;

    /** 430 近防炮：3×3，射程 400，以敌方子弹为目标并发射近炸拦截弹。 */
    public static TriCIWS ciws430;

    /** 创建炮塔内容（由 {@code triangle.loadContent()} 调用）。 */
    public static void load(){
        solubilize = new LiquidTurret("solubilize"){{
            requirements(Category.turret, with(Items.metaglass,45,TriItems.FeSteel,35,TriItems.TiAlloy,25));
            ammo(
                    // 王水：施加酸蚀，持续时间 300 tick（5 秒）
                    TriLiquids.aquaRegia,new LiquidBulletType(TriLiquids.aquaRegia){{
                        knockback = 0.7f;
                        drag = 0.05f;
                        speed = 15f;
                        lifetime = 30.0f;
                        layer = Layer.bullet - 2f;
                        status = TriStatus.AcidAttack;
                        statusDuration = 300;
                    }},
                    // 硝酸：腐蚀 300 tick（5 秒）
                    TriLiquids.HNO3,new LiquidBulletType(TriLiquids.HNO3){{
                        knockback = 0.7f;
                        drag = 0.05f;
                        speed = 15f;
                        lifetime = 30.0f;
                        layer = Layer.bullet - 2f;
                        status = TriStatus.corrosion;
                        statusDuration = 300;
                    }},
                    // 盐酸：腐蚀 120 tick（2 秒）
                    TriLiquids.hydrochloricAcid,new LiquidBulletType(TriLiquids.hydrochloricAcid){{
                        knockback = 0.7f;
                        drag = 0.05f;
                        speed = 15f;
                        lifetime = 30.0f;
                        layer = Layer.bullet - 2f;
                        status = TriStatus.corrosion;
                        statusDuration = 120;
                    }},
                    // 硫酸：腐蚀 180 tick（3 秒）
                    TriLiquids.H2SO4,new LiquidBulletType(TriLiquids.H2SO4){{
                        knockback = 0.7f;
                        drag = 0.05f;
                        speed = 15f;
                        lifetime = 30.0f;
                        layer = Layer.bullet - 2f;
                        status = TriStatus.corrosion;
                        statusDuration = 180;
                    }}
            );
            size = 3;
            recoil = 0f;
            reload = 3f;
            inaccuracy = 5f;
            shootCone = 50f;
            liquidCapacity = 100f;
            shootEffect = Fx.shootLiquid;
            range = 210f;
            scaledHealth = 250;
            health = 980;
        }};

        // ---- 430 近防炮（由 430.json 迁移，并改为拦截敌方子弹） ----
        ciws430 = new TriCIWS("430"){{
            size = 3;
            health = 730;
            // 保留原 430 的放置/统计基础属性；实际索敌仍由 TriCIWS 只针对子弹
            targetAir = true;
            targetGround = true;
            reload = 3f;
            range = 400f;
            recoil = 0f;
            minWarmup = 0.99f;
            shootWarmupSpeed = 2.8f;
            shootY = 18f;
            inaccuracy = 1f;
            rotateSpeed = 10f;
            shake = 0.1f;
            maxAmmo = 60;
            ammoPerShot = 1;
            ammoUseEffect = Fx.none;
            requirements(Category.turret, with(TriItems.TiAlloy,125,TriItems.FeSteel,85,TriItems.MonocrystallineSi,75));
            researchCost = with(TriItems.TiAlloy,225,TriItems.FeSteel,175,TriItems.MonocrystallineSi,75);
            consumePower(1f);

            // 拦截弹：速度 40、伤害 55；寿命 12 tick 对应约 480 世界单位，覆盖 400 射程
            ammo(TriItems.TiAlloy, new PIBulletType(40f, 55f){{
                lifetime = 12f;
                width = 3f;
                height = 10f;
                ammoMultiplier = 1f;
                knockback = 0.1f;
                sprite = "bullet";
                pierce = false;
                drag = 0f;
                hitSize = 10f;
                armorMultiplier = 1.5f;
                buildingDamageMultiplier = 0.05f;

                // 近炸参数：在目标附近空炸，并清理半径内的其他来袭子弹
                fuseRadius = 12f;
                splashRadius = 20f;
                maxTargets = 3;
                armingDistance = 6f;
                fuseDamageMultiplier = 1.2f;
                damageFalloff = true;

                // 430 需要兼顾普通目标：允许与敌方单位/建筑碰撞
                collides = true;
                collidesAir = true;
                collidesGround = true;
                collidesTiles = true;

                // 拦截弹曳光
                trailLength = 4;
                trailWidth = 1.2f;
                trailColor = Color.valueOf("FFD37F");
                frontColor = Color.valueOf("FFFFFF");
                backColor = Color.valueOf("FFD37F");
            }});

            // 与 430.json 一致的炮塔绘制：4 个炮管 + 1 个炮体
            drawer = new DrawMulti(new DrawTurret(){{
                parts.add(new RegionPart("-head"){{
                    x = 0f;
                    y = 0f;
                    progress = PartProgress.recoil;
                    moveX = 1f;
                    moveY = 0f;
                    heatColor = Color.valueOf("FF590020");
                }});
                parts.add(new RegionPart("-head"){{
                    x = -1f;
                    y = 0f;
                    progress = PartProgress.recoil;
                    moveX = 1f;
                    moveY = 0f;
                    heatColor = Color.valueOf("FF590020");
                }});
                parts.add(new RegionPart("-head"){{
                    x = 1f;
                    y = 0f;
                    progress = PartProgress.recoil;
                    moveX = -1f;
                    moveY = 0f;
                    heatColor = Color.valueOf("FF590020");
                }});
                parts.add(new RegionPart("-head"){{
                    x = 0f;
                    y = 0f;
                    progress = PartProgress.recoil;
                    moveX = -1f;
                    moveY = 0f;
                    heatColor = Color.valueOf("FF590020");
                }});
                parts.add(new RegionPart("-body"){{
                    mirror = false;
                    x = 0f;
                    y = 0f;
                    moveX = 0f;
                    moveY = 0f;
                    moveRot = 0f;
                    heatProgress = PartProgress.recoil;
                    heatColor = Color.valueOf("FF5900");
                }});
            }});
        }};
    }
}
