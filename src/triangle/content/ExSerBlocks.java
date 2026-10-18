package triangle.content;

import arc.Events;
import mindustry.content.Blocks;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import triangle.black.ArmorReactiveBulletType;

/**
 * 对原版（Serpulo）方块做运行时修改。
 *
 * <p>目前实现：给原版炮塔“幽灵”（spectre）新增 LHEAT（破甲弹）与 LHESH（碎甲弹，护甲反应型）两个弹种，
 * 并把射程扩充至 280；同时用原版 {@code ItemTurret.limitRange()} 依据新射程重算既有弹种、LHEAT、LHESH 的
 * lifetime，保证所有弹种都能飞行到射程而不会提前爆炸。
 *
 * <p>必须在模组 {@code loadContent()} 中调用：此时原版方块已创建、尚未 {@code init()}，对字段的修改
 * 会被随后的 {@code init()} 正确吸收（消耗过滤、placeOverlapRange 等都会纳入新弹种与新射程）。
 */
public class ExSerBlocks {
    public static void load(){
        // 原版 spectre 是 ItemTurret（mindustry.content.Blocks.spectre）
        ItemTurret spectre = (ItemTurret)Blocks.spectre;

        // 1) 把射程扩充到 280（原版默认更短；放大后需要让弹种飞得更远）
        spectre.range = 280f;

        // 2) 新增 LHEAT 破甲弹（数值对应 coilgun.json 的 LHEAT；未指定 type → 默认 BasicBulletType）
        //    key 使用模组物品 TriItems.LHEAT（与 coilgun 一致，spectre 会消耗该物品作为弹药）
        BulletType lheat = new BasicBulletType(8f, 20f){{
            splashDamageRadius = 6f;   // 溅射半径（格）
            splashDamage = 10f;        // 溅射伤害
            width = 5f;
            height = 10f;
            ammoMultiplier = 1f;       // 每次供弹提供的弹药数
            knockback = 10f;           // 击退
            pierce = false;            // 不穿透
            drag = 0f;
            hitSize = 12f;
            pierceArmor = false;       // 不无视护甲
            buildingDamageMultiplier = 0.2f; // 对建筑伤害倍率
            fragBullets = 1;           // 命中后分裂出 1 发子弹药
            fragRandomSpread = 0;      // 分裂散射角度
            sprite = "triangle-205HEAT";   // 自定义贴图（图集建好后由下方 ContentInitEvent 解析）
            // 分裂子弹：激光（coilgun LHEAT 的 fragBullet 对应字段）
            fragBullet = new LaserBulletType(){{
                sideAngle = 175f;      // 侧向子弹角度
                sideWidth = 1f;        // 侧向宽度
                sideLength = 40f;      // 侧向长度
                lifetime = 20f;
                damage = 225f;         // 激光伤害
                length = 45f;          // 激光长度
                pierce = true;         // 穿透
                pierceArmor = true;    // 无视护甲
                buildingDamageMultiplier = 0.2f;
            }};
        }};

        // 3) 把 LHEAT 追加进弹药表。注意用 put() 追加，不要用 ammo()——ammo() 会替换整张表、覆盖原版弹种
        spectre.ammoTypes.put(TriItems.LHEAT, lheat);

        // 新增 LHESH 碎甲弹（ArmorReactiveBulletType，模组自定义子弹：伤害随目标装甲变化）
        //    · 用带参构造(200)使 baseDamage=200 且 damage=200：spectre 是原版炮塔，详情页走原版 StatValues.ammo，
        //      会把 damage 显示为“200 damage”；若用无参构造只设 baseDamage，damage 仍为构造默认 5，显示会不对。
        //    · key 使用模组物品 TriItems.LHESH（与 coilgun 一致，spectre 会消耗该物品作为弹药）
        BulletType lhesh = new ArmorReactiveBulletType(200f){{
            baseArmor = 10f;           // 基准装甲：目标装甲低于此→伤害提升，高于此→降低
            ammoMultiplier = 1f;
            damageChangeRate = 75f;    // 伤害随装甲变化的倍率
            splashDamageRadius = 6f;   // 溅射半径（格）
            splashDamage = 10f;        // 溅射伤害
            width = 5f;
            height = 10f;
            knockback = 10f;
            sprite = "triangle-205HESH"; // 自定义贴图（图集建好后由下方 ContentInitEvent 解析）
            drag = 0f;
            hitSize = 12f;
            buildingDamageMultiplier = 1f;
        }};
        // 把 LHESH 追加进弹药表
        spectre.ammoTypes.put(TriItems.LHESH, lhesh);

        // 4) 依据新射程 280 重算所有弹种（既有弹种 + LHEAT + LHESH）的 lifetime，使其能飞到射程而不提前爆炸
        //    （limitRange() 默认 margin=9，lifetime=(range+margin+extraRangeMargin+10)/speed）
        spectre.limitRange();

        // 5) 自定义贴图 triangle-205HEAT / triangle-205HESH 需在图集（atlas）构建完成后才能解析。
        //    BasicBulletType.load() 会根据 sprite 字段查找 frontRegion/backRegion；
        //    这两个子弹未注册进 ContentLoader，不会自动 load，故手动在 ContentInitEvent 里调用。
        Events.on(ContentInitEvent.class, e -> {
            lheat.load();   // 解析 LHEAT 贴图
            lhesh.load();   // 解析 LHESH 贴图
        });
    }
}
