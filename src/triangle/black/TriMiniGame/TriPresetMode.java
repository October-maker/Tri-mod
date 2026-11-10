package triangle.black.TriMiniGame;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.ObjectFloatMap;
import arc.util.Timer;
import arc.func.Cons;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.RailBulletType;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.RulesLoadEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.game.EventType.WorldLoadEndEvent;
import mindustry.game.Gamemode;
import mindustry.game.Rules;
import mindustry.game.SpawnGroup;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.maps.Map;
import mindustry.world.Tile;
import triangle.content.TriUnits;

/**
 * “特殊模式”的启动入口与词条状态。
 *
 * <p>词条按类别分组：同一类别的选项按钮纵向排列，同类选项中至多选中一个
 * （选中一个即清除同类其它选项），都不选中时该项不生效——例如出怪倍率的两个选项都不选即为默认 1x。
 *
 * <p>词条是<b>数据驱动</b>的：每个 {@link Option} 自带一个 {@code apply}，选中后由
 * {@link #applyModifiers(Rules)} 统一执行，新增词条只需往 {@code categories} 里加一项，
 * 不必再改任何分支代码；右侧信息栏通过 {@link #selectedDetails()} 逐条列出已选项的总效果。
 *
 * <p>敌方数值走<b>事件钩子 + 实例级改写</b>（本构建里 {@code Rules.unitHealthMultiplier}
 * 等乘子没有被战斗逻辑读取）：词条只把倍率写进静态配置，真正生效在 {@link #init()} 注册的
 * {@code UnitCreateEvent}（单位血量/伤害）、{@code Trigger.update} 里的子弹逐帧扫描（炮台与
 * 单位子弹伤害）、{@code BlockBuildEndEvent} / {@code WorldLoadEndEvent}（建筑耐久）里。
 * 建筑没有 per-team 装甲（{@code Block.armor} 是类型级、双方共用），故只能抬耐久。
 *
 * <p>地图文件位于 {@code assets/maps/battleplan-planetaryTerminal.msav}。
 */
public final class TriPresetMode{

    public static final String MAP_NAME = "battleplan-planetaryTerminal";
    public static final String MAP_TITLE = "Planetary Launch Terminal";

    /**
     * 增援词条：起止波次与每波额外数量。
     *
     * <p>注意波次编号的口径：{@code Logic.runWave()} 是先 {@code spawnEnemies()}、后 {@code state.wave++}，
     * 而 {@code WaveSpawner.spawnEnemies()} 传给 {@code SpawnGroup.getSpawned()} 的参数是
     * {@code state.wave - 1}。也就是说界面显示“第 1 波”时，getSpawned 收到的是 0。
     * 想让界面上的第 1~10 波生效，begin/end 必须写 0/9；写成 1/10 会整体推迟一波。
     */
    private static final int REINFORCE_BEGIN = 0, REINFORCE_END = 9, REINFORCE_AMOUNT = 5;

    /** 敌方强化配置：由词条的 apply 写入，事件钩子读取；1f / 0f 表示不强化。每次开局前由 start() 重置。 */
    private static float enemyUnitHealth = 1f, enemyUnitDamage = 1f, enemyUnitArmor = 0f;
    private static float enemyBlockHealth = 1f, enemyTurretDamage = 1f;

    /** 我方削弱配置：同上，1f 表示不削弱。 */
    private static float playerUnitHealth = 1f, playerUnitDamage = 1f;
    private static float playerBlockHealth = 1f, playerTurretDamage = 1f;

    /**
     * 增援词条是否被选中。
     *
     * <p>选中只置这个标记，真正往波次表追加生成组要等 {@link RulesLoadEvent}：
     * 载入前那份 {@code Rules} 的 {@code spawns} 会在加载地图时被整个替换掉（原因见
     * {@link #applyReinforce(Rules)}）。
     */
    private static boolean reinforceEnabled = false;

    /**
     * 建筑耐久的强化范围是否包含“中立（derelict）”等非玩家队伍的建筑。
     * 地图自带的废墟/墙/电池常常是 derelict，只认 waveTeam 会把它们漏掉。
     */
    private static final boolean BUFF_NON_PLAYER_BUILDS = true;

    /** 本模式启动的对局标记：进入对局后调用 saves.resetSave()，让退出菜单只剩“不保存退出”。 */
    private static boolean presetSession, enteredPlaying;
    /** 已强化过的敌方单位 id，保证同一只单位只乘一次（创建钩子与兜底扫描都走它）。 */
    private static final arc.struct.IntSet buffedUnits = new arc.struct.IntSet();
    /** 已经乘过伤害的“这一次生成”的子弹（子弹池化复用，需要按生成周期区分）。 */
    private static final java.util.IdentityHashMap<Bullet, Boolean> scaledBullets = new java.util.IdentityHashMap<>();
    /** 玩家方建筑的上一帧血量，用于给“生成即结算”的弹种做受击端补差额。 */
    private static final ObjectFloatMap<Building> prevBuildingHealth = new ObjectFloatMap<>();
    /** 单位上一帧的血量（按 {@code unit.id}），用于减伤方向的反推补偿。 */
    private static final arc.struct.IntFloatMap prevUnitHealth = new arc.struct.IntFloatMap();
    /** 本帧收集到的“生成即结算”弹道，逐帧重建（复用同一个 Seq，避免每帧分配对象）。 */
    private static final Seq<Bullet> instantBeams = new Seq<>();

