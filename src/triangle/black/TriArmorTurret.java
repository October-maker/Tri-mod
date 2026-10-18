package triangle.black;

import arc.Core;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Strings;
import mindustry.entities.bullet.BulletType;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatValue;
import mindustry.world.meta.StatValues;
import arc.scene.ui.layout.Table;

/**
 * 护甲反应型炮塔。
 *
 * <p>用途：把 ArmorReactiveBulletType 的炮台详情页显示从原版默认改造成“隐藏穿甲 / 隐藏原版伤害，并把弹药
 * （图标+名称）与其横向伤害表框进同一个 table、整体挂在‘弹药：’统计下”，同时不影响非护甲反应弹药的原有显示。
 *
 * <p>原理：原版 ItemTurret.setStats() 通过 {@code stats.add(Stat.ammo, StatValues.ammo(ammoTypes))} 一次性把
 * 所有弹药的“伤害 / 穿甲 / 溅射”等直读渲染出来，子弹类没有可覆盖的钩子，JSON 炮塔也无法覆盖 setStats。
 * 因此本类继承 ItemTurret 并重写 setStats()：先调用 super 得到基础统计，再移除合并的弹药统计，分两组重建——
 * 非 ArmorReactive 弹药仍走原版 StatValues.ammo（保留伤害/穿甲），ArmorReactive 弹药只显示图标+名称（隐藏
 * 穿甲与默认伤害），并在同一框内追加自定义横向伤害表（装甲 0→½baseArmor→baseArmor→3⁄2baseArmor）。
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

        ObjectMap<Item, BulletType> normal = new ObjectMap<>();
        Seq<ArmorReactiveBulletType> armor = new Seq<>();
        for(ObjectMap.Entry<Item, BulletType> e : ammoTypes){
            if(e.value instanceof ArmorReactiveBulletType a){
                armor.add(a);
            }else{
                normal.put(e.key, e.value);
            }
        }

        // 非护甲反应弹药：保留原版显示（伤害/穿甲等）
        if(!normal.isEmpty()){
            stats.add(Stat.ammo, StatValues.ammo(normal));
        }

        // 护甲反应弹药：以与原版其他弹药一致的 grayPanel 框呈现（图标+名称 + 横向伤害表），整体挂在“弹药：”下
        if(!armor.isEmpty()){
            stats.add(Stat.ammo, new StatValue(){
                @Override public void display(Table table){
                    for(ArmorReactiveBulletType a : armor){
                        Item item = itemOf(a);
                        if(item == null) continue;
                        table.row();
                        // 与原版弹药一致的框：grayPanel 背景 + padLeft/Top/Bottom(5) + growX + margin(10)
                        table.table(Styles.grayPanel, t -> {
                            t.left().top().defaults().padRight(3f).left();
                            // 图标 + 名称（嵌套表，样式同原版）
                            t.table(iconName -> {
                                iconName.image(item.uiIcon).size(24f).padRight(4f).right().scaling(Scaling.fit).top();
                                iconName.add(item.localizedName).padRight(10f).left().top();
                            });
                            // 详情行：横向伤害表
                            t.row();
                            addArmorDamageTable(t, a);
                        }).padLeft(5f).padTop(5f).padBottom(5f).growX().margin(10f);
                    }
                }
            });
        }
    }

    /** 追加护甲反应弹药的横向伤害表（装甲 0→½baseArmor→baseArmor→3⁄2baseArmor，精确 1 位小数）。 */
    private void addArmorDamageTable(Table parent, ArmorReactiveBulletType b){
        float half = b.baseArmor / 2f;
        float threeHalf = b.baseArmor * 1.5f;
        String[] heads = {
            Core.bundle.get("tri.bullet.armor.damage0"),
            Core.bundle.get("tri.bullet.armor.armorHalf"),
            Core.bundle.get("tri.bullet.armor.damageHalf"),
            Core.bundle.get("tri.bullet.armor.armorBase"),
            Core.bundle.get("tri.bullet.armor.damageBase"),
            Core.bundle.get("tri.bullet.armor.armorOneHalf"),
            Core.bundle.get("tri.bullet.armor.damageOneHalf")
        };
        float[] vals = {
            b.totalDamage(0f), half, b.totalDamage(half),
            b.baseArmor, b.baseDamage, threeHalf, b.totalDamage(threeHalf)
        };
        parent.table(dt -> {
            dt.left();
            dt.table(dam ->{
                dam.left();
                dam.add("[#FF6666]" + Strings.autoFixed(b.totalDamage(0f),1)).row();
                dam.image(Icon.right).size(32f).padLeft(8f).padRight(12f).row();
                dam.add("[#999999]Armor:" + 0f);
            }).margin(6f);
            dt.table(dam -> {
                dam.left();
                dam.add("|").row();
                dam.add("[#FFFFFF]" + half).row();
                dam.add("|").row();
            }).margin(6f);
            dt.table(dam ->{
                dam.left();
                dam.add("[#FF6666]" + Strings.autoFixed(b.totalDamage(half),1)).row();
                dam.image(Icon.right).size(32f).padLeft(8f).padRight(12f).row();
                dam.add("[#999999]Armor:" + half);
            }).margin(6f);
            dt.table(dam -> {
                dam.left();
                dam.add("|").row();
                dam.add("[#FFFFFF]" + half).row();
                dam.add("|").row();
            }).margin(6f);
            dt.table(dam ->{
                dam.left();
                dam.add("[#FF6666]" + Strings.autoFixed(b.baseDamage,1)).row();
                dam.image(Icon.right).size(32f).padLeft(8f).padRight(12f).row();
                dam.add("[#999999]Armor:" + b.baseArmor);
            }).margin(6f);
            dt.table(dam -> {
                dam.left();
                dam.add("|").row();
                dam.add("[#FFFFFF]" + half).row();
                dam.add("|").row();
            }).margin(6f);
            dt.table(dam ->{
                dam.left();
                dam.add("[#FF6666]" + Strings.autoFixed(b.totalDamage(threeHalf),1)).row();
                dam.image(Icon.right).size(32f).padLeft(8f).padRight(12f).row();
                dam.add("[#999999]Armor:" + threeHalf);
            }).margin(6f);
        });
    }

    /** 根据子弹反查其所属弹药物品（用于取图标与本地化名称）。 */
    private Item itemOf(BulletType b){
        for(ObjectMap.Entry<Item, BulletType> e : ammoTypes){
            if(e.value == b) return e.key;
        }
        return null;
    }
}
