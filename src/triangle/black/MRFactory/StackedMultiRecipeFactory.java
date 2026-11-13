package triangle.black.MRFactory;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.logic.LAccess;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.world.Build;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.draw.DrawBlock;
import mindustry.world.draw.DrawDefault;
import triangle.black.TriSetting;

/**
 * 固定层数的多配方工厂方块。
 *
 * <p>基础 {@link MultiRecipeFactory} 只保留一层；当玩家把同实例工厂覆盖到已有工厂上时，
 * 放置计划会被替换成这里的 2 层 / 3 层 / ... 变体。每个变体是一个独立 Block，
 * 容量、生产线程数和配置面板数在方块属性中静态确定。
 */
public class StackedMultiRecipeFactory extends MultiRecipeFactory{

    /** 基础工厂 -> 全部堆叠变体。 */
    private static final ObjectMap<MultiRecipeFactory, Seq<StackedMultiRecipeFactory>> variants = new ObjectMap<>();
    /**
     * 待迁移的旧建筑状态（纯数据快照）。
     * 这里刻意不持有 Building 实例：静态表一旦残留，就会让整个建筑、物品模块和电网节点都无法回收。
     */
    private static final IntMap<Snapshot> pending = new IntMap<>();
    /**
     * 正在进行“降层拆除”的格子：格子号 -> 期望被拆掉的方块。
     * 只对这些格子的拆除结果做换层；玩家自己拆同格时方块对不上，不会被误判。
     */
    private static final IntMap<Block> downPending = new IntMap<>();
    /** 降层标记的容量上限；超过说明有标记再也不会被消费，直接整表清空。 */
    private static final int downPendingLimit = 256;

    /** 旧建筑的状态快照：库存 + 各层配方状态，全部是纯数据，不含任何实体引用。 */
    public static class Snapshot{
        public final Seq<ItemStack> items = new Seq<>();
        public final Seq<LiquidStack> liquids = new Seq<>();
        public final Seq<LayerState> layers = new Seq<>();
    }

    static{
        // 降层：原版拆除完成（deconstructFinish 触发本事件，随后才 tile.remove()）后，
        // 就地换成少一层的变体，并迁移原建筑的库存与各层配方状态。
        Events.on(BlockBuildEndEvent.class, event -> {
            if(!event.breaking || event.tile == null) return;
            int pos = event.tile.pos();
            Block expected = downPending.remove(pos);
            if(expected == null) return;

            Tile tile = event.tile;
            Building current = tile.build;
            if(!(current instanceof ConstructBlock.ConstructBuild construct)) return;
            Block previous = construct.previous;
            if(previous == null || previous != expected) return;
            MultiRecipeFactory base = baseOf(previous);
            if(base == null) return;
            int targetLayers = layersOf(previous) - 1;
            if(targetLayers < 1) return;
            Block target = targetLayers <= 1 ? base : variantOf(base, targetLayers);
            if(target == null) return;

            // 原建筑：本次会话内仍可拿到（读档后 prevBuild 不持久化，此时只保留层数不迁移库存）。
            Building old = construct.prevBuild != null && construct.prevBuild.size > 0
                ? construct.prevBuild.first() : null;
            Team team = event.team;
            int rot = construct.rotation;
            int x = tile.x, y = tile.y;

            Core.app.post(() -> {
                Tile at = Vars.world.tile(x, y);
                if(at == null || at.build != null) return;
                Snapshot snap = snapshotOf(old);
                if(snap != null) pending.put(at.pos(), snap);
                at.setBlock(target, team, rot);
            });
        });

        // 换地图/读档时清掉未完成的降层标记，避免残留下来的格子号误判后续拆除。
        Events.on(WorldLoadEvent.class, event -> {
            downPending.clear();
            pending.clear();
        });
    }

    public final MultiRecipeFactory baseFactory;
    public final int layerCount;

    @SuppressWarnings("this-escape")
    public StackedMultiRecipeFactory(MultiRecipeFactory base, int layerCount){
        super(base.plainName() + "-stack" + layerCount);
        this.baseFactory = base;
        this.layerCount = layerCount;
        copyBase(base);
        // 基础工厂的 ConsumePowerRecipe 只统计“当前配方”的耗电；
        // 堆叠体需要把所有层的耗电相加后交给电网。
        powerConsumer = new StackedPowerConsume();
        consume(powerConsumer);
    }

    /** 堆叠工厂的电力需求 = 所有层当前配方的 powerUse 之和。 */
    public static class StackedPowerConsume extends ConsumePowerRecipe{
        public StackedPowerConsume(){
            super(build -> null);
        }

        @Override
        public float requestedPower(Building build){
            return build instanceof StackedMultiRecipeFactory.StackedBuild stacked
                ? stacked.totalPowerUse()
                : super.requestedPower(build);
        }

        @Override
        public boolean isUndervoltage(Building build){
            if(build instanceof StackedMultiRecipeFactory.StackedBuild stacked){
                return stacked.hasUndervoltageLayer();
            }
            return super.isUndervoltage(build);
        }
    }

    /** 为基础工厂预生成 2..stackLimit 层变体；可重复调用以补齐后增加的上限。 */
    public static void registerVariants(MultiRecipeFactory base){
        if(base == null || !base.stackable) return;
        Seq<StackedMultiRecipeFactory> list = variants.get(base);
        if(list == null){
            list = new Seq<>();
            variants.put(base, list);
        }
        int max = Math.max(2, base.stackLimit);
        for(int i = 2; i <= max; i++){
            int layer = i;
            if(!list.contains(v -> v.layerCount == layer)){
                list.add(new StackedMultiRecipeFactory(base, layer));
            }
        }
        for(StackedMultiRecipeFactory variant : list){
            variant.stackable = variant.layerCount < max;
            variant.replaceable = variant.stackable && base.replaceable;
        }
    }

    /** 根据放置目标选择下一层变体；只接受链上的同实例工厂。 */
    public static Block nextVariant(MultiRecipeFactory current, BuildPlan req){
        if(current == null || req == null || Vars.world == null) return null;
        Tile tile = Vars.world.tile(req.x, req.y);
        if(tile == null || tile.build == null) return null;

        MultiRecipeFactory currentBase = baseOf(current);
        MultiRecipeFactory existingBase = baseOf(tile.build.block);
        if(currentBase == null || currentBase != existingBase) return null;

        int next = layersOf(tile.build.block) + 1;
        if(next > currentBase.stackLimitNow()) return null;
        Seq<StackedMultiRecipeFactory> list = variants.get(currentBase);
        if(list == null) return null;
        for(StackedMultiRecipeFactory variant : list){
            if(variant.layerCount == next) return variant;
        }
        return null;
    }

    /** 达到上限时返回当前顶层方块，防止放置计划落回基础方块。 */
    public static Block topVariant(MultiRecipeFactory current, BuildPlan req){
        if(current == null || req == null || Vars.world == null) return null;
        Tile tile = Vars.world.tile(req.x, req.y);
        if(tile == null || tile.build == null) return null;
        MultiRecipeFactory currentBase = baseOf(current);
        MultiRecipeFactory existingBase = baseOf(tile.build.block);
        if(currentBase == null || currentBase != existingBase) return null;
        return tile.build.block;
    }