    /** 类别中的一个可选项：显示文案 + 详情文案 + 选中时施加的改动。 */
    private static final class Option{
        final String label;
        final boolean literal;
        final String detailKey;
        /** 选中该项时执行：可直接改 rules，也可写敌方强化的静态配置（由事件钩子读取）。 */
        final Cons<Rules> apply;
        /** 自定义词条价值；{@code -1} 表示未指定，按“选项档位”取（同类第 1/2/3 个选项记 1/2/3）。 */
        final int customValue;

        Option(String label, boolean literal, String detailKey, Cons<Rules> apply){
            this(label, literal, detailKey, apply, -1);
        }

        /** 带自定义价值的构造：{@code value} 为该词条的显示价值（1~3 等任意整数，-1 表示按档位）。 */
        Option(String label, boolean literal, String detailKey, Cons<Rules> apply, int value){
            this.label = label;
            this.literal = literal;
            this.detailKey = detailKey;
            this.apply = apply;
            this.customValue = value;
        }

        String text(){
            return literal ? label : Core.bundle.get(label, label);
        }

        String detail(){
            return Core.bundle.get(detailKey, detailKey);
        }
    }

    /**
     * 已选词条的结构化条目：名称 / 价值 / 描述，供右侧详情逐条渲染“名称 + 价值数字 + 描述”的排版。
     *
     * <p>价值取值：优先用 {@link Option#customValue}（若 ≥ 0），否则按该选项在本类别中的档位
     * （同类第 1/2/3 个选项分别记 1/2/3；单选项记 1）。
     */
    public static final class Entry{
        public final String name;
        public final int value;
        public final String detail;

        Entry(String name, int value, String detail){
            this.name = name;
            this.value = value;
            this.detail = detail;
        }
    }

    /** 一个词条类别；selected 为当前选中的选项下标，-1 表示同类选项都未选中（该类不生效）。 */
    private static final class Category{
        final Seq<Option> options;
        int selected = -1;

        Category(Option... options){
            this.options = Seq.with(options);
        }
    }

