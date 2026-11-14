package triangle.black.TriMiniGame;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.ObjectFloatMap;
import arc.util.Timer;
import arc.func.Cons;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
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
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.campaign.LandingPad;
import mindustry.world.blocks.campaign.LaunchPad;
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
     * “游戏难度”词条附带的两个乘子。
     *
     * <p>原版 {@code Difficulty} 的 {@code enemyHealthMultiplier / enemySpawnMultiplier /
     * waveTimeMultiplier} 只在战役里生效：{@code WaveSpawner} 与 {@code Logic} 都是先判
     * {@code state.isCampaign()}，为真才去读 {@code state.getPlanet().campaignRules.difficulty}
     * （{@code Rules} 本身并不继承 {@code CampaignRules}）。本模式是自定义局，planet 为 null，
     * 所以这三项要由模组自己实现：
     * <ul>
     *   <li>单位生命：{@code difficultyUnitHealth} 与敌方单位生命词条的倍率<b>相乘</b>后写进实例；</li>
     *   <li>每波敌军数量：{@code difficultySpawn} 在 {@link RulesLoadEvent} 里乘到各生成组的
     *       {@code unitAmount/max} 上（生成组的写入在那里才不会被 {@code retainContentFields} 覆盖）；</li>
     *   <li>波次间隔：词条的 apply 里直接 {@code waveSpacing *= …}，自然与出怪间隔词条相乘叠加。</li>
     * </ul>
     */
    /**
     * 未选任何难度时的默认基线：对齐原版 {@code Difficulty.casual}（“休闲”）——
     * 敌方单位生命 ×0.5、每波敌军数量 ×0.5、波次间隔 ×2（原版实证常量见类注释）。
     * 选了“正常”才回到 1/1/1 的基准。
     */
    private static final float DEFAULT_DIFFICULTY_HEALTH = 0.5f, DEFAULT_DIFFICULTY_SPAWN = 0.5f,
            DEFAULT_DIFFICULTY_TIME = 2f;

    private static float difficultyUnitHealth = DEFAULT_DIFFICULTY_HEALTH,
            difficultySpawn = DEFAULT_DIFFICULTY_SPAWN;
    /** 生成组是否已按难度放大过（同一局只放大一次，避免重复乘）。 */
    private static boolean spawnScaled;

    /**
     * 增援词条是否被选中。
     *
     * <p>选中只置这个标记，真正往波次表追加生成组要等 {@link RulesLoadEvent}：
     * 载入前那份 {@code Rules} 的 {@code spawns} 会在加载地图时被整个替换掉（原因见
     * {@link #applyReinforce(Rules)}）。
     */
    private static boolean reinforceEnabled = false;

    /**
     * “禁用任何发射台/接收台”词条是否被选中。
     *
     * <p>与增援同理，这里只置标记：真正的拉黑要等 {@link RulesLoadEvent}，
     * 因为 {@code retainContentFields} 在地图载入时可能把 {@code bannedBlocks} 整份换成地图自带的那份。
     */
    private static boolean launchPadsDisabled = false;

    /** 基础设置里默认放开的原版发射台/接收台（不含旧版 3x3 发射台）。 */
    private static final String[] PAD_NAMES = {"advanced-launch-pad", "landing-pad"};
    /** 禁用词条要拉黑的对象：任何发射台/接收台，旧版发射台也算在内。 */
    private static final String[] ALL_PAD_NAMES = {"advanced-launch-pad", "landing-pad", "launch-pad"};

    /**
     * 建筑耐久的强化范围是否包含“中立（derelict）”等非玩家队伍的建筑。
     * 地图自带的废墟/墙/电池常常是 derelict，只认 waveTeam 会把它们漏掉。
     */
    private static final boolean BUFF_NON_PLAYER_BUILDS = true;

    /** 本模式启动的对局标记：进入对局后调用 saves.resetSave()，让退出菜单只剩“不保存退出”。 */
    private static boolean presetSession, enteredPlaying;
    /** 接收台 Build 实现的覆写是否已经打过补丁（内容加载后只能成功一次，之后幂等跳过）。 */
    private static boolean padPatched;
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

    /**
     * 词条前置条件：必须先选中“另一个词条组里的某一档”，本词条才可选。
     *
     * <p>前置组是复数选项时有两种解锁模式：
     * {@link #any(String)} 选中其中任意一档即开放；{@link #every(String)} 必须整组全选才开放
     * （“全选开放”）；{@link #is(String, int)} 精确要求某一档，例如“毁灭”难度。
     */
    private static final class Req{
        /** 前置词条组的 {@link Category#key}。 */
        final String category;
        /** 需要的选项下标；{@code -1} 表示“任一选项”。 */
        final int option;
        /** 仅当 {@code option < 0} 时有效：true = 该组全部选项都必须选中。 */
        final boolean all;

        private Req(String category, int option, boolean all){
            this.category = category;
            this.option = option;
            this.all = all;
        }

        /** “选择一种就开放”：前置组有任何一档被选中即可。 */
        static Req any(String category){
            return new Req(category, -1, false);
        }

        /** “全选开放”：前置组的全部选项都必须被选中。 */
        static Req every(String category){
            return new Req(category, -1, true);
        }

        /** 精确要求前置组选中某一档。 */
        static Req is(String category, int option){
            return new Req(category, option, false);
        }

        boolean satisfied(){
            Category c = byKey(category);
            // 配置了不存在的前置：按“不锁”处理，避免一处笔误让整片词条永久不可用
            if(c == null) return true;

            if(all){
                for(int i = 0; i < c.options.size; i++){
                    if(c.selected != i) return false;
                }
                return true;
            }
            return option < 0 ? c.selected >= 0 : c.selected == option;
        }
    }

    /** 类别中的一个可选项：显示文案 + 详情文案 + 选中时施加的改动。 */
    private static final class Option{
        final String label;
        final boolean literal;
        final String detailKey;
        /** 选中该项时执行：可直接改 rules，也可写敌方强化的静态配置（由事件钩子读取）。 */
        final Cons<Rules> apply;
        /** 自定义词条价值；{@code -1} 表示未指定，按“选项档位”取（同类第 1/2/3 个选项记 1/2/3）。 */
        final int customValue;
        /** 该项自身的前置；{@code null} 表示无前置，始终可选。 */
        final Req req;

        Option(String label, boolean literal, String detailKey, Cons<Rules> apply){
            this(label, literal, detailKey, apply, -1, null);
        }

        /** 带自定义价值的构造：{@code value} 为该词条的显示价值（1~3 等任意整数，-1 表示按档位）。 */
        Option(String label, boolean literal, String detailKey, Cons<Rules> apply, int value){
            this(label, literal, detailKey, apply, value, null);
        }

        /** 带前置条件的构造：{@code req} 未满足时该词条整格变灰且不可点。 */
        Option(String label, boolean literal, String detailKey, Cons<Rules> apply, int value, Req req){
            this.label = label;
            this.literal = literal;
            this.detailKey = detailKey;
            this.apply = apply;
            this.customValue = value;
            this.req = req;
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
        /** 所属类别下标与选项下标：两者组合即该词条的唯一标识，供界面按 key 复用已渲染的词条框。 */
        public final int category, option;

        Entry(String name, int value, String detail, int category, int option){
            this.name = name;
            this.value = value;
            this.detail = detail;
            this.category = category;
            this.option = option;
        }
    }

    /** 一个词条类别；selected 为当前选中的选项下标，-1 表示同类选项都未选中（该类不生效）。 */
    private static final class Category{
        /** 稳定标识：前置条件按 key 引用类别，避免用下标（增删词条组会让下标整体漂移）。 */
        final String key;
        final Seq<Option> options;
        int selected = -1;
        /** 类别级前置：需全部满足，本组任何一档才可选（{@link #lockClass(Req)} 声明）。 */
        final Seq<Req> locks = new Seq<>();

        Category(String key, Option... options){
            this.key = key;
            this.options = Seq.with(options);
        }

        /** 给整组挂一条前置（可链式写在 {@link #categories} 的字面里）。 */
        Category lockClass(Req req){
            locks.add(req);
            return this;
        }

        /** 本组是否被前置锁住（整组变灰）。 */
        boolean locked(){
            for(Req r : locks){
                if(!r.satisfied()) return true;
            }
            return false;
        }
    }

    /** 按 {@link Category#key} 查类别；找不到返回 null。 */
    private static Category byKey(String key){
        for(Category c : categories){
            if(c.key.equals(key)) return c;
        }
        return null;
    }

    /** 词条类别。顺序即界面上的列顺序；只有<b>一个</b>选项的类别不会单独占一列，
     * 而是统一收进右侧那个定高网格 table（坐标见 {@link #singleCategory(int)}）。
     *
     * - 出怪间隔：0.8x / 0.66x / 0.5x（waveSpacing 乘以对应倍率；都不选为原版默认间隔）；
     * - 游戏难度：正常 / 困难 / 毁灭（对应原版战役难度：单位生命 ×1/×1.25/×1.5、
     *   每波敌军数量 ×1/×1.5/×2、波次间隔 ×1/×0.8/×0.6；与本模式其它词条相乘叠加）；
     *   一个都不选 = 原版“休闲”（×0.5 / ×0.5 / ×2，见 {@link #DEFAULT_DIFFICULTY_HEALTH}）；
     * - 战争迷雾：fog/staticFog（单选项）；
     * - 敌方单位生命 / 敌方单位伤害：各三段式 ×2 / ×3 / ×5（实例级，互相独立）；
     * - 敌方建筑生命：+100% / +200% / +400%（实例级耐久）；
     * - 敌方炮台伤害：+100% / +200% / +400%（敌方建筑发射的子弹实例级）；
     * - 波次增援：第 1~10 波每波额外 5 个 triangle-coreUnit（单选项）；
     * - 我方单位伤害 / 我方炮台伤害 / 我方建筑生命 / 我方单位生命：各三段式 -25% / -50% / -75%；
     * - 禁用发射台/接收台：单选项，价值固定 3 分（{@link Option#customValue}）。
     *
     * <p>前置（{@link Req}）：各三段式数值词条的<b>第三档</b>（+400% / -75%）要求先选中
     * “毁灭”难度，未选中时该格整格变灰、不可点；把前置取消后依赖项会被自动清除（见 {@link #pruneLocks()}）。
     */
    private static final Seq<Category> categories = Seq.with(
            new Category("waveInterval",
                    new Option("0.8x", true, "tri.preset.detail.waveInterval.x08", r -> r.waveSpacing *= 0.8f),
                    new Option("0.66x", true, "tri.preset.detail.waveInterval.x066", r -> r.waveSpacing *= 0.66f),
                    new Option("0.5x", true, "tri.preset.detail.waveInterval.x05", r -> r.waveSpacing *= 0.5f)),
            new Category("difficulty",
                    new Option("tri.preset.value.difficulty.normal", false, "tri.preset.detail.difficulty.normal",
                            r -> applyDifficulty(r, 1f, 1f, 1f)),
                    new Option("tri.preset.value.difficulty.hard", false, "tri.preset.detail.difficulty.hard",
                            r -> applyDifficulty(r, 1.25f, 1.5f, 0.8f)),
                    new Option("tri.preset.value.difficulty.eradication", false, "tri.preset.detail.difficulty.eradication",
                            r -> applyDifficulty(r, 1.5f, 2f, 0.6f))),
            new Category("fog",
                    new Option("tri.preset.value.fog", false, "tri.preset.detail.fog.on", r -> {
                        r.fog = true;
                        r.staticFog = true;
                    })),
            new Category("unitHealth",
                    new Option("+100%", true, "tri.preset.detail.unitHealth.x1", r -> enemyUnitHealth = 2f),
                    new Option("+200%", true, "tri.preset.detail.unitHealth.x2", r -> enemyUnitHealth = 3f),
                    new Option("+400%", true, "tri.preset.detail.unitHealth.x3", r -> enemyUnitHealth = 5f,
                            -1, Req.is("difficulty", 2))),
            new Category("unitDamage",
                    new Option("+100%", true, "tri.preset.detail.unitDamage.x1", r -> enemyUnitDamage = 2f),
                    new Option("+200%", true, "tri.preset.detail.unitDamage.x2", r -> enemyUnitDamage = 3f),
                    new Option("+400%", true, "tri.preset.detail.unitDamage.x3", r -> enemyUnitDamage = 5f,
                            -1, Req.is("difficulty", 2))),
            new Category("blockHealth",
                    new Option("+100%", true, "tri.preset.detail.blockHealth.x1", r -> enemyBlockHealth = 2f),
                    new Option("+200%", true, "tri.preset.detail.blockHealth.x2", r -> enemyBlockHealth = 3f),
                    new Option("+400%", true, "tri.preset.detail.blockHealth.x3", r -> enemyBlockHealth = 5f,
                            -1, Req.is("difficulty", 2))),
            new Category("turretDamage",
                    new Option("+100%", true, "tri.preset.detail.turret.x1", r -> enemyTurretDamage = 2f),
                    new Option("+200%", true, "tri.preset.detail.turret.x2", r -> enemyTurretDamage = 3f),
                    new Option("+400%", true, "tri.preset.detail.turret.x3", r -> enemyTurretDamage = 5f,
                            -1, Req.is("difficulty", 2))),
            new Category("reinforce",
                    new Option("tri.preset.value.reinforce", false, "tri.preset.detail.reinforce.on",
                            TriPresetMode::applyReinforce)),
            new Category("playerUnitDamage",
                    new Option("-25%", true, "tri.preset.detail.playerUnitDamage.x1", r -> playerUnitDamage = 0.75f),
                    new Option("-50%", true, "tri.preset.detail.playerUnitDamage.x2", r -> playerUnitDamage = 0.5f),
                    new Option("-75%", true, "tri.preset.detail.playerUnitDamage.x3", r -> playerUnitDamage = 0.25f,
                            -1, Req.is("difficulty", 2))),
            new Category("playerTurret",
                    new Option("-25%", true, "tri.preset.detail.playerTurret.x1", r -> playerTurretDamage = 0.75f),
                    new Option("-50%", true, "tri.preset.detail.playerTurret.x2", r -> playerTurretDamage = 0.5f),
                    new Option("-75%", true, "tri.preset.detail.playerTurret.x3", r -> playerTurretDamage = 0.25f,
                            -1, Req.is("difficulty", 2))),
            new Category("playerBlockHealth",
                    new Option("-25%", true, "tri.preset.detail.playerBlockHealth.x1", r -> playerBlockHealth = 0.75f),
                    new Option("-50%", true, "tri.preset.detail.playerBlockHealth.x2", r -> playerBlockHealth = 0.5f),
                    new Option("-75%", true, "tri.preset.detail.playerBlockHealth.x3", r -> playerBlockHealth = 0.25f,
                            -1, Req.is("difficulty", 2))),
            new Category("playerUnitHealth",
                    new Option("-25%", true, "tri.preset.detail.playerUnitHealth.x1", r -> playerUnitHealth = 0.75f),
                    new Option("-50%", true, "tri.preset.detail.playerUnitHealth.x2", r -> playerUnitHealth = 0.5f),
                    new Option("-75%", true, "tri.preset.detail.playerUnitHealth.x3", r -> playerUnitHealth = 0.25f,
                            -1, Req.is("difficulty", 2))),
            // 单选项：禁用任何发射台/接收台。价值固定 3 分；排在单选项网格里（每列 3 个）
            new Category("pads",
                    new Option("tri.preset.value.pads", false, "tri.preset.detail.pads.off",
                            r -> launchPadsDisabled = true, 3))
    );

    private TriPresetMode(){}

    /** 注册敌方/我方数值的事件钩子；由 triangle 主类在启动时调用一次。 */
    public static void init(){
        // 接收台的 accessible() 覆写要等内容加载完才做得成（见 patchLandingPad），开局时再补。
        patchLandingPad();

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
            applySpawnMultiplier(e.rules);
            applyReinforceGroups(e.rules);
            applyLaunchPads(e.rules);
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

    /**
     * 所有接收台（原版 {@code landing-pad} 与模组自带的 {@code SerTriLandingPad}，两者都是
     * {@code LandingPad} 的子类）在非战役局里都“不可用”：原版 {@code LandingPadBuild.accessible()}
     * 只在战役 / 编辑器 / {@code infiniteResources}（沙盒）下返回 true。它一旦为 false，玩家既不能
     * 给接收台配置物品，{@code updateTile} 里“仓空 → 请求落地”那段也就永远走不到。
     *
     * <p>做法是按类型遍历内容，给每个 {@code LandingPad} 方块换一个 Build 实现，
     * 在 {@code presetSession} 期间放开 {@code accessible()}：配置好物品后，非战役下
     * {@code isFake()} 恒为 true → 空仓就 {@code Call.landingPadLanded} 直接补满
     * {@code itemCapacity}，再 dump 给相邻方块，和沙盒是同一条原版代码路径。
     * {@code presetSession} 之外仍交回 super，战役与其它自定义局的行为一律不变。
     *
     * <p>按类型而不是按名字表，是因为模组 JSON 方块的 name 就是文件名（大小写原样保留，
     * 如 {@code SerTriLandingPad}），写死名字既容易笔误也经不起改名。
     *
     * <p>时机：{@code Blocks} 的字段是在 {@code ContentList.load()} 里赋值的，mod 的 init 跑在那之前，
     * 那时内容还是空的，所以在 init 里只做一次尝试（多半是空转），真正生效放在 {@link #start()}。
     */
    private static void patchLandingPad(){
        if(padPatched || Vars.content == null) return;

        int patched = 0;
        for(Block block : Vars.content.blocks()){
            if(!(block instanceof LandingPad)) continue;

            LandingPad pad = (LandingPad)block;
            pad.buildType = () -> pad.new LandingPadBuild(){
                @Override
                public boolean accessible(){
                    return super.accessible() || presetSession;
                }
            };
            patched++;
        }

        // 一个都没找到说明内容还没加载，不置标记，下次进来再试
        if(patched > 0) padPatched = true;
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
        for(int i = 0; i < categories.size; i++){
            Category c = categories.get(i);
            if(c.selected < 0) continue;
            if(optionLocked(c, c.selected)) continue;   // 失去前置的残留选择不计入
            Option o = c.options.get(c.selected);
            int value = o.customValue >= 0 ? o.customValue : (c.selected + 1);
            out.add(new Entry(o.text(), value, o.detail(), i, c.selected));
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
     *
     * <p>前置未满足时直接忽略点击（与界面上的灰化状态一致）。取消前置后会连带清掉依赖它的选择，
     * 见 {@link #pruneLocks()}。
     */
    public static void toggle(int category, int option){
        if(isOptionLocked(category, option)) return;

        Category c = categories.get(category);
        c.selected = c.selected == option ? -1 : option;
        pruneLocks();
    }

    /**
     * 清除“失去前置”的已选词条。
     *
     * <p>取消前置（例如把难度从“毁灭”改回“困难”）时，依赖它的最高档词条必须一起失效，
     * 否则会出现“没选毁灭却留着 +400%”。清除可能级联（A 依赖 B、B 依赖 C），所以循环到稳定。
     */
    private static void pruneLocks(){
        boolean changed = true;
        while(changed){
            changed = false;
            for(Category c : categories){
                if(c.selected < 0) continue;
                if(!c.locked() && !optionLocked(c, c.selected)) continue;
                c.selected = -1;
                changed = true;
            }
        }
    }

    /** 该词条组是否整组被前置锁住。 */
    public static boolean isLocked(int category){
        return categories.get(category).locked();
    }

    /** 该选项当前是否不可选（整组被锁，或自身前置未满足）。 */
    public static boolean isOptionLocked(int category, int option){
        return optionLocked(categories.get(category), option);
    }

    private static boolean optionLocked(Category category, int option){
        if(category.locked()) return true;
        Option o = category.options.get(option);
        return o.req != null && !o.req.satisfied();
    }

    /** 该选项的详情文案（供界面悬停提示使用）。 */
    public static String optionDetail(int category, int option){
        return categories.get(category).options.get(option).detail();
    }

    /**
     * 该选项不可选的原因（已本地化的“需要先选择：X”），可选时返回 null，供界面提示使用。
     */
    public static String lockReason(int category, int option){
        Category c = categories.get(category);
        Option o = c.options.get(option);
        Req req = null;

        for(Req r : c.locks){
            if(!r.satisfied()){
                req = r;
                break;
            }
        }
        if(req == null && o.req != null && !o.req.satisfied()) req = o.req;
        if(req == null) return null;

        Category pre = byKey(req.category);
        if(pre == null) return null;

        String label = req.option >= 0
                ? pre.options.get(req.option).text()
                : Core.bundle.get("tri.preset.lock.anyOption", "该组任意一项");
        return Core.bundle.format("tri.preset.lock.requires", label);
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
        patchLandingPad();

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
        // 难度组一个都没选 → 落在原版“休闲”基线（敌军更弱更少、波次更慢）
        Category diff = byKey("difficulty");
        if(diff != null && diff.selected < 0){
            difficultyUnitHealth = DEFAULT_DIFFICULTY_HEALTH;
            difficultySpawn = DEFAULT_DIFFICULTY_SPAWN;
            if(rules != null) rules.waveSpacing *= DEFAULT_DIFFICULTY_TIME;
        }

        for(Category c : categories){
            if(c.selected < 0 || c.locked()) continue;
            Option o = c.options.get(c.selected);
            if(o.req != null && !o.req.satisfied()) continue;
            if(o.apply != null) o.apply.get(rules);
        }
    }

    /**
     * “游戏难度”词条的落地：三个乘子写进静态配置，波次间隔直接乘到 {@code waveSpacing}。
     *
     * <p>写成“乘”而不是“赋值”是刻意的：难度与其它词条是叠加关系——例如“毁灭”的单位生命 ×1.5
     * 与敌方单位生命 +400%（×5）叠加后是 ×7.5，而不是谁覆盖谁；波次间隔同理
     * （难度 ×0.6 与出怪间隔 ×0.5 叠加 = ×0.3）。
     */
    private static void applyDifficulty(Rules rules, float unitHealth, float spawn, float waveTime){
        difficultyUnitHealth = unitHealth;
        difficultySpawn = spawn;
        if(rules != null && waveTime != 1f) rules.waveSpacing *= waveTime;
    }

    /**
     * 把“每波敌军数量”倍率乘到本局最终的生成组上。
     *
     * <p>落点必须是 {@link RulesLoadEvent}：地图载入时 {@code retainContentFields} 会把
     * {@code rules.spawns} 整份换成地图自带那份，之前改的生成组会全部丢失（见
     * {@link #applyReinforce(Rules)} 的说明）。
     *
     * <p>缩放口径对齐 {@code SpawnGroup.getSpawned()}：
     * {@code spawned = min(unitAmount + (int)(((wave-begin)/spacing) / unitScaling), max)}。
     * 所以放大 {@code unitAmount}、并把<b>除数</b> {@code unitScaling} 缩小同样的倍数，
     * 才能让每一波的数量都按倍率放大；{@code max} 表示“同时在场上限”，默认 -1（不限），只对正数上限放大。
     */
    private static void applySpawnMultiplier(Rules rules){
        if(spawnScaled || rules == null || rules.spawns == null) return;
        if(difficultySpawn == 1f) return;

        Seq<SpawnGroup> spawns = new Seq<>(rules.spawns);
        for(SpawnGroup group : spawns){
            if(group == null) continue;
            group.unitAmount = Math.max(1, Mathf.round(group.unitAmount * difficultySpawn));
            if(group.unitScaling > 0f) group.unitScaling = group.unitScaling / difficultySpawn;
            if(group.max > 0) group.max = Math.max(1, Mathf.round(group.max * difficultySpawn));
        }
        rules.spawns = spawns;
        spawnScaled = true;
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
     * 发射台/接收台的基础设置：默认放开，选中“禁用”词条时整组拉黑。
     *
     * <p>原版把这两类方块的可见性绑在战役上（{@code BuildVisibility.notLegacyLaunchPadOnly}：
     * 只有“不在对局中 / 沙盒无限资源 / 战役且未启用旧版发射台机制”才可见），
     * 所以在自定义生存里 {@code Block.isHidden()} 会返回 true，建造菜单里根本选不到。
     * {@code Block.isHidden()} 的第二个判据是 {@code state.rules.revealedBlocks.contains(this)}，
     * 因此把它们塞进 {@code revealedBlocks} 就等于“已揭示”，无需改原版类。
     *
     * <p>禁用走 {@code bannedBlocks} + {@code hideBannedBlocks}：前者让 {@code Block.isBanned()} 为真
     * （放不下去），后者让 {@code Block.isVisible()} 为假（菜单里彻底不显示）。
     */
    private static void applyLaunchPads(Rules rules){
        if(rules == null || Vars.content == null) return;

        for(String name : PAD_NAMES){
            Block pad = Vars.content.block(name);
            if(pad != null && rules.revealedBlocks != null) rules.revealedBlocks.add(pad);
        }

        // 接收台按类型兜底揭示：模组自带的 SerTriLandingPad 名字保留大小写，不适合走名字表，
        // 按 instanceof 处理既不依赖命名，也顺带覆盖以后再加的同类方块。
        if(rules.revealedBlocks != null){
            for(Block b : Vars.content.blocks()){
                if(b instanceof LandingPad) rules.revealedBlocks.add(b);
            }
        }

        if(!launchPadsDisabled) return;
        for(String name : ALL_PAD_NAMES){
            Block pad = Vars.content.block(name);
            if(pad != null && rules.bannedBlocks != null) rules.bannedBlocks.add(pad);
        }
        // 同上：任何发射台/接收台（含模组那对发射台、接收台）都按类型拉黑，不依赖方块名字。
        if(rules.bannedBlocks != null){
            for(Block b : Vars.content.blocks()){
                if(b instanceof LandingPad || b instanceof LaunchPad) rules.bannedBlocks.add(b);
            }
        }
        rules.hideBannedBlocks = true;
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
            // 难度词条的单位生命倍率与敌方单位生命词条相乘叠加（不是覆盖）
            health = enemyUnitHealth * difficultyUnitHealth; damage = enemyUnitDamage; armor = enemyUnitArmor;
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
                || difficultyUnitHealth != 1f
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
        difficultyUnitHealth = DEFAULT_DIFFICULTY_HEALTH;
        difficultySpawn = DEFAULT_DIFFICULTY_SPAWN;
        spawnScaled = false;
        reinforceEnabled = false;
        launchPadsDisabled = false;
        buffedUnits.clear();
        scaledBullets.clear();
        prevBuildingHealth.clear();
        prevUnitHealth.clear();
        instantBeams.clear();
    }
}
