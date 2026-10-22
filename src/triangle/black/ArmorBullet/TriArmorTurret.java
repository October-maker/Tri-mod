package triangle.black.ArmorBullet;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.entities.bullet.BulletType;
import mindustry.type.Item;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatValues;

/**
 * 护甲反应型炮塔。
 *
 * <p>用途：把 ArmorReactiveBulletType 的炮台详情页显示从原版默认改造成“隐藏穿甲 / 隐藏原版伤害，并把弹药
 * （图标+名称）与其横向伤害表框进同一个 table、整体挂在‘弹药：’统计下”，同时不影响非护甲反应弹药的原有显示。
 *
 * <p>原理：原版 ItemTurret.setStats() 通过 {@code stats.add(Stat.ammo, StatValues.ammo(ammoTypes, name))} 一次性把
 * 所有弹药的“伤害 / 穿甲 / 溅射”等直读渲染出来，子弹类没有可覆盖的钩子，JSON 炮塔也无法覆盖 setStats。
 * 因此本类继承 ItemTurret 并重写 setStats()：先调用 super 得到基础统计，再移除合并的弹药统计，分两组重建——
 * 非 ArmorReactive 弹药仍走原版 StatValues.ammo（保留伤害/穿甲），并支持 block.&lt;炮塔名&gt;.&lt;弹药名&gt;.info 说明行；
 * ArmorReactive 弹药复用 {@link TriArmorStats#displayArmor} 渲染图标+名称 + 自定义横向伤害表（并追加原版
 * 条件数值行、支持 block.&lt;炮塔名&gt;.&lt;弹药名&gt;.info 说明行）。
 *
 * <p>在 assets JSON 中把炮塔的 "type" 改为 "TriArmorTurret" 即可生效（本类已在 triangle.java 的 ClassMap 注册）。
 */
public class TriArmorTurret extends ItemTurret {

    public TriArmorTurret(String name){
        super(name);
    }

    @Override
    public void setStats(){
        // 基础统计 + 原版弹药统计 + 弹药容量（一次性加入，随后拆分重建弹药区）
        super.setStats();
        // 去掉合并的弹药统计，改为分两组重建
        stats.remove(Stat.ammo);

        // 炮塔内部名（如 "coilgun"）：用于生成 block.<炮塔名>.<弹药名>.info 的弹药专属说明行 key
        String blockName = this.name;

        ObjectMap<Item, BulletType> normal = new ObjectMap<>();
        Seq<ArmorReactiveBulletType> armor = new Seq<>();
        for(ObjectMap.Entry<Item, BulletType> e : ammoTypes){
            if(e.value instanceof ArmorReactiveBulletType a){
                armor.add(a);
            }else{
                normal.put(e.key, e.value);
            }
        }

        // 非护甲反应弹药：走原版 StatValues.ammo(map, blockName)。
        // 与 v160.7 原版 ItemTurret 一致地传入 blockName，使 bundle 中存在 block.<炮塔名>.<弹药名>.info 键时
        // 能在该弹药的灰框内显示弹药专属说明行（保留伤害/穿甲等原版直读数值）。
        if(!normal.isEmpty()){
            stats.add(Stat.ammo, StatValues.ammo(normal, blockName));
        }

        // 护甲反应弹药：以与原版其他弹药一致的 grayPanel 框呈现（图标+名称 + 横向伤害表 + 原版条件数值行），
        // 整体挂在“弹药：”下。完整版表格逻辑集中复用 TriArmorStats。
        if(!armor.isEmpty()){
            stats.add(Stat.ammo, table -> TriArmorStats.displayArmor(table, ammoTypes, blockName, false));
        }
    }
}