    /**
     * 词条类别。顺序即界面上的列顺序；只有<b>一个</b>选项的类别不会单独占一列，
     * 而是统一收进右侧那个定高网格 table（坐标见 {@link #singleCategory(int)}）。
     *
     * - 出怪倍率：1.5x / 2x（waveSpacing 除以对应倍率；都不选为默认 1x）；
     * - 战争迷雾：fog/staticFog（单选项）；
     * - 敌方单位生命 / 敌方单位伤害：各三段式 ×2 / ×3 / ×5（实例级，互相独立）；
     * - 敌方建筑生命：+100% / +200% / +400%（实例级耐久）；
     * - 敌方炮台伤害：+100% / +200% / +400%（敌方建筑发射的子弹实例级）；
     * - 波次增援：第 1~10 波每波额外 5 个 triangle-coreUnit（单选项）；
     * - 我方单位伤害 / 我方炮台伤害 / 我方建筑生命 / 我方单位生命：各三段式 -25% / -50% / -75%。
     */
    private static final Seq<Category> categories = Seq.with(
            new Category(
                    new Option("1.5x", true, "tri.preset.detail.waveRate.x15", r -> r.waveSpacing /= 1.5f),
                    new Option("2x", true, "tri.preset.detail.waveRate.x2", r -> r.waveSpacing /= 2f)),
            new Category(
                    new Option("tri.preset.value.fog", false, "tri.preset.detail.fog.on", r -> {
                        r.fog = true;
                        r.staticFog = true;
                    })),
            new Category(
                    new Option("+100%", true, "tri.preset.detail.unitHealth.x1", r -> enemyUnitHealth = 2f),
                    new Option("+200%", true, "tri.preset.detail.unitHealth.x2", r -> enemyUnitHealth = 3f),
                    new Option("+400%", true, "tri.preset.detail.unitHealth.x3", r -> enemyUnitHealth = 5f)),
            new Category(
                    new Option("+100%", true, "tri.preset.detail.unitDamage.x1", r -> enemyUnitDamage = 2f),
                    new Option("+200%", true, "tri.preset.detail.unitDamage.x2", r -> enemyUnitDamage = 3f),
                    new Option("+400%", true, "tri.preset.detail.unitDamage.x3", r -> enemyUnitDamage = 5f)),
            new Category(
                    new Option("+100%", true, "tri.preset.detail.blockHealth.x1", r -> enemyBlockHealth = 2f),
                    new Option("+200%", true, "tri.preset.detail.blockHealth.x2", r -> enemyBlockHealth = 3f),
                    new Option("+400%", true, "tri.preset.detail.blockHealth.x3", r -> enemyBlockHealth = 5f)),
            new Category(
                    new Option("+100%", true, "tri.preset.detail.turret.x1", r -> enemyTurretDamage = 2f),
                    new Option("+200%", true, "tri.preset.detail.turret.x2", r -> enemyTurretDamage = 3f),
                    new Option("+400%", true, "tri.preset.detail.turret.x3", r -> enemyTurretDamage = 5f)),
            new Category(
                    new Option("tri.preset.value.reinforce", false, "tri.preset.detail.reinforce.on",
                            TriPresetMode::applyReinforce)),
            new Category(
                    new Option("-25%", true, "tri.preset.detail.playerUnitDamage.x1", r -> playerUnitDamage = 0.75f),
                    new Option("-50%", true, "tri.preset.detail.playerUnitDamage.x2", r -> playerUnitDamage = 0.5f),
                    new Option("-75%", true, "tri.preset.detail.playerUnitDamage.x3", r -> playerUnitDamage = 0.25f)),
            new Category(
                    new Option("-25%", true, "tri.preset.detail.playerTurret.x1", r -> playerTurretDamage = 0.75f),
                    new Option("-50%", true, "tri.preset.detail.playerTurret.x2", r -> playerTurretDamage = 0.5f),
                    new Option("-75%", true, "tri.preset.detail.playerTurret.x3", r -> playerTurretDamage = 0.25f)),
            new Category(
                    new Option("-25%", true, "tri.preset.detail.playerBlockHealth.x1", r -> playerBlockHealth = 0.75f),
                    new Option("-50%", true, "tri.preset.detail.playerBlockHealth.x2", r -> playerBlockHealth = 0.5f),
                    new Option("-75%", true, "tri.preset.detail.playerBlockHealth.x3", r -> playerBlockHealth = 0.25f)),
            new Category(
                    new Option("-25%", true, "tri.preset.detail.playerUnitHealth.x1", r -> playerUnitHealth = 0.75f),
                    new Option("-50%", true, "tri.preset.detail.playerUnitHealth.x2", r -> playerUnitHealth = 0.5f),
                    new Option("-75%", true, "tri.preset.detail.playerUnitHealth.x3", r -> playerUnitHealth = 0.25f))
    );

    private TriPresetMode(){}