    /** 当前格子的工厂链是否已达到上限。 */
    public static boolean atStackLimit(MultiRecipeFactory current, int x, int y){
        if(current == null || Vars.world == null) return false;
        Tile tile = Vars.world.tile(x, y);
        if(tile == null || tile.build == null) return false;
        MultiRecipeFactory base = baseOf(current);
        if(base == null || baseOf(tile.build.block) != base) return false;
        return layersOf(tile.build.block) >= Math.max(1, base.stackLimitNow());
    }

    /** 取基础工厂的某一层变体；不存在时返回 null。 */
    public static StackedMultiRecipeFactory variantOf(MultiRecipeFactory base, int layerCount){
        if(base == null) return null;
        Seq<StackedMultiRecipeFactory> list = variants.get(base);
        if(list == null) return null;
        for(StackedMultiRecipeFactory variant : list){
            if(variant.layerCount == layerCount) return variant;
        }
        return null;
    }

    /** 该方块所属链当前允许的叠层上限；不可堆叠时返回 0。 */
    public static int stackLimitFor(Block block){
        MultiRecipeFactory base = baseOf(block);
        if(base == null || !base.stackable) return 0;
        return base.stackLimitNow();
    }

    /** 该方块是否还能再叠一层（配置面板按钮用）。 */
    public static boolean canStackUp(Block block){
        MultiRecipeFactory base = baseOf(block);
        if(base == null || !base.stackable) return false;
        return layersOf(block) + 1 <= base.stackLimitNow();
    }

    /** 该方块是否能减一层（最低保持 1 层基础工厂）。 */
    public static boolean canStackDown(Block block){
        MultiRecipeFactory base = baseOf(block);
        if(base == null) return false;
        return layersOf(block) > 1;
    }

    /**
     * 让一个已建成的工厂向上叠一层（配置面板“堆叠”按钮）。
     * 走原版建造队列：把“上一层变体”作为普通放置计划压进玩家单位的计划队列，
     * 由原版 {@code Unit.updateBuildLogic()} 依次完成 beginPlace → 逐帧 construct → 完工，
     * 于是自动获得原版的扣料、建造时间和建造进度条。
     *
     * <p>注意：直接调 {@link Build#beginPlace} 只会生成一个 progress 恒为 0 的
     * ConstructBuild（进度推进完全由单位的建造计划驱动，见 v150 的 Unit.updateBuildLogic），
     * 所以这里必须入队 BuildPlan 而不是直接放置。
     */
    public static boolean stackUp(Building build){
        if(build == null || build.tile == null || Vars.player == null || Vars.player.unit() == null) return false;
        if(!Vars.player.isBuilder()) return false;
        MultiRecipeFactory base = baseOf(build.block);
        if(base == null || !base.stackable) return false;
        int next = layersOf(build.block) + 1;
        if(next > base.stackLimitNow()) return false;
        StackedMultiRecipeFactory target = variantOf(base, next);
        if(target == null) return false;

        Tile tile = build.tile;
        Vars.player.unit().addBuild(new BuildPlan(tile.x, tile.y, build.rotation, target, build.config()));
        return true;
    }

    /** 供基础工厂（1 层）在 created() 里取回被替换的旧建筑状态（只读，不移除）。 */
    static Snapshot pendingSnapshot(int pos){
        return pending.get(pos);
    }

    /** 堆叠体在 created() 里取走并清掉该格的旧建筑状态。 */
    static Snapshot takeSnapshot(int pos){
        return pending.remove(pos);
    }

    /** 迁移完成后清掉该格的旧建筑状态。 */
    static void clearPending(int pos){
        pending.remove(pos);
    }

    /** 采集旧建筑的库存与各层配方状态；旧建筑为 null 时返回 null。 */
    static Snapshot snapshotOf(Building old){
        if(old == null) return null;
        Snapshot snap = new Snapshot();
        if(old.items != null){
            old.items.each((item, amount) -> {
                if(amount > 0) snap.items.add(new ItemStack(item, amount));
            });
        }
        if(old.liquids != null){
            old.liquids.each((liquid, amount) -> {
                if(amount > 0) snap.liquids.add(new LiquidStack(liquid, amount));
            });
        }
        if(old instanceof StackedBuild stacked){
            for(LayerState src : stacked.layers) snap.layers.add(copyLayer(src));
        }else if(old instanceof MultiRecipeFactoryBuild base){
            LayerState copy = new LayerState();
            copy.recipeIndex = base.recipeIndex;
            copy.selectedIndex = base.selectedIndex;
            copy.auto = base.auto;
            copy.progress = base.progress;
            copy.logicOverride = base.logicOverride;
            snap.layers.add(copy);
        }
        return snap;
    }

    static LayerState copyLayer(LayerState src){
        LayerState copy = new LayerState();
        copy.recipeIndex = src.recipeIndex;
        copy.selectedIndex = src.selectedIndex;
        copy.auto = src.auto;
        copy.progress = src.progress;
        copy.logicOverride = src.logicOverride;
        return copy;
    }

    /** 取旧建筑的第一层配方状态（旧建筑为堆叠体时才有）。 */
    static LayerState stateOf(Building old){
        if(old instanceof StackedBuild stacked && stacked.layers.size > 0) return stacked.layers.first();
        return null;
    }

    /**
     * 清掉再也不会被消费的降层标记：建造计划被取消、格子已经换人等情况下，
     * 标记留在表里就会永久滞留，这里按“计划队列里是否还有对应拆除计划”回收。
     */
    static void pruneDownPending(){
        if(downPending.size == 0) return;
        if(Vars.world == null || Vars.player == null || Vars.player.unit() == null || downPending.size > downPendingLimit){
            downPending.clear();
            return;
        }
        var plans = Vars.player.unit().plans();
        if(plans == null || plans.isEmpty()){
            downPending.clear();
            return;
        }
        IntSet alive = new IntSet();
        for(BuildPlan plan : plans){
            if(!plan.breaking) continue;
            Tile tile = Vars.world.tile(plan.x, plan.y);
            if(tile != null) alive.add(tile.pos());
        }
        IntSeq stale = new IntSeq();
        for(IntMap.Entry<Block> entry : downPending){
            if(!alive.contains(entry.key)) stale.add(entry.key);
        }
        for(int i = 0; i < stale.size; i++) downPending.remove(stale.get(i));
    }

    /**
     * 让一个已建成的工厂降一层（配置面板“降层”按钮）。
     * 复用原版拆除流程：与 {@code InputHandler.breakBlock} 完全一致——只往玩家单位的
     * 计划队列里压一个 breaking 的 BuildPlan，由原版 {@code Unit.updateBuildLogic()}
     * 调 beginBreak 生成 ConstructBuild、逐帧 deconstruct(...) 推进进度，并按
     * {@code rules.deconstructRefundMultiplier} 把那一层的材料逐步退回核心。
     * 拆除完成的 BlockBuildEndEvent 里再把该格换成少一层的变体（见静态监听器）。
     */
    public static boolean stackDown(Building build){
        if(build == null || build.tile == null || Vars.player == null || Vars.player.unit() == null) return false;
        if(!Vars.player.isBuilder()) return false;
        MultiRecipeFactory base = baseOf(build.block);
        if(base == null) return false;
        if(layersOf(build.block) - 1 < 1) return false;

        Tile tile = build.tile;
        if(!Build.validBreak(build.team, tile.x, tile.y)) return false;

        // 顺手回收已经不可能被消费的降层标记，避免静态表无限增长。
        pruneDownPending();

        // 记录“本格这次拆除要换成少一层”，方块对不上就说明是别的原因导致的拆除。
        downPending.put(tile.pos(), build.block);
        Vars.player.unit().addBuild(new BuildPlan(tile.x, tile.y));
        return true;
    }

