package triangle.black;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Strings;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.EmpBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValue;
import mindustry.world.meta.StatValues;

import static mindustry.Vars.headless;
import static mindustry.Vars.tilesize;
import static triangle.black.MRFactory.MultiRecipeFactory.whiteOutlined;

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
 * ArmorReactive 弹药显示图标+名称 + 自定义横向伤害表，并追加除“伤害/穿透/穿甲/护甲修正/压制/伤害上限”外的
 * 原版条件数值行（对建筑伤害、溅射、击退等），同样支持 block.&lt;炮塔名&gt;.&lt;弹药名&gt;.info 说明行。
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

        // 护甲反应弹药：以与原版其他弹药一致的 grayPanel 框呈现（图标+名称 + 横向伤害表 + 原版条件数值行），整体挂在“弹药：”下
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
                            // 弹药专属说明行：bundle 存在 block.<炮塔名>.<弹药名>.info 键时才显示（等价原版 StatValues.tableInfo）
                            String infoKey = "block." + blockName + "." + item.name + ".info";
                            if(Core.bundle.has(infoKey)){
                                t.row();
                                tableInfo(t, infoKey);
                            }
                            // 详情行：自定义横向伤害表（装甲 0→½baseArmor→baseArmor→3⁄2baseArmor）
                            t.row();
                            // 追加原版条件数值行（排除被隐藏条目：伤害/穿透/穿甲/护甲修正/压制/伤害上限）
                            addAmmoStats(t, a, item);
                            t.row();
                            // 描述头：使用原版 bundle 键 stat.damage（中文“伤害”/英文“Damage”，随游戏语言本地化），
                            // 加冒号作为横向伤害表的标题行，明确框中数字为“伤害”数值
                            t.add("[accent]" + Core.bundle.get("stat.damage") + ":").padBottom(6f).left();
                            t.row();
                            addArmorDamageTable(t, a);
                        }).padLeft(5f).padTop(5f).padBottom(5f).growX().margin(10f);
                    }
                }
            });
        }
    }

    /**
     * 追加护甲反应弹药的“原版条件数值行”。
     *
     * <p>逻辑 Port 自 v160.7 {@code StatValues.ammo()}，对每个 BulletType 字段按原版条件判断是否显示对应
     * 数值与描述文字；但与需求约定排除以下 6 条（由自定义横向伤害表 / 护甲公式承担，避免重复或误导）：
     * <ol>
     *   <li>伤害（含持续伤害 DPS）—— 已由 addArmorDamageTable 按装甲档位呈现；</li>
     *   <li>穿透 N 个 / 无限穿透（pierce / pierceCap）—— 需求排除；</li>
     *   <li>穿甲（pierceArmor）—— ArmorReactiveBulletType 固定 pierceArmor=true，属内部实现，不展示；</li>
     *   <li>护甲弱点 / 部分穿甲 / 反甲（armorMultiplier / blockArmorMultiplier）—— 护甲已由总伤害公式计入；</li>
     *   <li>压制（秒+格）（suppressionRange）—— 需求排除；</li>
     *   <li>伤害上限 %（maxDamageFraction）—— 需求排除。</li>
     * </ol>
     *
     * @param parent 当前弹药的 grayPanel 框
     * @param a      护甲反应子弹（以 BulletType 传入，便于对 LaserBulletType / EmpBulletType 做 instanceof 判定）
     * @param item   该弹药对应的物品（供间隔/分裂/生成子弹递归显示时作为 key）
     */
    private void addAmmoStats(Table parent, BulletType a, Item item){
        // 【条目1】对建筑伤害修正：原版条件 buildingDamageMultiplier != 1
        if(a.buildingDamageMultiplier != 1){
            sep(parent, Core.bundle.format("bullet.buildingdamage", ammoStat((int)(a.buildingDamageMultiplier * 100 - 100))));
        }
        // 【条目2】射程变化（格）：原版条件 rangeChange != 0
        if(a.rangeChange != 0){
            sep(parent, Core.bundle.format("bullet.range", ammoStat(a.rangeChange / tilesize)));
        }
        // 【条目3】护盾伤害修正：原版条件 shieldDamageMultiplier != 1
        if(a.shieldDamageMultiplier != 1){
            sep(parent, Core.bundle.format("bullet.shielddamage", ammoStat((int)(a.shieldDamageMultiplier * 100 - 100))));
        }
        // 【条目4】溅射伤害 + 半径（格）：原版条件 splashDamage > 0
        if(a.splashDamage > 0){
            sep(parent, Core.bundle.format("bullet.splashdamage", (int)a.splashDamage, Strings.fixed(a.splashDamageRadius / tilesize, 1)));
        }
        // 【条目5】弹药倍率（弹药/物品）：原版条件 statLiquidConsumed<=0 且 ammoMultiplier!=1 且 displayAmmoMultiplier
        if(a.statLiquidConsumed <= 0f && !Mathf.equal(a.ammoMultiplier, 1f) && a.displayAmmoMultiplier){
            sep(parent, Core.bundle.format("bullet.multiplier", (int)a.ammoMultiplier));
        }
        // 【条目6】射速修正 %：原版条件 reloadMultiplier != 1
        if(!Mathf.equal(a.reloadMultiplier, 1f)){
            int val = (int)(a.reloadMultiplier * 100 - 100);
            sep(parent, Core.bundle.format("bullet.reload", ammoStat(val)));
        }
        // 【条目7】击退：原版条件 knockback > 0
        if(a.knockback > 0){
            sep(parent, Core.bundle.format("bullet.knockback", Strings.autoFixed(a.knockback, 2)));
        }
        // 【条目8】治疗百分比 %：原版条件 healPercent > 0
        if(a.healPercent > 0f){
            sep(parent, Core.bundle.format("bullet.healpercent", Strings.autoFixed(a.healPercent, 2)));
        }
        // 【条目9】直接治疗：原版条件 healAmount > 0
        if(a.healAmount > 0f){
            sep(parent, Core.bundle.format("bullet.healamount", Strings.autoFixed(a.healAmount, 2)));
        }
        // 【条目10】燃烧（incendiary）：原版条件 incendAmount > 0
        if(a.incendAmount > 0){
            sep(parent, "@bullet.incendiary");
        }
        // 【条目11】制导（homing）：原版条件 homingPower > 0.01
        if(a.homingPower > 0.01f){
            sep(parent, "@bullet.homing");
        }
        // 【条目12】闪电：原版条件 lightning > 0（显示数量 + 伤害，伤害未显式设置时用 damage）
        if(a.lightning > 0){
            sep(parent, Core.bundle.format("bullet.lightning", a.lightning, a.lightningDamage < 0 ? a.damage : a.lightningDamage));
        }
        // 【条目13】激光闪电间隔（LaserBulletType）：原版条件 lightningSpacing > 0（含闪电数量+伤害，及间隔/长度说明行）
        if(a instanceof LaserBulletType b && b.lightningSpacing > 0){
            int count = (int)(b.length / b.lightningSpacing) * 2 + 2;
            float damage = b.lightningDamage < 0 ? b.damage : b.lightningDamage;
            sep(parent, Core.bundle.format("bullet.lightning", count, damage));
            String length = b.lightningLengthRand > 0 ?
                Strings.format("@[]-[stat]@", Strings.autoFixed(b.lightningLength, 2), Strings.autoFixed(b.lightningLength + b.lightningLengthRand, 2))
                : Strings.autoFixed(b.lightningLength, 2);
            note(parent, Core.bundle.format("bullet.lightninginterval", Strings.autoFixed(b.lightningSpacing / tilesize, 2), length)).left();
        }
        // 【条目14】EMP（EmpBulletType）：原版条件 radius > 0（含半径 / 超频 / 减速 / 伤害 四行）
        if(a instanceof EmpBulletType b && b.radius > 0f){
            sep(parent, Core.bundle.format("bullet.empradius", Strings.fixed(b.radius / tilesize, 1)));
            if(b.timeDuration > 0f && b.timeIncrease > 1f){
                sep(parent, Core.bundle.format("bullet.empboost",
                    Strings.autoFixed(b.timeIncrease * 100f, 2),
                    Strings.autoFixed(b.timeDuration / 60f, 1)) + " " + StatUnit.seconds.localized());
            }
            if(b.timeDuration > 0f && b.powerSclDecrease < 1f){
                sep(parent, Core.bundle.format("bullet.empslowdown",
                    (b.powerSclDecrease < 1f ? "[negstat]" : "") + Strings.autoFixed((b.powerSclDecrease - 1f) * 100f, 2),
                    Strings.autoFixed(b.timeDuration / 60f, 1)) + " " + StatUnit.seconds.localized());
            }
            if(!Mathf.equal(b.powerDamageScl, 1f)){
                sep(parent, Core.bundle.format("bullet.empdamage", Strings.autoFixed(b.powerDamageScl * 100f, 2)));
            }
            if(b.hitUnits){
                sep(parent, Core.bundle.format("bullet.empunitdamage",
                    (b.unitDamageScl < 1f ? "[negstat]" : "") + Strings.autoFixed(b.unitDamageScl * 100f, 2)));
            }
        }
        // 【条目15】状态效果：原版条件 status != none 且 statusChance > 0（含概率、图标、持续秒数，可悬停查看状态详情）
        if(a.status != StatusEffects.none && a.statusChance > 0f){
            sep(parent, StatValues.statusText(a.status, a.statusDuration, a.statusChance))
                .with(c -> StatValues.withTooltip(c, a.status));
        }
        // 【条目16】忽略导弹：原版条件 targetMissiles == false
        if(!a.targetMissiles){
            sep(parent, "@bullet.notargetsmissiles");
        }
        // 【条目17】忽略建筑：原版条件 targetBlocks == false
        if(!a.targetBlocks){
            sep(parent, "@bullet.notargetsbuildings");
        }
        // 【条目18】间隔子弹：原版条件 intervalBullet != null（可展开，递归复用 StatValues.ammo）
        if(a.intervalBullet != null){
            parent.row();
            Table ic = new Table();
            StatValues.ammo(ObjectMap.of(item, a.intervalBullet), true, false).display(ic);
            Collapser coll = new Collapser(ic, true);
            coll.setDuration(0.1f);
            parent.table(it -> {
                it.left().defaults().left();
                it.add(Core.bundle.format("bullet.interval", Strings.autoFixed(a.intervalBullets / a.bulletInterval * 60, 2)));
                it.button(Icon.downOpen, Styles.emptyi, () -> coll.toggle(false)).update(i -> i.getStyle().imageUp = (!coll.isCollapsed() ? Icon.upOpen : Icon.downOpen)).size(8).padLeft(16f).expandX();
            });
            parent.row();
            parent.add(coll);
        }
        // 【条目19】分裂子弹：原版条件 fragBullet != null（可展开，递归复用 StatValues.ammo）
        if(a.fragBullet != null){
            parent.row();
            Table fc = new Table();
            StatValues.ammo(ObjectMap.of(item, a.fragBullet), true, false).display(fc);
            Collapser coll = new Collapser(fc, true);
            coll.setDuration(0.1f);
            parent.table(ft -> {
                ft.left().defaults().left();
                ft.add(Core.bundle.format("bullet.frags", a.fragBullets));
                ft.button(Icon.downOpen, Styles.emptyi, () -> coll.toggle(false)).update(i -> i.getStyle().imageUp = (!coll.isCollapsed() ? Icon.upOpen : Icon.downOpen)).size(8).padLeft(16f).expandX();
            });
            parent.row();
            parent.add(coll);
        }
        // 【条目20】生成子弹：原版条件 spawnBullets != null 且非空（可展开，仅对 showStats 的子弹递归显示）
        if(a.spawnBullets != null && a.spawnBullets.size > 0){
            parent.row();
            Table sc = new Table();
            for(BulletType spawn : a.spawnBullets){
                if(spawn.showStats) StatValues.ammo(ObjectMap.of(item, spawn), true, false).display(sc);
            }
            Collapser coll = new Collapser(sc, true);
            coll.setDuration(0.1f);
            parent.table(st -> {
                st.left().defaults().left();
                st.add(Core.bundle.format("bullet.spawnBullets", a.spawnBullets.size));
                if(sc.getChildren().size > 0) st.button(Icon.downOpen, Styles.emptyi, () -> coll.toggle(false)).update(i -> i.getStyle().imageUp = (!coll.isCollapsed() ? Icon.upOpen : Icon.downOpen)).size(8).padLeft(16f).expandX();
            });
            parent.row();
            parent.add(coll);
        }
    }

    /** 追加护甲反应弹药的横向伤害表（装甲 0→½baseArmor→baseArmor→3⁄2baseArmor，精确 1 位小数）。 */
    private void addArmorDamageTable(Table parent, ArmorReactiveBulletType b){
        float half = b.baseArmor / 2f;
        float threeHalf = b.baseArmor * 1.5f;
        float[] vals = {
            b.totalDamage(0f), half, b.totalDamage(half),
            b.baseArmor, b.baseDamage, threeHalf, b.totalDamage(threeHalf)
        };
        parent.table(whiteOutlined(Styles.grayPanel),dt -> {
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

    /**
     * 弹药专属说明行：bundle 存在 {@code key}（形如 block.&lt;炮塔名&gt;.&lt;弹药名&gt;.info）时才输出一行
     * “信息图标 + 说明文字”（等价 v160.7 StatValues.tableInfo 的私有实现）。
     */
    private Cell<?> tableInfo(Table table, String key){
        if(!Core.bundle.has(key)) return null;
        return table.table(t -> {
            if(!headless) t.image(Icon.info.getRegion()).size(20).color(Color.lightGray).scaling(Scaling.fit).padRight(8).padLeft(12);
            t.add("[lightgray]" + Core.bundle.get(key)).padBottom(10f);
        });
    }

    /** 分隔行：换行后追加一行文字（等价 v160.7 StatValues.sep 私有实现）。 */
    private Cell<Label> sep(Table table, String text){
        table.row();
        return table.add(text);
    }

    /** 带箭头的说明行（等价 v160.7 StatValues.note 私有实现，用于激光闪电间隔等子说明）。 */
    private Cell<?> note(Table table, String text){
        table.row();
        return table.table(t -> {
            if(!headless) t.image(Icon.arrowNoteSmall.getRegion()).size(15).color(Pal.stat).scaling(Scaling.fit).padRight(6).padLeft(12);
            t.add(text);
        });
    }

    /** 带 +/- 符号的数值格式化（等价 v160.7 StatValues.ammoStat 私有实现）。 */
    private String ammoStat(float val){
        return (val > 0 ? "[stat]+" : "[negstat]") + Strings.autoFixed(val, 1);
    }
}