    /** 注册敌方/我方数值的事件钩子；由 triangle 主类在启动时调用一次。 */
    public static void init(){
        // 单位（双方）：创建瞬间改写实例数值。maxHealth/health 是字段（不会随倍率自动重算），
        // damageMultiplier 会在其武器的伤害结算中被读取。
        Events.on(UnitCreateEvent.class, e -> applyUnitBuff(e.unit));

        // 首帧即结算的弹种（激光/轨道/榴霰）对“单位”的受击补偿：
        // Damage 在命中每个单位后都会 fire UnitDamageEvent，带上被击单位与那颗子弹，
        // 已经结算的基础伤害就是 b.damage()，这里把缺的 (倍率-1) 倍补上。
        Events.on(UnitDamageEvent.class, e -> {
            if(e.unit == null || e.bullet == null) return;
            Bullet b = e.bullet;
            if(b.type == null || !isInstantHitType(b.type)) return;
            if(b.team == null || b.team == Vars.state.rules.defaultTeam) return;
            float mult = b.owner instanceof Building ? enemyTurretDamage : enemyUnitDamage;
            if(mult <= 1f) return;
            e.unit.damage(b.damage() * (mult - 1f));
        });

        // 兜底：每秒扫一遍实体组，把“没走创建事件”的单位（引擎内部生成 / 换队后的单位）补上
        Timer.schedule(() -> {
            if(Vars.state == null || !Vars.state.isGame()) return;
            if(!unitBuffActive()) return;

            for(Unit u : Groups.unit){
                applyUnitBuff(u);
            }
        }, 1f, 1f);

        // 子弹（双方）：BulletCreateEvent 在炮台/单位的开火路径上不会触发（只有 BulletType.createBullet 这个
        // 逻辑/网络专用工具方法才 fire），所以改为逐帧扫描 Groups.bullet，按“阵营 + owner 类型”取倍率：
        // - 非玩家阵营 · owner 是建筑（炮台）→ 敌方炮台倍率；owner 是其它（敌方单位）→ 敌方单位倍率；
        // - 玩家阵营：同理取我方炮台 / 我方单位倍率（削弱时倍率 < 1）。
        // 去重：子弹对象是池化复用的，且 `justSpawned` 在 Bullet.update() 开头就被清掉
        // （Trigger.update 在实体更新之后才 fire，永远看不到它），所以不能按“年龄”判定。
        // 改为幂等式判定：以 BulletType.damage 为基础值，只有“当前伤害仍等于基础值”时才缩一次；
        // 缩放之后 damage ≠ 基础值（放大或缩小都是），自然跳过；池化复用时会 create 把 damage
        // 重置回基础值，于是又能被识别为“新的一发”。注意判定必须是“接近基础值”而不是
        // “不超过基础值”，否则减伤会被每帧重复乘，几下就归零。
        Events.run(Trigger.update, () -> {
            if(Vars.state == null || !Vars.state.isGame()) return;
            if(!bulletBuffActive()) return;

            for(Bullet b : Groups.bullet){
                if(b == null || b.type == null || b.team == null) continue;

                boolean buildingOwner = b.owner instanceof Building;
                float mult = b.team == Vars.state.rules.defaultTeam
                        ? (buildingOwner ? playerTurretDamage : playerUnitDamage)
                        : (buildingOwner ? enemyTurretDamage : enemyUnitDamage);
                if(mult == 1f) continue;

                // 激光 / 轨道 / 榴霰：伤害在 Bullet.add() 调 type.init(b) 时就结算完了，
                // 逐帧改 b.damage 对它们没有任何意义，交给受击端补偿。
                if(isInstantHitType(b.type)) continue;

                float base = b.type.damage;
                float cur = b.damage();
                if(base > 0f){
                    // 仍处于基础伤害 → 这一发生命周期还没缩放
                    if(cur > base * 1.01f || cur < base * 0.99f) continue;
                }else{
                    // 没有基础伤害参考的弹种，退回身份去重（一次性）
                    if(scaledBullets.put(b, Boolean.TRUE) != null) continue;
                }

                b.damage(cur * mult);
            }

            // 生成即结算弹种的受击端补偿：单位目标（双方）与建筑目标（双方）
            compensateInstantHits();
        });

        // 建筑（双方）：AI 新建 / 重建成时按各自阵营的倍率改耐久（建筑无法按队伍改装甲，耐久是等效手段）。
        Events.on(BlockBuildEndEvent.class, e -> {
            if(e.breaking || e.tile == null || e.tile.build == null) return;
            scaleBuilding(e.tile.build);
        });

        // 波次增援：这个事件在 Control.playMap 里“state.rules = rules”之后才 fire，
        // 此时 rules.spawns 就是本局最终使用的那份波次表，追加不会再被 retainContentFields 覆盖。
        Events.on(RulesLoadEvent.class, e -> {
            if(!presetSession) return;
            applyReinforceGroups(e.rules);
        });

        // 建筑（双方）：地图自带的存量建筑，世界加载完成后遍历一次。
        // 这里先按当前选择重算一遍静态配置：playMap 内部（Logic.reset / 切状态）会触发
        // StateChangeEvent，把我们前面写好的强化清空——若不在此重算，钩子读到的永远是 1f 的空配置。
        //
        // 覆盖面：先遍历 Groups.build，再遍历世界瓦片（{@code Vars.world.tiles}）补齐
        // 那些存在于瓦片上却没有进实体组的方块（墙、电力设施这类地图预置方块最容易出现这种差异）。
        Events.on(WorldLoadEndEvent.class, e -> {
            applyEnemyConfig();

            // 存量建筑：Groups.build 与瓦片两路都扫（墙、电力设施这类地图预置方块
            // 有时只存在于瓦片上、没进实体组），同一座多格建筑用 IdentityHashMap 去重。
            if(enemyBlockHealth != 1f || playerBlockHealth != 1f){
                java.util.IdentityHashMap<Building, Boolean> seen = new java.util.IdentityHashMap<>();

                for(Building b : Groups.build){
                    if(b == null) continue;
                    seen.put(b, Boolean.TRUE);
                    scaleBuilding(b);
                }

                for(Tile t : Vars.world.tiles){
                    Building b = t.build;
                    if(b == null) continue;
                    if(seen.put(b, Boolean.TRUE) != null) continue;   // 已在实体组里处理过
                    scaleBuilding(b);
                }
            }

            // 地图预置的单位在加载时就存在，创建钩子那一刻静态配置可能还是 1f，这里补一次
            for(Unit u : Groups.unit){
                applyUnitBuff(u);
            }
        });

        // 进入对局：标记本模式的局已真正开始，并把当前存档槽置空 —— 之后暂停菜单里的退出按钮
        // 会变成“退出”（而不是“保存并退出”），自动存档也不会写入；已有存档一概不动。
        // 离开对局：清空强化（没有 per-team 的 Rules 血量乘子可用）。
        Events.on(StateChangeEvent.class, e -> {
            if(e.to == State.playing){
                enteredPlaying = presetSession;
                if(enteredPlaying) lockSaving();
                return;
            }
            if(e.to == State.paused && presetSession){
                lockSaving(); // 暂停菜单的按钮文案按当前存档槽决定，打开前再锁一次
                return;
            }
            if(!presetSession || !enteredPlaying) return;

            presetSession = false;
            enteredPlaying = false;
            resetEnemyBuff();
        });
    }

