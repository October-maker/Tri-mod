package triangle.content;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.bullet.LiquidBulletType;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.world.blocks.defense.turrets.LiquidTurret;

import static mindustry.type.ItemStack.with;

public class TriTurret {
    public static LiquidTurret solubilize;

    public static void load(){
        solubilize = new LiquidTurret("solubilize"){{
        requirements(Category.turret, with(Items.metaglass,45,TriItems.FeSteel,35,TriItems.TiAlloy,25));
        ammo(
                TriLiquids.aquaRegia,new LiquidBulletType(TriLiquids.aquaRegia){{
                    knockback = 0.7f;
                    drag = 0.05f;
                    speed = 15f;
                    lifetime = 30.0f;
                    layer = Layer.bullet - 2f;
                    status = TriStatus.AcidAttack;
                    statusDuration = 300;
                }},
                TriLiquids.HNO3,new LiquidBulletType(TriLiquids.HNO3){{
                    knockback = 0.7f;
                    drag = 0.05f;
                    speed = 15f;
                    lifetime = 30.0f;
                    layer = Layer.bullet - 2f;
                    status = TriStatus.corrosion;
                    statusDuration = 300;
                }},
                TriLiquids.HCl,new LiquidBulletType(TriLiquids.HCl){{
                    knockback = 0.7f;
                    drag = 0.05f;
                    speed = 15f;
                    lifetime = 30.0f;
                    layer = Layer.bullet - 2f;
                    status = TriStatus.corrosion;
                    statusDuration = 120;
                }},
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