    private static MultiRecipeFactory baseOf(Block block){
        if(block instanceof StackedMultiRecipeFactory stacked) return stacked.baseFactory;
        if(block instanceof MultiRecipeFactory factory) return factory;
        return null;
    }

    public static int layersOf(Block block){
        if(block instanceof StackedMultiRecipeFactory stacked) return stacked.layerCount;
        return block instanceof MultiRecipeFactory ? 1 : 0;
    }

    private void copyBase(MultiRecipeFactory base){
        size = base.size;
        health = base.health;
        armor = base.armor;
        // 未初始化时基础工厂可能仍是 -1，init() 后会再次按层数修正。
        buildTime = base.buildTime >= 0f ? base.buildTime * layerCount : base.buildTime;
        itemCapacity = base.itemCapacity * layerCount;
        liquidCapacity = base.liquidCapacity * layerCount;
        requirements = base.requirements;
        category = base.category;
        // 不能设为 hidden：原版 validPlace 会拒绝不可放置方块。
        // 用 placeablePlayer=false 从建造菜单隐藏，同时保留替换放置能力。
        buildVisibility = base.buildVisibility;
        placeablePlayer = false;
        rotate = base.rotate;
        rotateDraw = base.rotateDraw;
        drawer = base.drawer;
        update = base.update;
        hasItems = base.hasItems;
        hasLiquids = base.hasLiquids;
        hasPower = base.hasPower;
        consumesPower = base.consumesPower;
        configurable = true;
        checkItemOutputFullness = base.checkItemOutputFullness;
        checkLiquidOutputFullness = base.checkLiquidOutputFullness;
        verticalStackPanels = base.verticalStackPanels;
        stackable = layerCount < Math.max(2, base.stackLimit);
        // 顶层变体不能再被覆盖替换，否则会绕过堆叠上限。
        replaceable = stackable && base.replaceable;
        registerResearch = false;
        hideDatabase = true;
        databaseTabs.clear();
        recipes.addAll(base.recipes);

        String baseName = base.localizedName == null ? base.name : base.localizedName;
        localizedName = baseName + " ×" + layerCount;
        description = base.description;
        uiIcon = base.uiIcon;
    }

    @Override
    public void init(){
        super.init();
        if(baseFactory != null){
            // 单层建造时间 × 层数；基础工厂尚未初始化时用变体自己算出的单层时间。
            float single = baseFactory.buildTime >= 0f ? baseFactory.buildTime : buildTime;
            buildTime = single * layerCount;
        }
    }

    @Override
    public void load(){
        // 不要用变体名重新加载基础工厂的 drawer：
        // DrawFlame 等会寻找 “变体名-flame” 贴图，找不到就回退成 ohno。
        DrawBlock baseDrawer = baseFactory == null ? null : baseFactory.drawer;
        drawer = new DrawDefault();
        super.load();
        drawer = baseDrawer;
        if(baseFactory != null){
            region = baseFactory.region;
            fullIcon = baseFactory.fullIcon;
            uiIcon = baseFactory.uiIcon;
        }
    }