    /**
     * 让本局只能在暂停菜单里“不保存退出”：把当前存档槽置空（{@code Saves.resetSave()} 只是 current = null）。
     * 原版暂停菜单的退出按钮据此显示为“退出”而非“保存并退出”，同时自动存档失去目标槽位。
     */
    private static void lockSaving(){
        if(Vars.control == null || Vars.control.saves == null) return;
        if(Vars.control.saves.getCurrent() == null) return;

        Vars.control.saves.resetSave();
    }

    /** 预设模式使用的自带地图；每次调用重新查找（地图可能被重载）。 */
    public static Map map(){
        return Vars.maps == null ? null : Vars.maps.moddedMaps()
                .find(m -> MAP_NAME.equals(m.file.nameWithoutExtension())
                        || MAP_TITLE.equals(m.name()));
    }

    public static int categoryCount(){
        return categories.size;
    }

    public static int optionCount(int category){
        return categories.get(category).options.size;
    }

    public static String optionLabel(int category, int option){
        return categories.get(category).options.get(option).text();
    }

    public static boolean isSelected(int category, int option){
        return categories.get(category).selected == option;
    }

    /** 当前所有已选中词条的详情文案（按类别顺序），供右侧“总效果”逐条显示。 */
    public static Seq<String> selectedDetails(){
        Seq<String> out = new Seq<>();
        for(Category c : categories){
            if(c.selected >= 0) out.add(c.options.get(c.selected).detail());
        }
        return out;
    }

    /**
     * 当前所有已选中词条的结构化条目（名称 / 价值 / 描述，按类别顺序）。
     * 价值默认按选项档位（同类第 1/2/3 个选项记 1/2/3，单选项记 1），除非该选项设了自定义价值。
     */
    public static Seq<Entry> selectedEntries(){
        Seq<Entry> out = new Seq<>();
        for(Category c : categories){
            if(c.selected < 0) continue;
            Option o = c.options.get(c.selected);
            int value = o.customValue >= 0 ? o.customValue : (c.selected + 1);
            out.add(new Entry(o.text(), value, o.detail()));
        }
        return out;
    }

    /** 所有已选词条的价值总和（用于详情右上角“总价值”统计）。 */
    public static int selectedValueTotal(){
        int sum = 0;
        for(Entry e : selectedEntries()) sum += e.value;
        return sum;
    }

    /**
     * 切换某个选项：已选中则取消（回到该类默认状态）；未选中则选中，同时清除同类其它选项
     * （同类选项互斥，任何时刻至多一个生效）。
     */
    public static void toggle(int category, int option){
        Category c = categories.get(category);
        c.selected = c.selected == option ? -1 : option;
    }

    /**
     * 该类别是否只有一个选项（如战争迷雾、波次增援）。
     * 这类词条不单独占一列，统一收在界面最右侧的定高网格 table 里。
     */
    public static boolean isSingleCategory(int category){
        return optionCount(category) == 1;
    }

    /** 单选项词条组的个数，即网格 table 里按钮的总数。 */
    public static int singleCategoryCount(){
        int n = 0;
        for(int c = 0; c < categories.size; c++){
            if(isSingleCategory(c)) n++;
        }
        return n;
    }

    /**
     * 第 {@code index} 个单选项词条组在 {@link #categories} 里的下标。
     * 网格坐标由调用方按“每列最多 3 个、先竖后横”换算：第 index 个位于
     * 第 {@code index / 3} 列（从 0 起算、从左往右）、第 {@code index % 3} 行（从上往下）。
     */
    public static int singleCategory(int index){
        int n = 0;
        for(int c = 0; c < categories.size; c++){
            if(isSingleCategory(c) && n++ == index) return c;
        }
        throw new IndexOutOfBoundsException("single category index " + index);
    }

    /** 是否有任一词条被选中；全未选中时界面回到地图缩略图。 */
    public static boolean hasSelection(){
        for(Category c : categories){
            if(c.selected >= 0) return true;
        }
        return false;
    }

    /** 以当前词条状态新建一局自定义游戏。 */
    public static void start(){
        Map map = map();
        // 地图缺失时不启动（避免 playMap(null) 抛异常）；界面侧无需额外提示
        if(map == null) return;

        resetEnemyBuff();
        Rules rules = map.applyRules(Gamemode.survival).copy();
        applyModifiers(rules);

        // 本模式的局：进入 playing 后会把当前存档槽置空，退出时只能不保存
        presetSession = true;
        enteredPlaying = false;

        Vars.control.playMap(map, rules, false);
    }

