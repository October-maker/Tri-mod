package triangle.content;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.bullet.LiquidBulletType;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.world.blocks.defense.turrets.LiquidTurret;

import static mindustry.type.ItemStack.with;

/**
 * 三角模组的液体炮塔注册表。
 *
 * <p>目前仅有 {@link #solubilize}（溶解塔）：以酸液为弹药，命中后施加酸蚀或腐蚀状态。
 * 四种弹药分别对应王水、硝酸、盐酸与硫酸，状态持续时间各不相同。
 */
public class TriTurret {

    /** 溶解塔：3×3 液体炮塔，射程 210，可发射四种酸性液体。 */
    public static LiquidTurret solubilize;

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
    }
}