    /**
     * 不再在 placeBegan 里捕获“旧建筑”：此时 tile.build 已经是被放上来的 ConstructBuild，
     * 捕获它只会把空库存当成旧建筑来迁移。旧建筑由原版 constructFinish 通过
     * {@code overwrote(prevBuild)} 交回来（beginPlace / beginBreak 都会填充 prevBuild）。
     */

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        if(tile != null && tile.build != null && sameChain(tile.build.block)
            && layersOf(tile.build.block) + 1 == layerCount
            && layerCount <= baseFactory.stackLimitNow()){
            return true;
        }
        return super.canPlaceOn(tile, team, rotation);
    }

    @Override
    public boolean canReplace(Block other){
        if(other != null && sameChain(other)){
            // 只允许相邻层互相覆盖：上一层覆盖下一层（向上堆叠），
            // 下一层覆盖上一层（配置面板的降层按钮）。
            // 顶层变体仍可作为新方块覆盖 7 层，但它自己不能再被更高层覆盖。
            int otherLayers = layersOf(other);
            return layerCount == otherLayers + 1 || layerCount == otherLayers - 1;
        }
        return super.canReplace(other);
    }

    private boolean sameChain(Block block){
        if(block == null) return false;
        if(block == baseFactory) return true;
        return block instanceof StackedMultiRecipeFactory stacked && stacked.baseFactory == baseFactory;
    }

    public static class LayerState{
        public int recipeIndex = -1;
        public int selectedIndex = -1;
        public boolean auto = true;
        public float progress;
        /** 该层最近一次被逻辑 {@code control config} 驱动的时刻；< 0 表示从未被逻辑控制。 */
        public float logicControlTime = -1f;
        /** true = 该层处于超控：忽略逻辑对该层的控制（随配置/蓝图/存档保存）。 */
        public boolean logicOverride = false;

        /** 该层当前是否处于逻辑控制下（时间窗内收到过控制，且该层未超控）。 */
        public boolean logicControlled(){
            return !logicOverride && logicControlTime >= 0f
                && Time.time - logicControlTime <= MultiRecipeFactory.MultiRecipeFactoryBuild.logicControlWindow;
        }
    }

    /**
     * 单层提示按钮：显示“第 N 层：被逻辑处理器控制 / 处于超控状态”，点击切换该层超控。
     * 外观与基础工厂一致：样式固定、悬停描述浮于面板之上、宽度按实测文字适配。
     */
    public static class LayerHint{
        public final Table box = new Table();
        public final TextButton button;
        private final Table panel;
        private final LayerState layer;
        private final int layerNo;
        private final Runnable onToggle;
        private final TextButton.TextButtonStyle styleNormal;
        private final TextButton.TextButtonStyle styleOverride;
        private final Tooltip tip;
        private final boolean[] hover = {false};
        private final Runnable[] sync = {null};
        private Cell<?> cell;
        private boolean shown;
        private boolean sizeDirty = true;
        private String lastText;

        public LayerHint(Table panel, LayerState layer, int layerNo, Runnable onToggle){
            this.panel = panel;
            this.layer = layer;
            this.layerNo = layerNo;
            this.onToggle = onToggle;

            TextButton.TextButtonStyle normal = new TextButton.TextButtonStyle(Styles.defaultt);
            normal.up = Tex.buttonDisabled;
            normal.over = Tex.buttonOver;      // 悬停暂时高亮
            normal.down = Tex.buttonDisabled;
            normal.disabled = Tex.buttonDisabled;
            normal.fontColor = Color.valueOf("a020f0");
            normal.disabledFontColor = Color.valueOf("a020f0");
            TextButton.TextButtonStyle over = new TextButton.TextButtonStyle(normal);
            over.fontColor = Color.valueOf("ffa500");
            over.disabledFontColor = Color.valueOf("ffa500");
            styleNormal = normal;
            styleOverride = over;

            button = new TextButton("", normal);
            button.getLabel().setWrap(false);

            tip = new Tooltip(t -> {
                t.background(Styles.black8);
                t.margin(4f);
                t.touchable = Touchable.disabled;
            }){
                {
                    // 已打开时只同步文案与位置，绝不再次 show
                    sync[0] = () -> {
                        refill();
                        setContainerPosition(button, 0f, 0f);
                    };
                    // 该层提示按钮离开场景（面板关闭）时自行收起浮框
                    container.update(() -> {
                        if(!button.hasParent() && container.hasParent()) hide();
                    });
                }

                @Override
                public void show(Element target, float x, float y){
                    refill();
                    super.show(target, x, y);
                }

                @Override
                protected void setContainerPosition(Element target, float x, float y){
                    targetActor = target;
                    // 垂直贴住按钮下边缘；水平以本层面板的中点为锚
                    float yPos = target.localToStageCoordinates(Tmp.v1.set(0f, 0f)).y;
                    container.pack();
                    Tmp.v1.set(panel.getWidth() / 2f, 0f);
                    float centerX = panel.localToStageCoordinates(Tmp.v1).x;
                    container.setPosition(centerX - container.getWidth() / 2f, yPos, Align.left | Align.top);
                    container.setOrigin(0f, target.getHeight());
                }
            };
            tip.allowMobile = true;

            button.addListener(new InputListener(){
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                    if(pointer == -1) hover[0] = true;
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                    if(pointer == -1) hover[0] = false;
                }
            });
            button.update(() -> {
                if(!button.hasParent()){
                    if(tip.container.hasParent()) tip.hide();
                    return;
                }
                if(hover[0]){
                    if(!tip.container.hasParent()) tip.show(button, 0f, 0f);
                    else if(sync[0] != null) sync[0].run();
                }else if(tip.container.hasParent()){
                    tip.hide();
                }
            });
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    // 显式限定：ClickListener 自带 int 字段 button，会遮蔽外部同名字段
                    if(!LayerHint.this.button.hasParent()) return;
                    layer.logicOverride = !layer.logicOverride;
                    onToggle.run();
                }
            });
        }

        private String text(){
            String state = layer.logicOverride ? Core.bundle.get("tri.stack.logicOverrideOn") : Core.bundle.get("tri.stack.logicControlled");
            return Core.bundle.format("tri.stack.hintPrefix", Core.bundle.format("tri.stack.layer", layerNo)) + state;
        }

        private String desc(){
            return layer.logicOverride ? Core.bundle.get("tri.stack.logicOverrideDesc") : Core.bundle.get("tri.stack.logicControlledDesc");
        }

        private void refill(){
            Table c = tip.container;
            String d = desc();
            if(c.getChildren().size == 1 && c.getChildren().first() instanceof Label
                && d.contentEquals(((Label)c.getChildren().first()).getText())) return;
            c.clearChildren();
            c.add(d);
            if(c.hasParent()) c.pack();
        }

        /** 同步显隐、文字、样式与按钮宽度；由该层面板的 refresh 调用。 */
        public void refresh(){
            boolean show = layer.logicControlled() || layer.logicOverride;
            if(show != shown){
                shown = show;
                if(!show) hover[0] = false;
                box.clearChildren();
                cell = null;
                if(show){
                    cell = box.add(button).center().padBottom(6f);
                    sizeDirty = true;
                }else if(tip.container.hasParent()){
                    tip.hide();
                }
            }
            String t = text();
            if(!t.equals(lastText) || sizeDirty){
                lastText = t;
                button.setStyle(layer.logicOverride ? styleOverride : styleNormal);
                button.setText(t);
                if(cell != null) cell.width(button.getPrefWidth());
                sizeDirty = false;
            }
        }
    }

    public class StackedBuild extends MultiRecipeFactoryBuild{
        public Seq<LayerState> layers = new Seq<>();

        public StackedBuild(){
            super();
            for(int i = 0; i < layerCount; i++) layers.add(new LayerState());
        }

        private void ensureLayers(){
            while(layers.size < layerCount) layers.add(new LayerState());
            while(layers.size > layerCount) layers.remove(layers.size - 1);
        }

        @Override
        public void created(){
            super.created();
            ensureLayers();

            Snapshot snap = takeSnapshot(tile.pos());
            if(snap != null){
                migrateFrom(snap);
                for(int i = 0; i < Math.min(layers.size, snap.layers.size); i++){
                    LayerState src = snap.layers.get(i);
                    LayerState dst = layers.get(i);
                    dst.recipeIndex = src.recipeIndex;
                    dst.selectedIndex = src.selectedIndex;
                    dst.auto = src.auto;
                    dst.progress = src.progress;
                    dst.logicOverride = src.logicOverride;
                }
            }
            clampStorage();
            // 只有“换块”（带着旧建筑状态）才需要重新登记网络；
            // 读档时每个建筑都会跑 created()，那里由原版自行建图，不做多余的重建。
            if(snap != null) Core.app.post(this::refreshNetworks);
        }

        /**
         * 原版 constructFinish 会带着 ConstructBlock.prevBuild 调用本方法，
         * 这是“放置覆盖 / 配置面板堆叠”时拿到真正旧建筑的地方。
         * 库存在 super 里迁移，这里补齐每一层的配方状态。
         */
        @Override
        public void overwrote(Seq<Building> previous){
            Building old = previousFactory(previous);
            super.overwrote(previous);
            ensureLayers();
            if(old instanceof StackedBuild stacked){
                for(int i = 0; i < Math.min(layers.size, stacked.layers.size); i++){
                    LayerState src = stacked.layers.get(i);
                    LayerState dst = layers.get(i);
                    dst.recipeIndex = src.recipeIndex;
                    dst.selectedIndex = src.selectedIndex;
                    dst.auto = src.auto;
                    dst.progress = src.progress;
                    dst.logicOverride = src.logicOverride;
                }
            }else if(old instanceof MultiRecipeFactoryBuild base){
                LayerState dst = layers.first();
                dst.recipeIndex = base.recipeIndex;
                dst.selectedIndex = base.selectedIndex;
                dst.auto = base.auto;
                dst.progress = base.progress;
                dst.logicOverride = base.logicOverride;
            }
            clampStorage();
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();
            refreshPowerGraph();
        }

        private void refreshPowerGraph(){
            if(power == null || power.graph == null) return;
            if(!power.graph.all.contains(this)){
                power.graph.add(this);
            }
            power.graph.update();
        }

        /** 所有层当前实际运行配方的电力需求总和。 */
        public float totalPowerUse(){            float total = 0f;
            for(LayerState layer : layers){
                // 自动模式只统计已实际选中的配方；手动模式的目标配方本身就是
                // 即将执行的配方，可在首次 updateTile 前提供正确需求。
                int index = layer.auto ? layer.recipeIndex : layer.selectedIndex;
                if(index >= 0 && index < recipes.size){
                    total += recipes.get(index).powerUse;
                }
            }
            return total;
        }

        /** 任意一层处于欠压即停状态。 */
        public boolean hasUndervoltageLayer(){
            if(power == null || power.status >= 1f) return false;
            for(LayerState layer : layers){
                int index = layer.auto ? layer.recipeIndex : layer.selectedIndex;
                if(index >= 0 && index < recipes.size){
                    Recipe recipe = recipes.get(index);
                    if(recipe.powerUse > 0 && recipe.stopUndervoltage) return true;
                }
            }
            return false;
        }

        @Override
        protected void updateUndervoltageLock(){
            if(hasUndervoltageLayer()){
                undervoltageLock = UNDERVOLTAGE_LOCK_TIME;
            }else if(undervoltageLock > 0f){
                undervoltageLock = Math.max(0f, undervoltageLock - Time.delta);
            }
        }

        @Override
        public void updateTile(){
            ensureLayers();
            for(LayerState layer : layers){
                recipeIndex = layer.recipeIndex;
                selectedIndex = layer.selectedIndex;
                auto = layer.auto;
                progress = layer.progress;

                super.updateTile();

                layer.recipeIndex = recipeIndex;
                layer.selectedIndex = selectedIndex;
                layer.auto = auto;
                layer.progress = progress;
            }
        }

        @Override
        public int getMaximumAccepted(Item item){
            return Math.max(0, itemCapacity - (items == null ? 0 : items.get(item)));
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return items != null && items.get(item) < itemCapacity && super.acceptItem(source, item);
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            // 多液体输入时按目标液体自身容量判断；原版 LiquidModule 的
            // liquidCapacity 也是“每种液体”的上限。若这里用 liquidTotal()
            // 先卡总容量，一种液体占满后另一种即使自身为空也无法输入。
            return liquids != null && super.acceptLiquid(source, liquid);
        }

        private float liquidTotal(){
            if(liquids == null) return 0f;
            float total = 0f;
            for(Liquid liquid : Vars.content.liquids()){
                total += liquids.get(liquid);
            }
            return total;
        }

        @Override
        public boolean canOutputForRecipe(Recipe recipe){
            // 与原版及 MultiRecipeFactory 一致：所有同类输出都满才停止。
            if(checkItemOutputFullness && !recipe.outputItem.isEmpty()){
                boolean allFull = true;
                for(ItemStack output : recipe.outputItem){
                    if(items.get(output.item) < itemCapacity){
                        allFull = false;
                        break;
                    }
                }
                if(allFull) return false;
            }
            if(checkLiquidOutputFullness && !recipe.outputLiquid.isEmpty()){
                boolean allFull = true;
                for(LiquidStack output : recipe.outputLiquid){
                    if(liquids.get(output.liquid) < liquidCapacity - 0.001f){
                        allFull = false;
                        break;
                    }
                }
                if(allFull) return false;
            }
            return true;
        }

        @Override
        public boolean shouldConsume(){
            if(getRecipe() == null) return false;
            if(undervoltageLock > 0f) return false;
            Recipe currentRecipe = getRecipe();
            if(power.status <= 0 || powerConsumer.isUndervoltage(this)) return false;
            if(currentRecipe.heatEnabled && currentRecipe.heatRequirement > 0
                && heat < currentRecipe.heatRequirement) return false;

            if(!ignoreLiquidFullness){
                if(currentRecipe.outputLiquid.isEmpty()) return true;
                boolean allFull = true;
                for(LiquidStack output : currentRecipe.outputLiquid){
                    if(liquids.get(output.liquid) < liquidCapacity - 0.001f){
                        allFull = false;
                        break;
                    }
                }
                if(allFull) return false;
            }
            return enabled;
        }

        /** 缩容/替换时销毁超出容量的部分；物品按每种独立计算。 */
        public void clampStorage(){
            // 快速路径：没有超容量时不做任何集合分配。
            if(items != null && items.total() > itemCapacity){
                for(Item item : Vars.content.items()){
                    int amount = items.get(item);
                    if(amount > itemCapacity){
                        items.remove(item, amount - itemCapacity);
                    }
                }
            }

            if(liquids == null) return;
            float total = liquidTotal();
            if(total <= liquidCapacity + 0.0001f) return;

            float excess = total - liquidCapacity;
            for(Liquid liquid : Vars.content.liquids()){
                if(excess <= 0.0001f) break;
                float amount = liquids.get(liquid);
                if(amount <= 0f) continue;

                float remove = Math.min(amount, excess);
                liquids.remove(liquid, remove);
                excess -= remove;
            }
        }

        @Override
        public Object config(){
            StringBuilder out = new StringBuilder();
            for(int i = 0; i < layers.size; i++){
                if(i > 0) out.append(',');
                out.append(encodeLayer(layers.get(i)));
            }
            // 全局（全部层）超控状态尾随在配置里（"各层选择#1"），使蓝图复制/粘贴也能带上
            if(logicOverride) out.append("#1");
            return out.toString();
        }

        /** 单层选择 + 该层超控位的编码：普通态沿用旧值（-1 或索引），超控态加 logicOverrideBase 偏移。 */
        private int encodeLayer(LayerState layer){
            if(layer.logicOverride) return logicOverrideBase + (layer.auto ? 0 : layer.selectedIndex + 1);
            return layer.auto ? -1 : layer.selectedIndex;
        }

        /** 解码单层：兼容旧值（< logicOverrideBase）、新值（带该层超控位）。 */
        private void decodeLayer(LayerState layer, int raw){
            if(raw >= logicOverrideBase){
                layer.logicOverride = true;
                int value = raw - logicOverrideBase;
                setSelection(layer, value == 0 ? -1 : value - 1);
            }else{
                layer.logicOverride = false;
                setSelection(layer, raw);
            }
        }

        @Override
        public void configured(Unit unit, Object value){
            if(value instanceof String text){
                boolean override = false;
                int hash = text.indexOf('#');
                if(hash >= 0){
                    override = text.substring(hash + 1).trim().equals("1");
                    text = text.substring(0, hash);
                }
                logicOverride = override;
                String[] parts = text.isEmpty() ? new String[0] : text.split(",");
                ensureLayers();
                for(int i = 0; i < parts.length && i < layers.size; i++){
                    try{
                        decodeLayer(layers.get(i), Integer.parseInt(parts[i].trim()));
                    }catch(NumberFormatException ignored){
                        setSelection(layers.get(i), -1);
                    }
                }
            }else if(value instanceof Integer integer){
                // 基础工厂格式（含超控偏移）：粘贴到变体时按同一编码解码到第一层，
                // 其中偏移位表示“整机超控”（与基础工厂语义一致）
                int raw = integer;
                if(raw >= logicOverrideBase){
                    logicOverride = true;
                    int v = raw - logicOverrideBase;
                    setSelection(layers.first(), v == 0 ? -1 : v - 1);
                }else{
                    logicOverride = false;
                    setSelection(layers.first(), raw);
                }
            }else if(value instanceof int[] arr){
                for(int i = 0; i < arr.length && i < layers.size; i++) setSelection(layers.get(i), arr[i]);
            }else{
                super.configured(unit, value);
            }
        }

        private void setSelection(LayerState layer, int value){
            if(value < 0 || value >= recipes.size){
                layer.auto = true;
                layer.selectedIndex = -1;
            }else{
                layer.auto = false;
                layer.selectedIndex = value;
            }
        }

        @Override
        public byte version(){
            return 3;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            ensureLayers();
            write.i(layers.size);
            for(LayerState layer : layers){
                write.i(encodeLayer(layer));
                write.f(layer.progress);
                // 版本 3 起每层额外保存该层的超控位（旧的全局超控位仍在 super.write 里）
                write.bool(layer.logicOverride);
            }
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 2){
                int count = Math.max(1, read.i());
                layers.clear();
                for(int i = 0; i < count; i++){
                    LayerState layer = new LayerState();
                    int value = read.i();
                    layer.progress = read.f();
                    // 版本 2 的条目没有该层超控位，保持默认 false
                    if(revision >= 3) layer.logicOverride = read.bool();
                    decodeLayer(layer, value);
                    layers.add(layer);
                }
            }else{
                layers.clear();
                LayerState layer = new LayerState();
                layer.auto = auto;
                layer.selectedIndex = selectedIndex;
                layer.progress = progress;
                layers.add(layer);
            }
            ensureLayers();
            // 读档发生在 created() 之后；created() 里的 clampStorage 看不到
            // 存档中恢复的液体。若旧容量下的存档液体总量超过当前容量，
            // 不在这里收缩会让 acceptLiquid 的 liquidTotal() < capacity 永远为 false。
            clampStorage();
        }

        // ---- 每层逻辑控制 / 超控 ----

        /** 任一层处于逻辑控制中（“全部超控”按钮据此决定是否占位显示）。 */
        public boolean anyLayerControlled(){
            for(LayerState layer : layers) if(layer.logicControlled()) return true;
            return false;
        }

        /** 任一层处于超控状态。 */
        public boolean anyLayerOverridden(){
            for(LayerState layer : layers) if(layer.logicOverride) return true;
            return false;
        }

        /** 是否所有层都已超控（“全部超控”按钮据此决定这一下是全部开启还是全部取消）。 */
        public boolean allLayersOverridden(){
            if(layers.isEmpty()) return false;
            for(LayerState layer : layers) if(!layer.logicOverride) return false;
            return true;
        }

        /** 一键切换所有层：当前全为超控则全部取消，否则全部开启。 */
        public void toggleAllLayerOverride(){
            boolean all = allLayersOverridden();
            for(LayerState layer : layers) layer.logicOverride = !all;
        }

        /** "全部超控"按钮的悬停描述：已全部超控时说明超控含义，否则说明按下会发生什么。 */
        @Override
        protected String logicHintDesc(){
            return Core.bundle.get(allLayersOverridden() || logicOverride
                ? "tri.stack.logicOverrideAllDesc"
                : "tri.stack.logicOverrideAllEnable");
        }

        /**
         * 逻辑 {@code control config} 入口。
         * 数值编码：整数部分 = 层号（1 基），小数部分 = 配方号（按位拼成整数，1 基）。
         * 例：{@code 3.1} = 第 3 层锁定 1 号配方；{@code 3.0} = 第 3 层切回自动匹配配方。
         * 注意：逻辑里写整数 {@code 3} 与 {@code 3.0} 是同一个 double，无法区分，因此也按“该层切自动”处理。
         */
        @Override
        public void control(LAccess access, double p1, double p2, double p3, double p4){
            if(logicOverride) return;   // 整机（全部层）超控：忽略一切逻辑控制
            if(access == LAccess.config){
                // 只有成功应用了分层控制才立刻重刷面板，无效指令不打扰界面
                if(applyLayerConfig(p1)){
                    Runnable refresh = this.panelRefresh;
                    if(refresh != null) refresh.run();
                }
                return;
            }
            super.control(access, p1, p2, p3, p4);
        }

        /** 解析并应用一次分层控制；返回是否真的应用了（层号非法等无效指令返回 false）。 */
        private boolean applyLayerConfig(double p1){
            // Double.toString 给出人类书写的最短表示（3.1 -> "3.1"），比浮点取余更稳
            String text = Double.toString(p1);
            int dot = text.indexOf('.');
            if(dot < 0) return false;   // 理论上不会出现，Double.toString 总会带小数位
            int layerNo;
            int recipeNo;
            try{
                layerNo = Integer.parseInt(text.substring(0, dot).trim());
                // 小数部分按位拼成整数：3.1 -> 1，3.12 -> 12，3.0 -> 0
                recipeNo = Integer.parseInt(text.substring(dot + 1).trim());
            }catch(NumberFormatException ignored){
                return false;
            }
            if(layerNo <= 0 || layerNo > layers.size) return false;
            applyLayerSelection(layers.get(layerNo - 1), recipeNo);
            return true;
        }

        /** 对单层应用逻辑选择：配方号 1 基，<=0 切自动；该层已超控则忽略。 */
        private void applyLayerSelection(LayerState layer, int recipeNo){
            if(layer.logicOverride) return;
            layer.logicControlTime = Time.time;
            if(recipeNo <= 0){
                if(!layer.auto || layer.selectedIndex != -1) layer.progress = 0f;
                layer.auto = true;
                layer.selectedIndex = -1;
                return;
            }
            int idx = recipeNo - 1;
            if(idx >= 0 && idx < recipes.size && !MultiRecipeFactory.isLocked(recipes.get(idx))){
                if(layer.auto || layer.selectedIndex != idx) layer.progress = 0f;
                layer.auto = false;
                layer.selectedIndex = idx;
                // 同步已解析的配方号：电力需求统计在下次 updateTile 前就用新配方
                layer.recipeIndex = idx;
            }
        }

        @Override
        public void buildConfiguration(Table table){
            table.clearChildren();

            // 所有受“逻辑控制”影响的顶部按钮（堆叠/降层）：逻辑接管时统一置灰
            Seq<Button> logicButtons = new Seq<>();
            Seq<Boolp> baseDisabled = new Seq<>();
            // 逻辑控制提示：包在一个不可点击的按钮里，紫色文字突出显示；
            // 背景贴图沿用“置灰按钮”的 Tex.buttonDisabled，外观与被禁用的按钮一致
            TextButton.TextButtonStyle hintStyle = new TextButton.TextButtonStyle(Styles.defaultt);
            hintStyle.up = Tex.buttonDisabled;
            hintStyle.over = Tex.buttonOver;          // 悬停暂时高亮，与配方选择按钮的 over 一致
            hintStyle.down = Tex.buttonDisabled;
            hintStyle.disabled = Tex.buttonDisabled;
            hintStyle.fontColor = Color.valueOf("a020f0");
            hintStyle.disabledFontColor = Color.valueOf("a020f0");
            TextButton logicHint = new TextButton("", hintStyle);
            // 超控状态专用样式：文字改为橙色 #ffa500（其余贴图与紫色态一致）
            TextButton.TextButtonStyle hintStyleOverride = new TextButton.TextButtonStyle(hintStyle);
            hintStyleOverride.fontColor = Color.valueOf("ffa500");
            hintStyleOverride.disabledFontColor = Color.valueOf("ffa500");
            // 提示行容器：只在受控或超控时挂上按钮；移除后容器为空，行高为 0，不会留下空行
            Table hintBox = new Table();
            // 悬停描述：浮于控制面板之上的 tooltip（不参与面板布局，显示/隐藏不会改变面板尺寸）
            // 样式对齐设置界面的条目描述：Styles.black8 黑底 + 4 边距；位置贴在提示按钮左下角的下方
            final Runnable[] tipSync = {null};
            Tooltip hintTooltip = new Tooltip(t -> {
                t.background(Styles.black8);
                t.margin(4f);
                t.touchable = Touchable.disabled;
            }){
                {
                    // 已打开时只同步文案与位置，绝不再次 show（避免重复打开或被重新排布导致收起）
                    tipSync[0] = () -> {
                        refillHintTooltip(this);
                        setContainerPosition(logicHint, 0f, 0f);
                    };
                    // 面板关闭、按钮离开场景后浮框若还挂着就自行收起，避免残留在屏幕上
                    container.update(() -> {
                        if(!logicHint.hasParent() && container.hasParent()) hide();
                    });
                }

                @Override
                public void show(Element target, float x, float y){
                    // 每次显示前重填文案，保证与当前“受控 / 超控”状态一致
                    refillHintTooltip(this);
                    super.show(target, x, y);
                }

                @Override
                protected void setContainerPosition(Element target, float x, float y){
                    targetActor = target;
                    // 垂直位置：贴住提示按钮的下边缘
                    float yPos = target.localToStageCoordinates(Tmp.v1.set(0f, 0f)).y;
                    container.pack();
                    // 水平位置：以配置面板的水平中点为锚（按钮本身也是居中的）
                    Tmp.v1.set(table.getWidth() / 2f, 0f);
                    float centerX = table.localToStageCoordinates(Tmp.v1).x;
                    container.setPosition(centerX - container.getWidth() / 2f, yPos, Align.left | Align.top);
                    container.setOrigin(0f, target.getHeight());
                }
            };
            hintTooltip.allowMobile = true;
            logicHint.getLabel().setWrap(false);
            // 提示按钮所在的单元格（用于按文案实测宽度适配）；按钮显示/隐藏时整格重建，避免残留空单元格
            Cell<?>[] hintCell = {null};
            boolean[] hintShown = {false};
            final boolean[] hintHover = {false};
            String[] lastHintText = {null};
            boolean[] hintSizeDirty = {true};
            // 每层面板的刷新回调集合：全部超控 / 单层超控变化后统一重刷
            Seq<Runnable> layerRefreshes = new Seq<>();
            // 刷新按钮状态前，先按“即将显示的文案”实测宽度并适配单元格，避免用旧宽度排版导致换行
            Runnable applyHintText = () -> {
                // “全部超控”按钮：开启态 = 所有层都已超控（或旧的整机超控位仍为 true）
                boolean on = allLayersOverridden() || logicOverride;
                String text = Core.bundle.get(on ? "tri.stack.logicOverrideAllOn" : "tri.stack.logicOverrideAll");
                if(text.equals(lastHintText[0]) && !hintSizeDirty[0]) return;
                lastHintText[0] = text;
                logicHint.setStyle(on ? hintStyleOverride : hintStyle);
                logicHint.setText(text);
                if(hintCell[0] != null){
                    hintCell[0].width(logicHint.getPrefWidth());
                }
                hintSizeDirty[0] = false;
            };
            Runnable refreshStack = () -> {
                // 只有“全部超控”按钮需要占位：任一层被逻辑控制、任一层已单独超控，或旧的整机超控
                boolean logic = anyLayerControlled();
                boolean show = logic || anyLayerOverridden() || logicOverride;
                // 只在“显示/隐藏”状态真正翻转时重建该行，避免反复 remove 留下的空单元格把按钮挤偏
                if(show != hintShown[0]){
                    hintShown[0] = show;
                    hintBox.clearChildren();
                    hintCell[0] = null;
                    if(show){
                        hintCell[0] = hintBox.add(logicHint).center().padBottom(6f);
                        hintSizeDirty[0] = true;
                    }else{
                        // 逻辑结束、按钮撤下时同时收起悬停描述，避免浮框残留
                        hintHover[0] = false;
                        hintTooltip.hide();
                    }
                }
                // 同步按钮文字/样式（内部按实测宽度适配单元格）
                applyHintText.run();
                for(int b = 0; b < logicButtons.size; b++){
                    logicButtons.get(b).setDisabled(logic || baseDisabled.get(b).get());
                }
                // 各层面板一起刷新（层号文案、指示灯、灰显）
                for(int i = 0; i < layerRefreshes.size; i++){
                    layerRefreshes.get(i).run();
                }
            };

            // 悬停状态自己维护：不挂 Tooltip 监听器（它的 touchDown 会先把浮框摘掉再重开，表现为“收回”）
            logicHint.addListener(new InputListener(){
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                    if(pointer == -1) hintHover[0] = true;
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                    if(pointer == -1) hintHover[0] = false;
                }
            });
            // 浮框开关的唯一入口：只要鼠标在按钮上就尝试打开；已经打开时只同步文案/位置，不重复打开
            logicHint.update(() -> {
                if(!logicHint.hasParent()){
                    if(hintTooltip.container.hasParent()) hintTooltip.hide();
                    return;
                }
                if(hintHover[0]){
                    if(!hintTooltip.container.hasParent()){
                        hintTooltip.show(logicHint, 0f, 0f);
                    }else if(tipSync[0] != null){
                        tipSync[0].run();
                    }
                }else if(hintTooltip.container.hasParent()){
                    hintTooltip.hide();
                }
            });
            // 点击“全部超控”：一键切换所有层的超控状态（全部开启 / 全部取消）
            logicHint.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    if(!logicHint.hasParent()) return;
                    // 逐层超控统一生效；旧的整机超控位一并清掉，避免两套状态叠加
                    logicOverride = false;
                    toggleAllLayerOverride();
                    configure(config());
                    applyHintText.run();
                    refreshStack.run();
                    // 若 tooltip 正在显示，就地换成切换后的描述（不重新显示、不影响布局）
                    refillHintTooltip(hintTooltip);
                }
            });

            // “全部超控”按钮：位于堆叠 / 降层按钮组之上，水平居中；无事发生时整行高度为 0
            table.add(hintBox).growX().row();
            // 变体同样允许继续向上堆叠 / 降层（按钮逻辑与基础工厂共用）
            addStackButtons(table, logicButtons, baseDisabled);
            refreshStack.run();
            // 有逻辑控制指令到达时用它立刻重刷面板
            panelRefresh = refreshStack;
            boolean[] lastStackLogic = {anyLayerControlled()};
            boolean[] lastStackShow = {anyLayerControlled() || anyLayerOverridden() || logicOverride};
            table.update(() -> {
                boolean logic = anyLayerControlled();
                boolean show = logic || anyLayerOverridden() || logicOverride;
                if(logic != lastStackLogic[0] || show != lastStackShow[0]){
                    lastStackLogic[0] = logic;
                    lastStackShow[0] = show;
                    refreshStack.run();
                }
            });

            int mode = TriSetting.stackLayout();
            boolean factoryVertical = baseFactory != null ? baseFactory.verticalStackPanels : verticalStackPanels;
            boolean vertical = mode == 1 || (mode == 0 && factoryVertical);
            float panelWidth = 560f;

            Table content = new Table();
            content.top().left();

            for(int i = 0; i < layers.size; i++){
                final int index = i;
                LayerState layer = layers.get(i);
                Table panel = new Table(Tex.button);
                panel.top().left().margin(4f);
                panel.add(Core.bundle.format("tri.stack.layer", i + 1)).left().pad(4).row();

                // 该层的提示 / 超控按钮（徽标文案带层号）；点击切该层超控并保存
                Runnable[] layerRefresh = {null};
                LayerHint hint = new LayerHint(panel, layer, i + 1, () -> {
                    configure(config());
                    if(layerRefresh[0] != null) layerRefresh[0].run();
                    refreshStack.run();
                });

                TextButton.TextButtonStyle autoActive = new TextButton.TextButtonStyle(Styles.defaultt);
                autoActive.up = Tex.buttonOver;
                autoActive.over = Tex.buttonOver;
                autoActive.down = Tex.buttonOver;
                autoActive.fontColor = Color.white;

                TextButton.TextButtonStyle autoInactive = new TextButton.TextButtonStyle(Styles.defaultt);
                autoInactive.up = Tex.buttonTrans;
                autoInactive.over = Tex.buttonTrans;
                autoInactive.down = Tex.buttonTrans;
                autoInactive.fontColor = Color.white;

                Seq<Button> recipeRows = new Seq<>();
                Seq<Image> checks = new Seq<>();
                TextButton autoBtn = new TextButton("", autoActive);

                Runnable refresh = () -> {
                    if(layer.auto){
                        autoBtn.setStyle(autoActive);
                        autoBtn.setText("[white]" + Core.bundle.get("autoRecipe") + "[lightgray]" + Core.bundle.get("onRe"));
                    }else{
                        autoBtn.setStyle(autoInactive);
                        autoBtn.setText("[white]" + Core.bundle.get("autoRecipe") + "[lightgray]" + Core.bundle.get("offRe"));
                    }
                    for(int r = 0; r < recipeRows.size; r++){
                        recipeRows.get(r).setDisabled(layer.logicControlled() || isLocked(recipes.get(r)));
                        boolean selected = !layer.auto && layer.selectedIndex == r;
                        checks.get(r).setDrawable(selected ? Tex.checkOnOver : Tex.checkOff);
                    }
                    autoBtn.setDisabled(layer.logicControlled());
                    hint.refresh();
                };

                autoBtn.clicked(() -> {
                    if(layer.logicControlled()) return;
                    layer.auto = true;
                    layer.selectedIndex = -1;
                    configure(config());
                    refresh.run();
                });
                panel.add(autoBtn).size(240f, 46f).padBottom(6f).row();
                // 该层提示按钮位于 AUTO 与配方列表之间，不占用高度时整行为 0
                panel.add(hint.box).growX().row();

                panel.table(cont -> {
                    for(int r = 0; r < recipes.size; r++){
                        Recipe recipe = recipes.get(r);
                        int finalR = r;
                        Button recipeRow = new Button(Styles.defaultb);
                        recipeRow.left();
                        recipeRow.add("[accent][" + (finalR + 1) + "]:[]").width(48f);
                        recipeRow.table(inner -> {
                            inner.table(left -> {
                                left.left();
                                recipe.inputItem.each(stack -> left.add(MultiRecipeFactory.displayIcon(stack.item)));
                                recipe.inputLiquid.each(stack -> left.add(MultiRecipeFactory.displayIcon(stack.liquid)));
                                recipe.inputPayload.each(stack -> left.add(MultiRecipeFactory.displayIcon(stack.item)));
                                if(recipe.powerUse > 0){
                                    left.table(pow -> {
                                        pow.image(Icon.power);
                                        pow.add("[stat]" + Strings.autoFixed(recipe.powerUse * 60f, 2));
                                    }).row();
                                }
                            }).growX();
                            inner.table(right -> {
                                right.left();
                                right.image(Icon.right).size(32f).padLeft(8f).padRight(12f);
                                recipe.outputItem.each(stack -> right.add(MultiRecipeFactory.displayIcon(stack.item)));
                                recipe.outputLiquid.each(stack -> right.add(MultiRecipeFactory.displayIcon(stack.liquid)));
                                recipe.outputPayload.each(stack -> right.add(MultiRecipeFactory.displayIcon(stack.item)));
                            }).growX();
                        }).growX();

                        recipeRow.clicked(() -> {
                            if(layer.logicControlled() || isLocked(recipe)) return;
                            if(!layer.auto && layer.selectedIndex == finalR){
                                layer.auto = true;
                                layer.selectedIndex = -1;
                            }else{
                                layer.auto = false;
                                layer.selectedIndex = finalR;
                            }
                            configure(config());
                            refresh.run();
                        });

                        recipeRows.add(recipeRow);
                        Image check = new Image(Tex.checkOff);
                        checks.add(check);
                        cont.table(c -> {
                            c.add(recipeRow).growX();
                            c.add(check).size(32f).padLeft(8f);
                        }).fillX().row();
                    }
                }).growX();

                refresh.run();
                // 供“全部超控”等外部动作统一切换后重刷本层
                layerRefresh[0] = refresh;
                layerRefreshes.add(refresh);
                boolean[] lastAuto = {layer.auto};
                int[] lastSelected = {layer.selectedIndex};
                boolean[] lastLogic = {layer.logicControlled()};
                boolean[] lastOverride = {layer.logicOverride};
                panel.update(() -> {
                    boolean logic = layer.logicControlled();
                    if(layer.auto != lastAuto[0] || layer.selectedIndex != lastSelected[0]
                        || logic != lastLogic[0] || layer.logicOverride != lastOverride[0]){
                        lastAuto[0] = layer.auto;
                        lastSelected[0] = layer.selectedIndex;
                        lastLogic[0] = logic;
                        lastOverride[0] = layer.logicOverride;
                        refresh.run();
                    }
                });

                if(vertical){
                    // 不固定宽度，让面板收缩到配方列表的自然宽度。
                    content.add(panel).top().left().pad(4f).row();
                }else{
                    content.add(panel).top().left().pad(4f);
                }
            }

            if(vertical){
                ScrollPane pane = new ScrollPane(content);
                // 竖直排列保留固定高度和纵向滚动；宽度由内部面板列表的自然宽度决定。
                pane.setScrollingDisabled(true, false);
                pane.setFadeScrollBars(false);
                table.add(pane).height(600f);
            }else{
                // 横向排列回退到旧版：不使用 ScrollPane，全部面板直接展开。
                table.add(content).grow();
            }
        }

        @Override
        public void draw(){
            super.draw();
            if(layerCount <= 1 || Fonts.outline == null) return;
            float cx = x + size * 4f;
            float cy = y + size * 8f - 8f;
            // 只画带描边的数字，不画背景块，保持透明底。
            Fonts.outline.draw(String.valueOf(layerCount), cx, cy, Color.white, 0.45f, false, arc.util.Align.center);
        }
    }
}