    /** 数据驱动：只执行当前选中项的 apply；加词条不需要改这里。 */
    private static void applyModifiers(Rules rules){
        for(Category c : categories){
            if(c.selected >= 0 && c.options.get(c.selected).apply != null){
                c.options.get(c.selected).apply.get(rules);
            }
        }
    }

    /**
     * 只重算敌方强化的静态配置，不改任何 rules 值：用一个临时 Rules 跑一遍各选中项的 apply，
     * 出怪倍率/迷雾那几项落在临时对象上随即丢弃，单位/建筑/炮台强化则落回静态字段。
     * 世界加载完成时调用一次，确保钩子读到的是“当前选择”，而不是被状态切换清空后的默认值。
     */
    private static void applyEnemyConfig(){
        resetEnemyBuff();
        applyModifiers(new Rules());
    }

    /**
     * 波次增援词条：这一步只置标记，不碰传入的 {@code Rules}。
     *
     * <p>为什么不能在这里追加生成组：{@code Control.playMap} 的流程是
     * {@code Logic.reset()} → {@code world.loadMap(map, rules)} →
     * {@code rules.retainContentFields(state.rules)} → {@code state.rules = rules}。
     * 而 {@code retainContentFields} 的第一条字节码就是 {@code this.spawns = other.spawns}
     * （偏移 0~5），也就是把整个波次表换回地图自带的那份，
     * 载入前往 {@code rules.spawns} 里追加的生成组必然被丢弃。
     * 因此真正写入放在 {@link RulesLoadEvent}（它在 {@code state.rules = rules} 之后 fire）。
     */
    private static void applyReinforce(Rules ignored){
        reinforceEnabled = true;
    }

    /**
     * 把增援生成组追加进本局最终的波次表。
     *
     * <p>先复制一份 {@code Seq} 再追加，避免直接改动可能被默认波次表/地图规则共用的那个实例
     * （否则本局结束后，默认波次表会永久多出一条 coreUnit 生成组）。
     */
    private static void applyReinforceGroups(Rules rules){
        if(!reinforceEnabled || rules == null || TriUnits.core == null) return;

        Seq<SpawnGroup> spawns = rules.spawns == null ? new Seq<>() : new Seq<>(rules.spawns);

        SpawnGroup group = new SpawnGroup(TriUnits.core);
        group.begin = REINFORCE_BEGIN;
        group.end = REINFORCE_END;
        group.spacing = 1;              // 每个波次都刷
        group.unitAmount = REINFORCE_AMOUNT;
        group.max = REINFORCE_AMOUNT;   // getSpawned 末尾 Math.min(…, max)
        // unitScaling 保持默认的极大值（它是除数，设 0 会得到 +∞），这样每波固定 5 只
        spawns.add(group);
        rules.spawns = spawns;
    }

    /**
     * 强化 / 削弱一个单位：血量上限与当前血量按倍率改写，伤害乘子累乘，护甲做加法（只有敌方有护甲词条）。
     * 用 {@link #buffedUnits} 记录已处理过的单位 id，避免“创建钩子 + 加载补扫 + 每秒兜底”把同一只单位算多次。
     */
    private static void applyUnitBuff(Unit unit){
        if(unit == null) return;

        float health, damage, armor;
        if(unit.team == Vars.state.rules.waveTeam){
            health = enemyUnitHealth; damage = enemyUnitDamage; armor = enemyUnitArmor;
        }else if(unit.team == Vars.state.rules.defaultTeam){
            health = playerUnitHealth; damage = playerUnitDamage; armor = 0f;
        }else{
            return;   // 中立等其它队伍的单位不在词条范围内
        }
        if(health == 1f && damage == 1f && armor == 0f) return;
        if(!buffedUnits.add(unit.id)) return;

        float max = unit.maxHealth() * health;
        unit.maxHealth(max);
        unit.health(max);
        if(damage != 1f) unit.damageMultiplier(unit.damageMultiplier() * damage);
        if(armor != 0f) unit.armor(unit.armor() + armor);
    }

    /** 按该建筑所属阵营的倍率改写耐久上限并补满；倍率为 1 时不动。 */
    private static void scaleBuilding(Building build){
        if(build == null) return;
        float mult = blockMultiplier(build.team);
        if(mult == 1f) return;

        float max = build.maxHealth() * mult;
        build.maxHealth(max);
        build.health(max);
    }

    /**
     * 建筑耐久的作用范围：玩家阵营走我方倍率，波次阵营走敌方倍率；
     * 中立（derelict）等其它队伍在 {@link #BUFF_NON_PLAYER_BUILDS} 打开时跟随敌方倍率。
     * 地图自带的墙、电池常常是 derelict，只认 waveTeam 会把它们整片漏掉。
     */
    private static float blockMultiplier(Team team){
        if(team == Vars.state.rules.defaultTeam) return playerBlockHealth;
        if(team == Vars.state.rules.waveTeam) return enemyBlockHealth;
        return BUFF_NON_PLAYER_BUILDS ? enemyBlockHealth : 1f;
    }

    /**
     * 是否为“生成瞬间就结算伤害”的弹种。
     *
     * <p>这类子弹的伤害发生在 {@code Bullet.add()} 里的 {@code type.init(b)} 阶段，
     * 也就是开火当帧、任何逐帧钩子之前；事后改 {@code Bullet.damage} 已经无效。
     * 目前内核里属于这一类的是激光（{@link LaserBulletType}）、轨道（{@link RailBulletType}）
     * 与榴霰（{@link ShrapnelBulletType}）。连续光束（Continuous*）每帧读 {@code b.damage}，
     * 仍走子弹层缩放。
     */
    private static boolean isInstantHitType(BulletType type){
        return type instanceof LaserBulletType || type instanceof RailBulletType || type instanceof ShrapnelBulletType;
    }

    /**
     * “生成即结算”类弹种的受击端补偿（双方通用：建筑与单位都算）。
     *
     * <p>这类弹种在 {@code Bullet.add()} 当帧就结算完伤害，子弹层改不动；而 {@code EventType} 里
     * 只有 {@link UnitDamageEvent}，它只告诉我们“哪颗子弹命中了谁”，拿不到实际扣血量
     * （护甲、倍率都在 {@code Damage} 内部消化）。所以这里用两个可观测量反推：
     * <ol>
     *   <li>本帧收集场上仍存在的“生成即结算”弹道（起点、角度、{@code fdata} 记录的命中长度）；</li>
     *   <li>每个目标（单位/建筑）每帧留一次血量快照，掉落量就是这一帧实际被扣的血。</li>
     * </ol>
     *
     * <p>结算规则（同帧混有其它伤害时的统一口径）：以当前帧所有命中该目标的敌方弹道的
     * {@code type.damage} 之和作为“可归因份额”——掉落不超过该份额时，说明这一帧基本就是这几条
     * 弹道造成的，直接按整份掉落结算（护甲已经被扣进掉落里，最准）；掉落明显大于该份额时，
     * 说明同帧还有普通炮弹（它们已在子弹层缩放过），只对可归因份额结算，避免二次缩放。
     * 增伤与减伤走同一套公式（减伤时段额为负，表现为回血），并且追加量不会超过本帧掉落、
     * 也不会越过目标的生命区间。
     */
    private static void compensateInstantHits(){
        if(Vars.state == null || !Vars.state.isGame()) return;
        if(enemyTurretDamage == 1f && enemyUnitDamage == 1f
                && playerTurretDamage == 1f && playerUnitDamage == 1f) return;

        instantBeams.clear();
        for(Bullet b : Groups.bullet){
            if(b == null || b.type == null || b.team == null) continue;
            if(isInstantHitType(b.type)) instantBeams.add(b);
        }

        // 建筑：快照每帧都更新，否则倍率启用那一刻 prev 会退回很久以前的值，被误判成巨额伤害
        if(prevBuildingHealth.size > Groups.build.size() + 64) prevBuildingHealth.clear();
        for(Building bd : Groups.build){
            if(bd == null) continue;
            float now = bd.health();
            float prev = prevBuildingHealth.get(bd, -1f);
            prevBuildingHealth.put(bd, now);
            if(prev <= 0f || prev <= now) continue;

            float drop = prev - now;
            if(drop <= 0.5f || !attributeBeam(bd.x, bd.y, bd.block.size * 4f + 4f, bd.team)) continue;
            if(beamMult == 1f) continue;

            float portion = drop <= beamSum + 0.5f ? drop : beamSum;
            applyDelta(bd, portion * (beamMult - 1f), drop);
        }

        // 单位：增伤方向已有 UnitDamageEvent 的精确逐发路径，这里只处理减伤方向，避免重复补
        if(playerUnitDamage == 1f && playerTurretDamage == 1f) return;
        if(prevUnitHealth.size > Groups.unit.size() + 64) prevUnitHealth.clear();
        for(Unit u : Groups.unit){
            if(u == null) continue;
            float now = u.health();
            float prev = prevUnitHealth.get(u.id, -1f);
            prevUnitHealth.put(u.id, now);
            if(prev <= 0f || prev <= now) continue;

            float drop = prev - now;
            if(drop <= 0.5f) continue;
            if(!attributeBeam(u.x, u.y, u.type.hitSize * 0.5f + 6f, u.team)) continue;
            if(beamMult >= 1f) continue;

            float portion = drop <= beamSum + 0.5f ? drop : beamSum;
            applyDelta(u, portion * (beamMult - 1f), drop);
        }
    }

    /** 本帧可归因的弹道份额（各弹道 {@code type.damage} 之和）与按伤害加权的倍率，由 {@link #attributeBeam} 写入。 */
    private static float beamSum, beamMult;

    /**
     * 统计落在 (x, y, radius) 上、且不属于 {@code targetTeam} 的“生成即结算”弹道：
     * 返回 false 表示这一帧没有可归因的敌方弹道，调用方应跳过补偿。
     */
    private static boolean attributeBeam(float x, float y, float radius, Team targetTeam){
        beamSum = 0f;
        float weighted = 0f;
        for(Bullet b : instantBeams){
            if(b.team == targetTeam) continue;      // 自己人的弹道不算
            if(!beamHits(b, x, y, radius)) continue;
            float base = b.type.damage > 0f ? b.type.damage : 1f;
            beamSum += base;
            weighted += base * instantBeamMult(b);
        }
        if(beamSum <= 0f) return false;
        beamMult = weighted / beamSum;
        return true;
    }

    /** 该弹道所属阵营与 owner 类型对应的伤害倍率。 */
    private static float instantBeamMult(Bullet b){
        boolean buildingOwner = b.owner instanceof Building;
        if(b.team == Vars.state.rules.defaultTeam){
            return buildingOwner ? playerTurretDamage : playerUnitDamage;
        }
        return buildingOwner ? enemyTurretDamage : enemyUnitDamage;
    }

    /** 该点（半径 radius 内）是否落在一条“生成即结算”弹道的射线上。 */
    private static boolean beamHits(Bullet b, float x, float y, float radius){
        float angle = b.rotation();
        // collideLaser 会把实际命中长度写进 fdata，没有时退回一个宽松的默认长度
        float len = b.fdata > 0f ? b.fdata : 400f;
        float cos = Mathf.cos(angle), sin = Mathf.sin(angle);
        float dx = x - b.x, dy = y - b.y;
        float along = dx * cos + dy * sin;
        if(along < -4f || along > len + 4f) return false;
        return Math.abs(dy * cos - dx * sin) <= radius;
    }

    /**
     * 追加一份伤害（正）或回血（负）：不超过本帧掉落，也不越过生命区间。
     *
     * <p>回血必须直接写 {@code health()} 而不是 {@code damage(负数)}：{@code Unit.damage} 会走
     * {@code Damage.applyArmor(amount, armor)}，其实现是 {@code max(amount - armor, 0.1 * amount)}
     * —— 对负数来说两个候选值都被压成“负得更少”，实际只回 10% 左右。
     */
    private static void applyDelta(Building bd, float delta, float drop){
        if(delta > 0f){
            bd.damage(Math.min(delta, bd.health()));
        }else if(delta < 0f){
            float heal = Math.min(-delta, Math.min(drop, bd.maxHealth() - bd.health()));
            if(heal > 0f) bd.health(bd.health() + heal);
        }
    }

    /** 追加一份伤害（正）或回血（负）：同上，供单位复用。 */
    private static void applyDelta(Unit unit, float delta, float drop){
        if(delta > 0f){
            unit.damage(Math.min(delta, unit.health()));
        }else if(delta < 0f){
            float heal = Math.min(-delta, Math.min(drop, unit.maxHealth() - unit.health()));
            if(heal > 0f) unit.health(unit.health() + heal);
        }
    }

    /** 是否有任何单位倍率生效（双方），用于跳过每秒兜底扫描。 */
    private static boolean unitBuffActive(){
        return enemyUnitHealth != 1f || enemyUnitDamage != 1f || enemyUnitArmor != 0f
                || playerUnitHealth != 1f || playerUnitDamage != 1f;
    }

    /** 是否有任何子弹倍率生效（双方），用于跳过逐帧子弹扫描。 */
    private static boolean bulletBuffActive(){
        return enemyTurretDamage != 1f || enemyUnitDamage != 1f
                || playerTurretDamage != 1f || playerUnitDamage != 1f;
    }

    /** 清空强化 / 削弱配置（1f / 0f 表示不生效），并丢弃各类去重标记。 */
    private static void resetEnemyBuff(){
        enemyUnitHealth = 1f;
        enemyUnitDamage = 1f;
        enemyUnitArmor = 0f;
        enemyBlockHealth = 1f;
        enemyTurretDamage = 1f;
        playerUnitHealth = 1f;
        playerUnitDamage = 1f;
        playerBlockHealth = 1f;
        playerTurretDamage = 1f;
        reinforceEnabled = false;
        buffedUnits.clear();
        scaledBullets.clear();
        prevBuildingHealth.clear();
        prevUnitHealth.clear();
        instantBeams.clear();
    }
}
