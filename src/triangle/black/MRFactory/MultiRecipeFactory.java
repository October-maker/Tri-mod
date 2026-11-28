package triangle.black.MRFactory;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.func.Floatp;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.scene.style.Drawable;
import arc.scene.Element;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.FloatSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Align;
import arc.util.Eachable;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.logic.LAccess;
import mindustry.game.Objectives;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.world.blocks.heat.HeatConsumer;
import mindustry.world.blocks.payloads.BuildPayload;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.*;
import triangle.black.TriDraw;
import triangle.black.draw.TriDrawChain;
import triangle.content.SerTriTechTree;

/**
 * 多配方工厂：一台方块内置多条 {@link Recipe}，可按输入自动匹配，也可手动锁定配方。
 *
 * <p>核心机制：
 * <ul>
 *   <li>配方存放在 {@link #recipes}，输入过滤与输出列表在 {@link #init()} 中根据配方汇总；</li>
 *   <li>动态消费器 {@link ConsumeRecipe} 与动态电力消费器 {@link ConsumePowerRecipe} 按当前配方结算；</li>
 *   <li>自动模式（{@code auto=true}）优先严格匹配；无结果时按配方的“欠压/低温即停”开关放宽条件；</li>
 *   <li>手动模式锁定 {@code selectedIndex}，输入不足时效率归零而不是切换配方；</li>
 *   <li>配方可声明 {@code requiresResearch}，在 {@link #setBars()} 中生成隐藏的科技树研究节点；</li>
 *   <li>配置通过 {@link MultiRecipeFactoryBuild#configured} 同步，并随存档/蓝图序列化。</li>
 * </ul>
 *
 * <p>子类化方式：在 {@code triangle.content.TriFactory} 中 new 出实例后直接向 {@link #recipes} 追加配方。
 */
public class MultiRecipeFactory extends GenericCrafter{
    /** 本工厂的全部配方，顺序即编号顺序。 */
    public Seq<Recipe> recipes = new Seq<>();
    /** 是否允许同实例堆叠；由具体工厂在内容加载前设置。 */
    public boolean stackable = false;
    /**
     * 特殊模式开关：无视“需要研究”直接放开配方。{@code true} 时 {@link #isLocked(Recipe)}
     * 不再做研究/额外条件判定（默认态）。由 {@code TriPresetMode} 按“不完整数据库”词条切换。
     */
    public static boolean ignoreRecipeResearch = false;
    /**
     * 特殊模式开关：堆叠层数上限无视堆叠研究节点（叠到 {@link #stackLimit} 为止）。
     * 由 {@code TriPresetMode} 按“砂石地质”词条与默认态切换。
     */
    public static boolean ignoreStackResearch = false;
    /**
     * 特殊模式开关：禁止多配方工厂堆叠。{@code true} 时放置、覆盖、蓝图、配置面板的堆叠入口
     * 全部拒绝。由 {@code TriPresetMode} 按“砂石地质”词条切换。
     */
    public static boolean stackingDisabled = false;
    /** 最大堆叠层数（含基础层）；堆叠变体按该值预生成。 */
    public int stackLimit = 8;
    /** 战役科技增长线；为空时 stackLimitNow() 直接返回 stackLimit。 */
    public Seq<StackUpgrade> stackUpgrades = new Seq<>();
    /** 已创建的自动堆叠研究节点数量，用于标题序号。 */
    private int stackResearchCount = 0;
    /** 堆叠变体配置面板是否默认竖直排列（false = 现有的横向排列）。 */
    public boolean verticalStackPanels = false;
    /** 是否创建配方研究节点；堆叠变体复用基础工厂的节点。 */
    public boolean registerResearch = true;
    /**
     * 是否在绘制链末尾挂 {@link RecipeStateLayer}（输出满/低温/欠压/缺输入的小色块）。
     * 默认开启；设为 false 时方块保持原版外观。
     */
    public boolean useStateOverlay = true;

    /** 全部物品输出种类（用于统一物流输出）。 */
    public Seq<Item> itemOutput = new Seq<>();
    /** 全部液体输出种类。 */
    public Seq<Liquid> liquidOutput = new Seq<>();
    /** 全部载荷输出种类。 */
    public Seq<UnlockableContent> payloadOutput = new Seq<>();

    /** 输出物品达到容量上限时是否停止生产。 */
    public boolean checkItemOutputFullness = true;
    /** 输出液体达到容量上限时是否停止生产。 */
    public boolean checkLiquidOutputFullness = true;

    /** 欠压跳闸后的恢复锁定时间（秒）。 */
    protected static final float UNDERVOLTAGE_LOCK_TIME = 1f;

    /** 动态电力消耗器（保留引用，供欠压跳闸判定与效率控制使用）。 */
    public ConsumePowerRecipe powerConsumer;

    /**
     * 创建多配方工厂；注册动态输入消费器与电力消费器，并启用旋转与配置面板。
     */
    @SuppressWarnings("this-escape")
    public MultiRecipeFactory(String name) {
        super(name);
        // 注册自定义消费器
        consume(new ConsumeRecipe(MultiRecipeFactoryBuild::getRecipe, MultiRecipeFactoryBuild::getDisplayRecipe));
        // 注册动态电力消耗器
        powerConsumer = new ConsumePowerRecipe(build -> {
            if (build instanceof MultiRecipeFactoryBuild mrb) {
                return mrb.getRecipe();
            }
            return null;
        });
        consume(powerConsumer);
        hasPower = true;
        consumesPower = true;
        rotate = true;
        rotateDraw = false;
        regionRotated1 = -2;
        regionRotated2 = -2;
        configurable = true; // 允许打开配置面板（手动选定配方 / 自动匹配开关）
        saveConfig = true;
        drawArrow = false;//是否显示箭头
        invertFlip = true;
        // 配置类型登记：configured() 由本类自己解析，这里只需要把类型登记进 Block.configurations，
        // 让原版按 value.getClass() 查表时不会走到默认实现。int[] 是本类的新格式载荷。
        config(Integer.class, (MultiRecipeFactoryBuild build, Integer value) -> {});
        config(String.class, (MultiRecipeFactoryBuild build, String value) -> {});
        config(int[].class, (MultiRecipeFactoryBuild build, int[] value) -> {});
    }

    /** 当前是否真的允许堆叠：工厂自身的 {@link #stackable} 加上特殊模式的禁用开关。 */
    public boolean stackAllowed(){
        return stackable && !stackingDisabled;
    }

    /**
     * 液体输出镜像位：bit0 = 左右镜像（左↔右），bit1 = 上下镜像（上↔下）。
     * 镜像不占用 {@code rotation}（rotation 仍用于输出方向的 90° 旋转），
     * 而是作为配置载荷里的独立位（{@link MrfConfig} 的第 3 个 int），因此能随蓝图与存档一起保存。
     */
    public static final int mirrorHorizontal = 1, mirrorVertical = 2;

    /**
     * 输出预览用的液体列表：取建筑“当前展示配方”的输出液体（自动模式下为第一条配方）。
     * 只给 {@link #drawOverlay(float, float, int)} 的悬停/放置虚影用，不参与生产逻辑。
     */
    public Seq<LiquidStack> previewOutputLiquids(Building build){
        if(build instanceof MultiRecipeFactoryBuild mr){
            Recipe recipe = mr.getDisplayRecipe();
            return recipe == null ? null : recipe.outputLiquid;
        }
        return null;
    }

    /** 蓝图预览用：从 {@code plan.config} 里解出配方编号，取对应配方的输出液体。 */
    public Seq<LiquidStack> previewOutputLiquids(BuildPlan plan){
        int index = previewRecipeIndex(plan == null ? null : plan.config);
        if(recipes.size == 0) return null;
        Recipe recipe = index >= 0 && index < recipes.size ? recipes.get(index) : recipes.first();
        return recipe == null ? null : recipe.outputLiquid;
    }

    /** 从配置对象里解出配方编号；自动模式返回 -1。兼容新载荷、超控偏移与堆叠体的逗号多层配置（取第一层）。 */
    protected int previewRecipeIndex(Object config){
        if(config instanceof int[] payload){
            if(!MrfConfig.valid(payload)) return -1;
            int index = MrfConfig.stacked(payload) ? MrfConfig.layerRecipe(payload, 0) : MrfConfig.recipe(payload);
            return index < 0 ? -1 : index;
        }
        int value;
        if(config instanceof Integer integer){
            value = integer;
        }else if(config instanceof String text){
            int hash = text.indexOf('#');
            String head = (hash >= 0 ? text.substring(0, hash) : text).trim();
            int comma = head.indexOf(',');
            if(comma >= 0) head = head.substring(0, comma).trim();
            try{
                value = Integer.parseInt(head);
            }catch(NumberFormatException ignored){
                return -1;
            }
        }else{
            return -1;
        }
        if(value >= MultiRecipeFactoryBuild.logicOverrideBase){
            // 超控态的编码：base + (0=自动, n=配方 n-1)
            int layer = value - MultiRecipeFactoryBuild.logicOverrideBase;
            return layer == 0 ? -1 : layer - 1;
        }
        if(value < 0) return -1;
        return value;
    }

    /** 输出面方向（相对朝向，未叠加 rotation）；没有配置时为 -1。 */
    public int previewOutputDirection(int index){
        return liquidOutputDirections != null && index < liquidOutputDirections.length
                ? liquidOutputDirections[index]
                : -1;
    }

    /** 把 {@code dumpLiquid} 使用的相对方向按镜像位翻转；-1（四面随便倒）保持原样。 */
    public static int mirrorDirection(int direction, int mask){
        if(direction < 0 || mask == 0) return direction;
        if((mask & mirrorHorizontal) != 0){
            if(direction == 0) direction = 2;
            else if(direction == 2) direction = 0;
        }
        if((mask & mirrorVertical) != 0){
            if(direction == 1) direction = 3;
            else if(direction == 3) direction = 1;
        }
        return direction;
    }

    /** 从配置对象里取出镜像位；新载荷直接读，旧字符串读 {@code #f} 标记，其余视为 0。 */
    public static int mirrorMaskOf(Object config){
        if(config instanceof int[] payload){
            return MrfConfig.valid(payload) ? MrfConfig.mirror(payload) : 0;
        }
        if(!(config instanceof String text)) return 0;
        int hash = text.indexOf('#');
        if(hash < 0) return 0;
        int mask = 0;
        for(String token : text.substring(hash + 1).split("#")){
            String value = token.trim();
            if(value.startsWith("f")){
                try{
                    mask |= Integer.parseInt(value.substring(1).trim());
                }catch(NumberFormatException ignored){
                }
            }
        }
        return mask & 3;
    }

    /**
     * 从被覆盖的 {@link ConstructBlock.ConstructBuild} 上取回蓝图翻转留下的镜像位。
     * 原版建造完成时 {@code configured} 不一定会拿到 config（{@code lastConfig} 才是最可靠的副本），
     * 所以换块时再兜底读一次，避免镜像位在完工环节丢失。
     */
    public static int mirrorMaskFromPrevious(Seq<Building> previous){
        if(previous == null) return 0;
        for(Building building : previous){
            if(!(building instanceof ConstructBlock.ConstructBuild construct)) continue;
            Object config = construct.lastConfig;
            int mask = mirrorMaskOf(config);
            if(mask != 0) return mask;
        }
        return 0;
    }

    /** 去掉配置里的 {@code #f<mask>} 标记，其余标记（如堆叠体的 {@code #1}）原样保留。 */
    public static String stripMirror(Object config){
        String text = config == null ? "" : config.toString();
        int hash = text.indexOf('#');
        if(hash < 0) return text;
        StringBuilder out = new StringBuilder(text.substring(0, hash));
        for(String token : text.substring(hash + 1).split("#")){
            String value = token.trim();
            if(value.isEmpty() || value.startsWith("f")) continue;
            out.append('#').append(value);
        }
        return out.toString();
    }

    /** 用新的镜像位重建配置对象，保持原类型：新载荷返回克隆的 int[]，旧字符串返回 {@code #f} 串。 */
    public static Object withMirror(Object config, int mask){
        if(config instanceof int[] payload){
            if(!MrfConfig.valid(payload)) return config;   // 不认识的版本不动，避免退化成 "[I@..." 字符串
            int[] copy = payload.clone();
            MrfConfig.setMirror(copy, mask);
            return copy;
        }
        if(config == null){
            // 空配置（全新计划）没有旧字符串可拆：直接给一份自动模式的基座载荷，
            // 避免把 "#f1" 这种旧格式字符串塞进新蓝图。
            return MrfConfig.encode(-1, true, false, mask, null, null);
        }
        String base = stripMirror(config);
        return (mask & 3) == 0 ? base : base + "#f" + (mask & 3);
    }

    /**
     * 蓝图镜像翻转入口：原版只在 {@link mindustry.input.InputHandler#flipPlans} 里调用。
     * 本工厂贴图不随 rotation 旋转，旋转值保留给输出方向的 90° 转向，
     * 所以这里只翻转镜像位，让液体输出面跟着镜像，而不是把整机转 180°。
     */
    @Override
    public void flipRotation(BuildPlan plan, boolean horizontal){
        if(plan == null) return;
        plan.config = withMirror(plan.config, mirrorMaskOf(plan.config) ^ (horizontal ? mirrorHorizontal : mirrorVertical));
        // 贴图旋转对称，rotation 保持原样（不调 super：原版会做 ±180° 旋转）
    }

    /** 放置计划实际建造的方块；堆叠工厂在这里切换为固定层数变体。 */
    @Override
    public Block getReplacement(BuildPlan req, Seq<BuildPlan> plans){
        if(stackAllowed()){
            Block variant = StackedMultiRecipeFactory.nextVariant(this, req);
            if(variant != null) return variant;
            // 已达上限时把计划固定到当前顶层方块；顶层 replaceable=false，
            // 避免把已有堆叠替换回基础方块。
            Block top = StackedMultiRecipeFactory.topVariant(this, req);
            if(top != null) return top;
        }
        return super.getReplacement(req, plans);
    }

    /** 允许同实例覆盖放置，作为堆叠入口。 */
    @Override
    public boolean canPlaceOn(Tile tile, mindustry.game.Team team, int rotation){
        if(stackAllowed() && tile != null && tile.build != null){
            Block existing = tile.build.block;
            boolean chain = existing == this
                || (existing instanceof StackedMultiRecipeFactory variant && variant.baseFactory == this);
            if(chain && StackedMultiRecipeFactory.layersOf(existing) < stackLimitNow()) return true;
        }
        return super.canPlaceOn(tile, team, rotation);
    }

    @Override
    public boolean canReplace(Block other){
        if(stackAllowed() && other == this) return true;
        if(stackAllowed() && other instanceof StackedMultiRecipeFactory variant
            && variant.baseFactory == this && variant.stackAllowed()) return true;
        return super.canReplace(other);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        if((stackAllowed() || this instanceof StackedMultiRecipeFactory)
            && StackedMultiRecipeFactory.atStackLimit(this, x, y)){
            drawPlaceText(Core.bundle.get("tri.stack.limit"), x, y, false);
        }
    }

    /**
     * 预览合法性：原版按“菜单里的方块”判定，同方块同朝向覆盖到自身会被判非法，
     * 而堆叠实际会把计划换成下一层变体。这里只有“同链覆盖且还有层数”这一种情况
     * 改判为合法，其它非法原因（遮挡、敌核心范围等）仍然显示红色虚影。
     */
    private boolean stackPreviewValid(BuildPlan req){
        if(!stackAllowed() || req == null || Vars.world == null || Vars.player == null) return false;
        Tile tile = Vars.world.tile(req.x, req.y);
        if(tile == null || tile.build == null) return false;
        if(tile.team() != Vars.player.team()) return false;
        Block existing = tile.build.block;
        boolean chain = existing == this
            || (existing instanceof StackedMultiRecipeFactory variant && variant.baseFactory == this);
        if(!chain) return false;
        return StackedMultiRecipeFactory.nextVariant(this, req) != null;
    }

    @Override
    public void drawPlan(BuildPlan req, Eachable<BuildPlan> list, boolean valid){
        super.drawPlan(req, list, valid || stackPreviewValid(req));
    }

    /** 一条堆叠层数科技增长线。 */
    public static class StackUpgrade{
        public final UnlockableContent tech;
        public final int layers;

        public StackUpgrade(UnlockableContent tech, int layers){
            this.tech = tech;
            this.layers = layers;
        }
    }

    /**
     * 添加一个已有的科技节点作为堆叠层数增长条件。
     * 需要在该工厂 registerVariants 之前调用，或再次调用 registerVariants。
     */
    public MultiRecipeFactory stackUpgrade(UnlockableContent tech, int layers){
        if(layers <= 1) return this;
        stackUpgrades.add(new StackUpgrade(tech, layers));
        stackLimit = Math.max(stackLimit, layers);
        return this;
    }

    /**
     * 自动创建一个隐藏的堆叠层数研究节点（挂在当前工厂科技树节点下）。
     * cost 为研究消耗；研究完成后 stackLimitNow() 提升到 layers。
     */
    public MultiRecipeFactory stackUpgrade(int layers, ItemStack[] cost){
        return stackUpgrade(layers, cost, null);
    }

    /** 工厂在 bundle 中配置的显示名；未配置时回退到内部 name。 */
    public String displayName(){
        // Content.name 对 Java 内容可能已经带有 mod 前缀（triangle-BulletFactory），
        // 因此直接用完整 name 拼 block 键，不能再次叠加 mod 前缀。
        String key = "block." + name + ".name";
        String value = Core.bundle.get(key, "");
        if(value == null || value.isEmpty()){
            value = Core.bundle.get("block.triangle-" + plainName() + ".name", "");
        }
        if(value != null && !value.isEmpty()) return value;
        if(localizedName != null && !localizedName.isEmpty()) return localizedName;
        return name;
    }

    /** 不含 mod 前缀的内容名，用于稳定的 bundle 键。 */
    public String plainName(){
        return plainName(this);
    }

    private static String plainName(UnlockableContent content){
        if(content == null) return "";
        String prefix = content.minfo != null && content.minfo.mod != null
            ? content.minfo.mod.name + "-" : null;
        if(prefix != null && content.name.startsWith(prefix)){
            return content.name.substring(prefix.length());
        }
        return content.name;
    }

    /**
     * 自动创建堆叠研究节点，并指定详情描述 key。
     * descriptionKey 为 null 时自动使用
     * {@code tri.research.<工厂名>-stack-<层数>.description}。
     */
    public MultiRecipeFactory stackUpgrade(int layers, ItemStack[] cost, String descriptionKey){
        if(layers <= 1) return this;
        String contentName = plainName() + "-stack-" + layers;
        RecipeResearch tech = new RecipeResearch(contentName);
        tech.cost = cost == null ? ItemStack.empty : cost;
        String baseName = displayName();
        int upgradeIndex = ++stackResearchCount;
        tech.localizedName = Core.bundle.format("tri.research.stackName", baseName, upgradeIndex);
        tech.uiIcon = stackResearchIcon(layers);
        String descKey = descriptionKey != null
            ? descriptionKey
            : "tri.research." + plainName() + "-stack-" + layers + ".description";
        tech.description = Core.bundle.get(descKey, "");
        if(tech.description == null || tech.description.isEmpty()){
            // 兼容使用完整内容名（含 mod 前缀）的旧键。
            tech.description = Core.bundle.get(
                "tri.research." + contentName + ".description", "");
        }
        tech.hideDatabase = true;
        tech.databaseTabs.clear();
        stackUpgrades.add(new StackUpgrade(tech, layers));
        stackLimit = Math.max(stackLimit, layers);
        Events.on(ContentInitEvent.class, event -> {
            // 方块 load() 完成后 uiIcon 才可靠，这里再解析一次覆盖贴图/工厂图标。
            tech.uiIcon = stackResearchIcon(layers);
            SerTriTechTree.addNodeLater(this, tech);
        });
        return this;
    }

    /** 当前实际允许的堆叠层数；未配置科技增长线时直接使用 stackLimit。 */
    public int stackLimitNow(){
        // 特殊模式“砂石地质”：整条链上都不允许再叠
        if(stackingDisabled) return 1;
        if(Vars.state != null && Vars.state.rules.infiniteResources) return stackLimit;
        // 特殊模式默认态：无视堆叠研究节点，直接叠到该工厂的 stackLimit
        if(ignoreStackResearch) return stackLimit;
        if(stackUpgrades.isEmpty()) return stackLimit;
        int limit = 1;
        for(StackUpgrade upgrade : stackUpgrades){
            if(upgrade.tech == null || upgrade.tech.unlocked()){
                limit = Math.max(limit, upgrade.layers);
            }
        }
        return Math.min(stackLimit, limit);
    }

    /**
     * 把现有 drawer 包装成相位链，并挂上状态叠加层。
     *
     * <p>只处理非堆叠体：堆叠变体在 {@link StackedMultiRecipeFactory#load()} 里
     * 直接复用基础工厂的链，避免用变体名重新加载贴图。
     */
    @Override
    public void load(){
        if(useStateOverlay && !(this instanceof StackedMultiRecipeFactory) && !(drawer instanceof TriDrawChain)){
            drawer = TriDrawChain.of(drawer).add(new RecipeStateLayer());
        }
        super.load();
    }

    /**
     * 根据配方表汇总输入过滤与输出列表，并设置父类所需的输出占位。
     * 配方为空时使用铜/水作为零数量占位，避免原版生产逻辑出现空数组。
     */
    @Override
    public void init() {
        super.init();

        // MRF 必须有方向：rotate=false 会让侧热、蓝图输入和部分鼠标交互失去方向基准。
        // 即使贴图不旋转，也保留逻辑 rotation，故 rotateDraw 仍为 false。
        rotate = true;
        rotateDraw = false;

        // 设置输入过滤器和输出列表（去重；同一产物可能被多个配方共享）。
        Seq<ItemStack> allOutputItems = new Seq<>();
        Seq<LiquidStack> allOutputLiquids = new Seq<>();
        for(int r = 0; r < recipes.size; r++){
            Recipe recipe = recipes.get(r);
            for(int i = 0; i < recipe.inputItem.size; i++){
                itemFilter[recipe.inputItem.get(i).item.id] = true;
            }
            for(int i = 0; i < recipe.inputLiquid.size; i++){
                liquidFilter[recipe.inputLiquid.get(i).liquid.id] = true;
            }

            for(int i = 0; i < recipe.outputItem.size; i++){
                ItemStack stack = recipe.outputItem.get(i);
                if(!allOutputItems.contains(s -> s.item == stack.item)){
                    allOutputItems.add(stack);
                    itemOutput.add(stack.item);
                }
            }
            for(int i = 0; i < recipe.outputLiquid.size; i++){
                LiquidStack stack = recipe.outputLiquid.get(i);
                if(!allOutputLiquids.contains(s -> s.liquid == stack.liquid)){
                    allOutputLiquids.add(stack);
                    liquidOutput.add(stack.liquid);
                }
            }
            for(int i = 0; i < recipe.outputPayload.size; i++){
                UnlockableContent payload = recipe.outputPayload.get(i).item;
                if(!payloadOutput.contains(payload)){
                    payloadOutput.add(payload);
                }
            }
        }

        // 初始化父类兼容输出字段：使用全部配方的并集，而不是只看第一个配方。
        if(allOutputItems.isEmpty()){
            outputItems = new ItemStack[]{new ItemStack(Items.copper, 0)};
        }else{
            outputItems = allOutputItems.toArray(ItemStack.class);
        }

        if(allOutputLiquids.isEmpty()){
            outputLiquids = new LiquidStack[]{new LiquidStack(Liquids.water, 0f)};
        }else{
            outputLiquids = new LiquidStack[allOutputLiquids.size];
            for(int i = 0; i < allOutputLiquids.size; i++){
                Liquid liquid = allOutputLiquids.get(i).liquid;
                outputLiquids[i] = new LiquidStack(liquid, 0f);
            }
        }

        craftTime = 60f;
        if (!liquidOutput.isEmpty()) outputsLiquid = true;

        // 把“可堆叠”写入方块描述：description 已由父类 load() 从 bundle 读取。
        if(stackable){
            String stackDesc = Core.bundle.format("tri.stack.description", stackLimit);
            if(description == null || description.isEmpty()){
                description = stackDesc;
            }else if(!description.contains(stackDesc)){
                description += "\n" + stackDesc;
            }
        }
    }

    /**
     * 悬停 / 放置虚影里的液体输出图标。
     *
     * <p>原版这套图标就在 {@link GenericCrafter#drawOverlay(float, float, int)} 里：
     * 调用链是 {@code Block.drawPlace → drawOverlay}，对每个输出按
     * {@code liquidOutputDirections[i]}（再叠加 rotation）把该液体的 {@code fullIcon} 画在对应的面上，
     * 尺寸 8×8。这里保持完全一样的画法，只把数据换成本 mod 的“当前配方输出液体 + 镜像位”：
     * 悬停落点上如果是 MRF 建筑，就取它自己的配方/mask/朝向，这样虚影的液体种类与方向与实际倒液一致。
     */
    @Override
    public void drawOverlay(float x, float y, int rotation){
        Seq<LiquidStack> outputs = null;
        MultiRecipeFactory factory = this;
        int mask = 0;

        if(Vars.world != null){
            Tile tile = Vars.world.tileWorld(x, y);
            Building build = tile == null ? null : tile.build;
            if(build instanceof MultiRecipeFactoryBuild mr && build.block instanceof MultiRecipeFactory hoveredFactory){
                outputs = hoveredFactory.previewOutputLiquids(mr);
                mask = mr.liquidMirror;
                factory = hoveredFactory;
            }
        }

        if(outputs == null || outputs.size == 0){
            super.drawOverlay(x, y, rotation);
            return;
        }

        float off = size * 4f + 4f;
        for(int i = 0; i < outputs.size; i++){
            LiquidStack output = outputs.get(i);
            if(output == null || output.liquid == null) continue;
            int dir = factory.previewOutputDirection(i);
            if(dir == -1) continue;
            int side = Mathf.mod(mirrorDirection(dir, mask) + rotation, 4);
            Draw.rect(output.liquid.fullIcon,
                x + Geometry.d4x(side) * off,
                y + Geometry.d4y(side) * off,
                8f, 8f);
        }
    }

    @Override
    public boolean outputsItems() {
        return !itemOutput.isEmpty() || !liquidOutput.isEmpty();
    }

    /**
     * 移除原版单配方的输入/输出/时间/热量/电力统计，改为用 {@link #displayRecipes()} 统一展示全部配方。
     */
    @Override
    public void setStats() {
        super.setStats();
        stats.remove(Stat.input);
        stats.remove(Stat.output);
        stats.remove(Stat.productionTime);
        stats.remove(Stat.heatCapacity);
        stats.remove(Stat.powerUse);
        stats.add(Stat.input, displayRecipes());
    }

    // 圆角描边等共用绘制工具见 triangle.black.TriDraw

    /**
     * 圆形进度条：禁用原版 Bar 的数值平滑（value 逐渐逼近 fraction），
     * 每次绘制前 {@code snap()} 使填充严格等于 fraction，保证按生产时长从左到右循环填充。
     */
    public static class LoopingBar extends Bar {
        public LoopingBar(String name, Color color, Floatp fraction) {
            super(name, color, fraction);
        }

        @Override
        public void draw() {
            snap();
            super.draw();
        }
    }

    /**
     * 构建“全部配方”统计表：每条配方一行，依次展示输入、电力/热量、生产时间与输出。
     */
    public StatValue displayRecipes() {
        return table -> {
            table.row();
            table.table(cont -> {
                for (int i = 0; i < recipes.size; i++) {
                    Recipe recipe = recipes.get(i);
                    int finalI = i;
                    cont.table(t -> {
                        t.left().marginLeft(12f).add("[accent][" + (finalI + 1) + "]:[]").width(48f);
                        t.table(inner -> { // 加小背景
                            inner.table(TriDraw.whiteOutlined(Styles.grayPanel), rec -> { // 输入框背景（带白色描边）
                                rec.left();
                                recipe.inputItem.each(stack -> rec.add(display(stack.item, stack.amount, recipe.craftTime)).row());
                                recipe.inputLiquid.each(stack -> rec.add(display(stack.liquid, stack.amount * 60, 60f)).row());
                                recipe.inputPayload.each(stack -> rec.add(display(stack.item, stack.amount, recipe.craftTime)).row());
                            }).growX().pad(6).margin(6);

                            inner.table(rec ->{
                                rec.left();
                                // 添加电力消耗显示（配方存在电力消耗时）
                                if (recipe.powerUse > 0) {
                                    rec.table(pow ->{
                                        pow.image(Icon.power);
                                        pow.add("[stat]" + Strings.autoFixed(recipe.powerUse * 60f, 2) + " [lightgray]" + StatUnit.powerSecond.localized()).row();
                                    }).row();
                                    if (recipe.stopUndervoltage) {
                                        rec.add("[#FF3333]" + Core.bundle.get("notUndervoltage")).row();
                                    }else {
                                        rec.add("[#FFD27E]" + Core.bundle.get("undervoltage")).row();
                                    }
                                }
                                // 生产进度条（替代原箭头）：按 craftTime 循环填充，并显示生产时长文本
                                float sec = recipe.craftTime / 60f;
                                String dur;
                                if (sec == Mathf.floor(sec)) {
                                    dur = (int) sec + "sec";
                                } else {
                                    dur = Strings.autoFixed(sec, 2);
                                    if (dur.endsWith("0")) dur = dur.substring(0, dur.length() - 1);
                                    dur += "sec";
                                }
                                float period = Math.max(recipe.craftTime / 60f, 1f / 60f);
                                long startNanos = System.nanoTime();
                                rec.add(new LoopingBar(dur, Pal.accent, () -> {
                                    // 使用实时时钟(System.nanoTime)，避免游戏暂停（如从核心数据库查看详情）时进度冻结
                                    float elapsed = (System.nanoTime() - startNanos) / 1e9f; // 单调递增，单位秒
                                    return (elapsed % period) / period;
                                })).width(128f).height(18f).padLeft(8f).padRight(12f).row();
                                // 添加热量消耗显示（配方启用热量且需要输入热量时）
                                if (recipe.heatEnabled && recipe.heatRequirement > 0) {
                                    rec.table(heat ->{
                                        heat.image(Icon.waves).pad(5f).color(Color.valueOf("FF6666"));
                                        heat.add(Strings.autoFixed(recipe.heatRequirement, 2) + " [lightgray]" + StatUnit.heatUnits.localized()).row();
                                    }).row();
                                    if (recipe.stopLowTemperature) {
                                        rec.add("[#FF3333]" + Core.bundle.get("notLowTemperature")).row();
                                    }else {
                                        rec.add("[#FFD27E]" + Core.bundle.get("lowTemperature")).row();
                                    }
                                }
                            }).pad(6);

                            inner.table(TriDraw.whiteOutlined(Styles.grayPanel), rec -> {
                                recipe.outputItem.each(stack -> rec.add(display(stack.item, stack.amount, recipe.craftTime)).row());
                                recipe.outputLiquid.each(stack -> rec.add(display(stack.liquid, stack.amount * 60, 60f)).row());
                                recipe.outputPayload.each(stack -> rec.add(display(stack.item, stack.amount, recipe.craftTime)).row());
                                // 添加热量产出显示（配方启用热量且能产出热量时）
                                if (recipe.heatEnabled && recipe.heatOutput > 0) {
                                    rec.add("[#FF6666]" + Strings.autoFixed(recipe.heatOutput, 2) + " [lightgray]" + StatUnit.heatUnits.localized()).row();
                                }
                            }).growX().pad(6).margin(6);
                        }).margin(6);
                        t.row();
                    }).fillX();
                    cont.row();
                }
            });
        };
    }

    /**
     * 创建单个资源的显示单元：图标 + 数量 + 名称 + 每秒速率。
     *
     * @param content    物品/液体/载荷
     * @param amount     每周期数量
     * @param timePeriod 周期时长（tick），用于换算每秒速率
     */
    public static Table display(UnlockableContent content, float amount, float timePeriod) {
        Table table = new Table();
        Stack stack = new Stack();

        stack.add(new Table(o -> {
            o.left();
            o.add(new Image(content.uiIcon)).size(32f).scaling(Scaling.fit);
        }));

        if (amount != 0) {
            stack.add(new Table(t -> {
                t.left().bottom();
                t.add(amount >= 1000 ? UI.formatAmount((int) amount) : Strings.autoFixed(amount, 2))
                        .style(Styles.outlineLabel);
                t.pack();
            }));
        }

        StatValues.withTooltip(stack, content);

        table.add(stack);
        table.add((content.localizedName + "\n") + "[lightgray]" +
                        Strings.autoFixed(amount / (timePeriod / 60f), 2) + StatUnit.perSecond.localized() ) // 每秒产出速率；电量消耗显示已移除
                .padLeft(2).padRight(5).style(Styles.outlineLabel);
        return table;
    }

    /** 仅显示资源贴图（用于配置面板中的配方行），悬停仍显示资源名。 */
    public static Table displayIcon(UnlockableContent content) {
        Table table = new Table();
        Stack stack = new Stack();
        stack.add(new Table(o -> {
            o.left();
            o.add(new Image(content.uiIcon)).size(32f).scaling(Scaling.fit);
        }));
        StatValues.withTooltip(stack, content);
        // 配置面板里的图标只保留悬停提示，不再点击打开内容详情页。
        for(int i = stack.getListeners().size - 1; i >= 0; i--){
            if(stack.getListeners().get(i) instanceof arc.scene.event.ClickListener){
                stack.removeListener(stack.getListeners().get(i));
            }
        }
        table.add(stack);
        return table;
    }

    /**
     * 为配方涉及的液体添加状态条，并按当前配方显示热量输入/输出条；
     * 同时创建需研究配方的隐藏研究条目，并在内容初始化完成后挂到科技树。
     */
    @Override
    public void setBars() {
        super.setBars();
        removeBar("liquid");

        recipes.each(recipe -> {
            recipe.inputLiquid.each(stack -> addLiquidBar(stack.liquid));
            recipe.outputLiquid.each(stack -> addLiquidBar(stack.liquid));
        });

        // 添加热量消耗进度条
        addBar("heatInput", (MultiRecipeFactoryBuild entity) -> {
            Recipe recipe = entity.getRecipe();
            if (recipe != null && recipe.heatEnabled && recipe.heatRequirement > 0) {
                return new Bar(() ->
                    Core.bundle.format("bar.heatpercent", (int)(entity.rawHeat + 0.01f), (int)(entity.heatEfficiencyScale() * 100 + 0.01f)),
                    () -> Pal.lightOrange,
                    () -> entity.rawHeat / recipe.heatRequirement);
            }
            return null;
        });

        // 添加热量产出进度条
        addBar("heatOutput", (MultiRecipeFactoryBuild entity) -> {
            Recipe recipe = entity.getRecipe();
            if (recipe != null && recipe.heatEnabled && recipe.heatOutput > 0) {
                return new Bar("bar.heat", Pal.lightOrange, () -> entity.warmup);
            }
            return null;
        });

        // 为需要研究的配方创建研究条目（对象创建不依赖科技树，可在此进行）
        for (int i = 0; i < recipes.size; i++) {
            Recipe r = recipes.get(i);
            if (registerResearch && r.requiresResearch) {
                r.research = createResearch(this, r, i + 1);
            }
        }

        // 挂载研究条目到科技树节点的时机延后到 ContentInitEvent 之后：
        // 此时原版科技树（SerpuloTechTree）已完全加载，且工厂自身的科技树节点已就位，
        // 避免在 init() 阶段 TechTree.all 尚未包含工厂节点导致 find 返回 null 而无法挂载。
        Events.on(ContentInitEvent.class, e -> {
            if (!registerResearch) return;
            for (int i = 0; i < recipes.size; i++) {
                Recipe r = recipes.get(i);
                if (r.requiresResearch && r.research instanceof RecipeResearch research) {
                    // 物品/方块贴图在 ContentInit 时才全部就绪；此时重新解析，避免 ohno。
                    research.uiIcon = researchIcon(this, i + 1);
                    research.description = researchDescription(this, r, i + 1, this.name + "-" + (i + 1));
                    SerTriTechTree.addNodeLater(this, research);
                }
            }
        });
    }

    /**
     * 原版科技树“研究条件”中显示的额外条件。
     * 条件未完成时，ResearchDialog 会在节点详情里显示该项。
     */
    public static class UnlockConditionObjective implements Objectives.Objective {
        public final Boolp condition;
        public final String text;

        public UnlockConditionObjective(Boolp condition, String text){
            this.condition = condition;
            this.text = text;
        }

        @Override
        public boolean complete(){
            if(condition == null) return true;
            try{
                return condition.get();
            }catch(Throwable t){
                Log.err("[triangle] recipe unlock condition failed.", t);
                return false;
            }
        }

        @Override
        public String display(){
            return text != null && !text.isEmpty() ? text : Core.bundle.get("tri.recipe.unlockCondition");
        }
    }

    /**
     * 配方研究条目：承载研究消耗与名称，不进入数据库，只通过科技树节点展示。
     */
    public static class RecipeResearch extends UnlockableContent {
        /** 研究消耗。 */
        public ItemStack[] cost;
        /** 额外解锁条件；为空表示无额外条件。 */
        public arc.func.Boolp unlockCondition;
        /** 科技树“研究条件”中显示的文字。 */
        public String unlockConditionText;

        public RecipeResearch(String name) {
            super(name);
        }

        @Override
        public ContentType getContentType() {
            return ContentType.error;
        }

        @Override
        public ItemStack[] researchRequirements() {
            return cost == null ? ItemStack.empty : cost;
        }

        /** 条件不满足时即使在科技树里点击研究，也不能真正解锁。 */
        @Override
        public boolean unlocked(){
            if(Vars.state != null && Vars.state.rules.infiniteResources) return true;
            if(unlockCondition != null){
                try{
                    if(!unlockCondition.get()) return false;
                }catch(Throwable t){
                    Log.err("[triangle] recipe unlock condition failed.", t);
                    return false;
                }
            }
            return super.unlocked();
        }

        /** 条件不满足时拦截 unlock，避免科技树绕过条件。 */
        @Override
        public void unlock(){
            if(Vars.state != null && Vars.state.rules.infiniteResources){
                super.unlock();
                return;
            }
            if(unlockCondition != null){
                try{
                    if(!unlockCondition.get()) return;
                }catch(Throwable t){
                    Log.err("[triangle] recipe unlock condition failed.", t);
                    return;
                }
            }
            super.unlock();
        }
    }

    /**
     * 为单个配方创建研究条目。
     * 研究消耗默认取 {@code inputItem}，可由 {@link Recipe#researchCost} 覆盖；
     * 图标固定读取 atlas 中的 {@code <完整工厂名>-<配方编号>}，不再使用产物/工厂贴图兜底。
     */
    public static UnlockableContent createResearch(UnlockableContent factory, Recipe r, int index) {
        // 配方研究节点在 init() 阶段创建，此时 ContentLoader.currentMod 已清空，
        // 不会自动补 triangle- 前缀；必须使用已带前缀的 factory.name。
        String rname = factory.name + "-" + index;
        RecipeResearch c = new RecipeResearch(rname);
        c.cost = r.researchCost != null ? r.researchCost : r.inputItem.toArray(ItemStack.class);
        c.unlockCondition = r.unlockCondition;
        c.unlockConditionText = r.unlockConditionText;
        c.localizedName = researchName(factory, r, index);
        c.uiIcon = researchIcon(factory, index);
        c.description = researchDescription(factory, r, index, rname);
        // 研究条目不注册进 Vars.content；hideDatabase=true 使数据库的 select 过滤将其排除（官方隐藏方式），databaseTabs 清空确保不归属任何 tab。
        // 仅通过科技树（TechNode）显示。挂载由 SerTriTechTree 在 ContentInitEvent 后处理。
        c.hideDatabase = true;
        c.databaseTabs.clear();
        return c;
    }

    private static String researchName(UnlockableContent factory, Recipe r, int index) {
        if (!r.outputItem.isEmpty()) return r.outputItem.first().item.localizedName;
        if (!r.outputLiquid.isEmpty()) return r.outputLiquid.first().liquid.localizedName;
        if (!r.outputPayload.isEmpty()) return r.outputPayload.first().item.localizedName;
        return factory.name + "-" + index;
    }

    private static String researchDescription(UnlockableContent factory, Recipe r, int index, String rname) {
        // 优先使用显式 key，否则使用不含 mod 前缀的稳定自动 key。
        String descKey = r.researchDescriptionKey != null
            ? r.researchDescriptionKey
            : "tri.research." + plainName(factory) + "-" + index + ".description";
        String desc = Core.bundle.get(descKey, "");
        if(desc == null || desc.isEmpty()){
            // 兼容使用完整内容名（含 mod 前缀）的键。
            desc = Core.bundle.get("tri.research." + rname + ".description", "");
        }
        return desc == null ? "" : desc;
    }

    /** 配方研究节点图标名：与节点内部名一致，为 <完整内容名>-<配方编号>。 */
    public static String recipeResearchIconName(UnlockableContent factory, int index){
        return factory.name + "-" + index;
    }

    private static TextureRegion researchIcon(UnlockableContent factory, int index) {
        return Core.atlas.find(recipeResearchIconName(factory, index));
    }

    /** 堆叠节点图标名：<完整内容名>-stack-<层数>。 */
    public static String stackResearchIconName(MultiRecipeFactory factory, int layers){
        return factory.name + "-stack-" + layers;
    }

    private TextureRegion stackResearchIcon(int layers){
        return Core.atlas.find(stackResearchIconName(this, layers));
    }

    /** 配方是否因额外条件或“需要研究且尚未解锁”而处于锁定状态。 */
    public static boolean isLocked(Recipe r) {
        if (r == null) return false;
        // 沙盒模式：所有配方、研究条目和额外解锁条件都视为已满足。
        if (Vars.state != null && Vars.state.rules.infiniteResources) return false;
        // 特殊模式默认态（“不完整数据库”未选中）：需要研究的配方也直接可用。
        if (ignoreRecipeResearch) return false;
        // unlockCondition 只约束需要研究的配方；普通配方即使误设条件也保持原行为。
        if (r.requiresResearch && r.unlockCondition != null) {
            try {
                if (!r.unlockCondition.get()) return true;
            } catch (Throwable t) {
                Log.err("[triangle] recipe unlock condition failed.", t);
                return true;
            }
        }
        return r.requiresResearch && (r.research == null || !r.research.unlocked());
    }

    /** 工厂实体：维护当前配方、热量、自动/手动模式与配置序列化。 */
    public class MultiRecipeFactoryBuild extends GenericCrafterBuild implements HeatConsumer, HeatBlock {
        /** 当前生效的配方索引；-1 表示无可用配方。 */
        public int recipeIndex = -1;
        /** 四面热量输入。 */
        public float[] sideHeat = new float[4];
        /** 热量兜底去重用：同一多格热源在 proximity 中占多槽，只计一次。 */
        public Building[] heatSeen = new Building[8];
        /** 当前有效热量（供可用性/消耗检查使用）。 */
        public float heat = 0f;
        /** 实际接收的热量（供效率计算与显示使用）。 */
        public float rawHeat = 0f;
        /** 预热值（用于热量产出）。 */
        public float warmup = 0f;
        /** true = 自动匹配配方；false = 手动使用 {@link #selectedIndex}。 */
        public boolean auto = true;
        /** 手动模式选定的配方索引；-1 表示未选定。 */
        public int selectedIndex = -1;
        /** 欠压跳闸后的恢复锁定（秒）；避免一断电就重启、重启又断电的逐帧振荡。 */
        public float undervoltageLock = 0f;
        /** 最近一次被逻辑 {@code control config} 驱动的时刻（Vars 的 Time.time）；< 0 表示从未被逻辑控制。 */
        public float logicControlTime = -1f;
        /** 逻辑控制的判定时间窗（秒）。逻辑每帧都可能执行，超出窗口即认为逻辑已不再驱动本建筑。 */
        public static final float logicControlWindow = 1.5f;
        /** true = 玩家启用了“超控”：本建筑忽略任何逻辑控制（control 等逻辑入口直接跳过）。随配置/蓝图/存档保存。 */
        public boolean logicOverride = false;
        /** 液体输出镜像位：bit0 左右镜像、bit1 上下镜像；随配置载荷（int[]）与存档保存。 */
        public int liquidMirror = 0;
        /** 配置编码里“超控”的偏移基数；旧配置值（-1 与 0..n-1）都小于它，因此向后兼容。 */
        protected static final int logicOverrideBase = 10000;
        /** 控制面板刷新回调（面板打开时挂上，关闭后为 null）：逻辑控制指令到达时立刻重刷面板，避免下一帧才更新。 */
        protected transient Runnable panelRefresh;

        /** 本建筑当前是否处于逻辑控制下（最近 logicControlWindow 秒内收到过 control config，且玩家未启用超控）。 */
        public boolean logicControlled(){
            return !logicOverride && logicControlTime >= 0f && Time.time - logicControlTime <= logicControlWindow;
        }

        /** 悬停描述文案：随当前“受控 / 超控”状态变化。 */
        protected String logicHintDesc(){
            return logicOverride ? Core.bundle.get("tri.stack.logicOverrideDesc") : Core.bundle.get("tri.stack.logicControlledDesc");
        }

        /** 就地重填悬停 tooltip 的文案（正在显示时会立刻反映最新状态，不改变面板布局）。 */
        protected void refillHintTooltip(Tooltip tooltip){
            Table c = tooltip.container;
            String desc = logicHintDesc();
            // 文案没变就不重建：悬停期间会被驱动循环反复调用，避免每帧 clear+add 造成抖动
            if(c.getChildren().size == 1 && c.getChildren().first() instanceof Label
                && desc.contentEquals(((Label)c.getChildren().first()).getText())) return;
            c.clearChildren();
            c.add(desc);
            if(c.hasParent()) c.pack();
        }

        /**
         * 旧的单 int 编码（自动 -1 / 手动为索引，超控加 {@link #logicOverrideBase} 偏移）。
         * 新流程改用 {@link #configPayload()}；这里保留给旧存档/旧蓝图解码与逻辑指令语义对照。
         */
        protected int encodeConfig(){
            if(logicOverride) return logicOverrideBase + (auto ? 0 : selectedIndex + 1);
            return auto ? -1 : selectedIndex;
        }

        /** 解码 {@link #encodeConfig()} 的结果；旧值（-1 / 0..n-1）按“未超控”处理。 */
        protected void decodeConfig(int value){
            if(value >= logicOverrideBase){
                logicOverride = true;
                int sel = value - logicOverrideBase;
                if(sel == 0 || sel - 1 >= recipes.size){
                    // 自动模式（或索引越界时退回自动），超控状态保持
                    auto = true;
                    selectedIndex = -1;
                }else{
                    auto = false;
                    selectedIndex = sel - 1;
                }
            }else{
                logicOverride = false;
                if(value < 0){
                    auto = true;
                    selectedIndex = -1;
                }else if(value < recipes.size){
                    auto = false;
                    selectedIndex = value;
                }
            }
        }

        /**
         * 建成本体时若存在被替换的旧建筑（放置覆盖、或降层换回 1 层），迁移库存与配方状态。
         * 堆叠变体在自己的 created() 里还要迁移各层状态，所以这里只做只读搬运，不消耗迁移记录。
         */
        @Override
        public void created(){
            super.created();
            StackedMultiRecipeFactory.Snapshot snap = StackedMultiRecipeFactory.pendingSnapshot(tile.pos());
            if(snap == null) return;
            // 堆叠体在自己的 created() 里统一迁移（含逐层状态），这里再迁移一次会把库存重复累加。
            if(this instanceof StackedMultiRecipeFactory.StackedBuild) return;

            migrateFrom(snap);

            if(snap.layers.size > 0){
                StackedMultiRecipeFactory.LayerState src = snap.layers.first();
                recipeIndex = src.recipeIndex;
                auto = src.auto;
                selectedIndex = src.selectedIndex;
            }
            StackedMultiRecipeFactory.clearPending(tile.pos());
            // 换块后重新向热量/电力网络登记，保证热量输入和电网能自动连上。
            Core.app.post(this::refreshNetworks);
        }

        /** 找出去掉自己以外的第一个同链旧建筑（原版完工时通过了 overwrote 交回来）。 */
        protected Building previousFactory(Seq<Building> previous){
            if(previous == null) return null;
            for(Building b : previous){
                if(b != null && b != this && b instanceof MultiRecipeFactoryBuild) return b;
            }
            return null;
        }

        /** 把旧建筑的库存整体搬过来；换成更小容量后超出部分丢弃（与原版缩容一致）。 */
        protected void migrateFrom(Building old){
            if(old == null) return;
            if(items != null && old.items != null){
                items.set(old.items);
                clampItems();
            }
            if(liquids != null && old.liquids != null){
                old.liquids.each((liquid, amount) -> liquids.add(liquid, amount));
            }
        }

        /** 按纯数据快照迁移库存，避免持有旧建筑引用。 */
        protected void migrateFrom(StackedMultiRecipeFactory.Snapshot snap){
            if(snap == null) return;
            if(items != null){
                for(ItemStack stack : snap.items) items.add(stack.item, stack.amount);
                clampItems();
            }
            if(liquids != null){
                for(LiquidStack stack : snap.liquids) liquids.add(stack.liquid, stack.amount);
            }
        }

        /** 超出本建筑物品容量上限的部分丢弃（缩容时与原版行为一致）。 */
        protected void clampItems(){
            if(items == null) return;
            for(Item item : Vars.content.items()){
                int amount = items.get(item);
                if(amount > itemCapacity) items.remove(item, amount - itemCapacity);
            }
        }

        /**
         * 换块之后重新向两张网登记自己：
         * 热量靠 {@code calculateHeat} 沿 proximity 做 BFS，换了 Building 之后必须重建邻接；
         * 电力靠 PowerGraph，需要重新并到相邻电网并刷新整体需求。
         * 少了这一步，升/降层后的新方块会“看不见”周边的供热与供电建筑。
         */
        public void refreshNetworks(){
            updateProximity();
            syncPowerGraph(true);
        }

        /**
         * 重新向电网登记自己：缺失时加入，并刷新整体需求。
         *
         * @param rebind true = 同时重建本建筑的电网引用（换块后需要）；
         *               false = 只在已有电网上做一次 add/update。onProximityUpdate 里必须传 false：
         *               那里再调 updateProximity 会把自己重新触发一遍，形成递归。
         */
        protected void syncPowerGraph(boolean rebind){
            if(power == null || power.graph == null) return;
            if(!power.graph.all.contains(this)) power.graph.add(this);
            if(rebind) updatePowerGraph();
            if(power.graph != null) power.graph.update();
        }

        /**
         * 原版 constructFinish 会用 ConstructBlock.prevBuild 调用本方法，
         * 这是“放置覆盖 / 配置面板堆叠”时拿到真正旧建筑的唯一可靠时机
         * （beginPlace / beginBreak 都会填充 prevBuild）。
         * 直接调 Build.beginPlace 时拿不到旧建筑，所以迁移放在这里而不是 created()。
         */
        @Override
        public void overwrote(Seq<Building> previous){
            super.overwrote(previous);
            // 蓝图镜像兜底：ConstructBuild.lastConfig 保存着翻转后的完整配置，
            // 即使完工时 configured 没被原版调用，也能在这里把镜像位补回来。
            int previousMirror = mirrorMaskFromPrevious(previous);
            if(previousMirror != 0) liquidMirror = previousMirror;
            Building old = previousFactory(previous);
            if(old == null) return;
            migrateFrom(old);
            if(!(this instanceof StackedMultiRecipeFactory.StackedBuild)){
                StackedMultiRecipeFactory.LayerState src = StackedMultiRecipeFactory.stateOf(old);
                if(src != null){
                    recipeIndex = src.recipeIndex;
                    auto = src.auto;
                    selectedIndex = src.selectedIndex;
                }else if(old instanceof MultiRecipeFactoryBuild base){
                    recipeIndex = base.recipeIndex;
                    auto = base.auto;
                    selectedIndex = base.selectedIndex;
                }
            }
            if(tile != null) StackedMultiRecipeFactory.clearPending(tile.pos());
            // 升层完工后同样要重新登记热量/电力网络。
            Core.app.post(this::refreshNetworks);
        }

        /** 当前生效配方；索引越界时返回 null。 */
        public Recipe getRecipe() {
            if (recipeIndex < 0 || recipeIndex >= recipes.size) return null;
            return recipes.get(recipeIndex);
        }

        /**
         * 当前应按哪条配方报热量需求。
         * 手动模式下 selectedIndex 才是玩家选中的目标，recipeIndex 可能还是上一帧的配方；
         * 这里必须优先用 selectedIndex，否则切换配方的第一帧仍按旧配方报热量。
         */
        private Recipe heatRecipe() {
            if(!auto && selectedIndex >= 0 && selectedIndex < recipes.size){
                return recipes.get(selectedIndex);
            }
            return getRecipe();
        }

        /** 自动模式首次选择前，用第一条热配方向热网报出需求，避免启动时完全无需求。 */
        private Recipe heatRequirementRecipe() {
            Recipe current = heatRecipe();
            if (current != null) return current;
            if (auto) {
                for (Recipe candidate : recipes) {
                    if (candidate.heatEnabled && candidate.heatRequirement > 0) {
                        return candidate;
                    }
                }
            }
            return null;
        }

        /**
         * 维护欠压恢复锁：欠压时锁定 1 秒，电力恢复后也要等锁结束才重启。
         * 这样工厂停止后需求消失、电网状态瞬间恢复时，不会立刻重新启动造成振荡。
         */
        protected void updateUndervoltageLock(){
            Recipe current = getRecipe();
            boolean tripped = current != null
                    && current.powerUse > 0
                    && current.stopUndervoltage
                    && power != null
                    && power.status < 1f;

            if(tripped){
                undervoltageLock = UNDERVOLTAGE_LOCK_TIME;
            }else if(undervoltageLock > 0f){
                undervoltageLock = Math.max(0f, undervoltageLock - Time.delta);
            }
        }

        /** 用于界面展示的配方：尚未选中时回退到第一条配方。 */
        public Recipe getDisplayRecipe() {
            if (recipeIndex < 0 && recipes.size > 0) {
                return recipes.first();
            }
            return getRecipe();
        }

        /**
         * 新配置载荷：{@link MrfConfig} 的定长头 int[]，蓝图、网络与存档共用同一份结构。
         * 每次返回新数组，避免被 BuildPlan / ConstructBuild.lastConfig 持有后被后续状态改动污染。
         */
        public int[] configPayload(){
            return MrfConfig.encode(selectedIndex, auto, logicOverride, liquidMirror, null, null);
        }

        @Override
        public Object config() {
            return configPayload();
        }

        /**
         * 配置处理入口：蓝图粘贴（configureAny）与 UI 配置（configure）都会经 Call.tileConfig 触发本方法。
         * int[] 是新格式，走 {@link #applyPayload(int[])}；Integer/String 只在旧蓝图、旧逻辑指令上出现，
         * 统一收进 {@link #applyLegacyConfig(Unit, Object)}，正常流程不会进入字符串解析分支。
         */
        @Override
        public void configured(Unit unit, Object value) {
            if(value instanceof int[] payload){
                applyPayload(payload);
                return;
            }
            applyLegacyConfig(unit, value);
        }

        /** 新载荷解码：越界索引一律退回自动模式，不做异常驱动的解析。 */
        protected void applyPayload(int[] payload){
            if(!MrfConfig.valid(payload)) return;
            int recipe = MrfConfig.recipe(payload);
            boolean payloadAuto = MrfConfig.auto(payload);
            if(MrfConfig.stacked(payload)){
                // 堆叠载荷贴到单层方块：取第一层
                recipe = MrfConfig.layerRecipe(payload, 0);
                payloadAuto = MrfConfig.layerAuto(payload, 0);
            }
            logicOverride = MrfConfig.override(payload);
            liquidMirror = MrfConfig.mirror(payload);
            if(payloadAuto || recipe < 0 || recipe >= recipes.size){
                auto = true;
                selectedIndex = -1;
            }else{
                auto = false;
                selectedIndex = recipe;
            }
        }

        /**
         * 旧格式兼容的唯一入口：只在 value 不是 int[] 时调用。
         * - Integer：面板旧指令 / 旧蓝图，纯整数，保留当前镜像位；
         * - String：旧蓝图 {@code "值#f<mask>"}（堆叠体另有 {@code "a,b#1#f<mask>"}）。
         */
        protected void applyLegacyConfig(Unit unit, Object value){
            if(value instanceof Integer v){
                decodeConfig(v);
            }else if(value instanceof String text){
                liquidMirror = mirrorMaskOf(text);
                int hash = text.indexOf('#');
                String head = (hash >= 0 ? text.substring(0, hash) : text).trim();
                try{
                    decodeConfig(Integer.parseInt(head));
                }catch(NumberFormatException ignored){
                    // 降层时可能拿到堆叠体的逗号列表配置，配方状态由 Snapshot 迁移，这里只保留镜像位
                }
            }else{
                super.configured(unit, value);
            }
        }

        /** 存档版本：3 起改为 int[] 载荷；旧版本（1 配方状态 / 2 镜像字节）仍可读。 */
        @Override
        public byte version() {
            return 3;
        }

        /** 写入配方状态 + 超控 + 镜像位（同一份载荷；堆叠体覆写它追加层表）。 */
        @Override
        public void write(Writes write) {
            super.write(write);
            writePayload(write, configPayload());
        }

        /** 读取配方状态；revision >= 3 走载荷，更早的存档走旧的“int + 镜像字节”布局。 */
        @Override
        public void read(Reads read, byte revision) {
            if(revision >= 3){
                super.read(read, revision);
                applyPayload(readPayload(read));
            }else{
                readLegacyBase(read, revision);
                if(revision >= 2){
                    liquidMirror = read.b() & 3;
                }
            }
        }

        /** 载荷落盘：长度 + 逐元素 int。读回时按长度分配，未来版本追加的尾字段不会错位。 */
        protected void writePayload(Writes write, int[] payload){
            write.i(payload.length);
            for(int i = 0; i < payload.length; i++){
                write.i(payload[i]);
            }
        }

        /** 读回载荷；长度不在合法区间时返回空数组，由 {@link #applyPayload(int[])} 直接忽略。 */
        protected int[] readPayload(Reads read){
            int length = read.i();
            if(length < MrfConfig.baseLength || length > MrfConfig.maxLength){
                return new int[0];
            }
            int[] payload = new int[length];
            for(int i = 0; i < length; i++){
                payload[i] = read.i();
            }
            return payload;
        }

        /**
         * 旧存档基座字段：原版建筑字段（{@code super.read}）+ 版本 1 起的一个配方状态 int。
         * 不含版本 2 的镜像字节，堆叠体复用它按自己的版本号对齐。
         */
        protected void readLegacyBase(Reads read, byte revision) {
            super.read(read, revision);
            // 兼容旧存档：仅当版本 >= 1 时才存在额外 int，否则保持默认（自动模式）
            if (revision >= 1) {
                decodeConfig(read.i());
            }
        }

        /** 四面热量输入数组（HeatConsumer）。 */
        @Override
        public float[] sideHeat() {
            return sideHeat;
        }

        /**
         * 热量读取兜底：原版 calculateHeat() 会对“可旋转的热量传输器”做方向过滤，
         * 某些朝向的面会被直接跳过。这里只对原版没有接收到的相邻 HeatBlock 补一次，
         * 已接收的面不重复计数，避免修方向时把热量算两遍。
         * 只处理正交四邻（用 tile 坐标判定单轴相邻），排除斜对角（proximity 是 3×3，
         * relativeTo 会把斜对角折叠到某个正交面，导致“没有面对本工厂”的热源也被计入）；
         * 同一多格热源在 proximity 中占多个槽，按引用去重只计一次。
         */
        @Override
        public float calculateHeat(float[] sideHeat) {
            float result = super.calculateHeat(sideHeat);
            int seen = 0;
            for(var neighbor : proximity){
                if(neighbor == null || neighbor.team != team || !(neighbor instanceof HeatBlock heatBlock)){
                    continue;
                }

                // 去重：同一多格热源在 proximity 中会重复出现
                boolean duplicate = false;
                for(int i = 0; i < seen; i++){
                    if(heatSeen[i] == neighbor){
                        duplicate = true;
                        break;
                    }
                }
                if(duplicate) continue;
                if(seen < heatSeen.length) heatSeen[seen++] = neighbor;

                // 仅正交相邻：|dx|+|dy| 为单轴；斜对角不接收
                int dx = neighbor.tile.x - tile.x;
                int dy = neighbor.tile.y - tile.y;
                if(dx != 0 && dy != 0){
                    continue;
                }

                int side = Mathf.mod(relativeTo(neighbor), 4);
                if(sideHeat[side] > 0f) continue;

                float amount = heatBlock.heat() / Math.max(1, neighbor.block.size);
                if(amount <= 0f) continue;

                sideHeat[side] += amount;
                result += amount;
            }
            return result;
        }

        /** 当前配方的热量需求；未启用热量时返回 0。 */
        @Override
        public float heatRequirement() {
            Recipe recipe = heatRequirementRecipe();
            if (recipe != null && recipe.heatEnabled && recipe.heatRequirement > 0) {
                return recipe.heatRequirement;
            }
            return 0f;
        }

        /** 当前配方的热量产出（HeatBlock）。 */
        @Override
        public float heat() {
            Recipe recipe = getRecipe();
            if (recipe != null && recipe.heatEnabled && recipe.heatOutput > 0) {
                return warmup * recipe.heatOutput;
            }
            return 0f;
        }

        /** 热量产出进度（0~1），供状态条显示。 */
        @Override
        public float heatFrac() {
            Recipe recipe = getRecipe();
            if (recipe != null && recipe.heatEnabled && recipe.heatOutput > 0) {
                return warmup;
            }
            return 0f;
        }

        /**
         * 热量效率曲线：base + over，最终按 recipeMaxEfficiency 截断。
         * 该结果只在 updateEfficiencyMultiplier() 中应用一次，并供热量条显示。
         */
        public float heatEfficiencyScale() {
            return heatEfficiencyScale(heatRecipe());
        }

        /** 指定配方的热量效率曲线；堆叠体逐层调用，避免读取最后一层配方。 */
        public float heatEfficiencyScale(Recipe recipe) {
            if (recipe == null || !recipe.heatEnabled || recipe.heatRequirement <= 0) {
                return 1f;
            }

            float x = rawHeat / recipe.heatRequirement;
            if(!Float.isFinite(x)) return 0f;

            float power = Float.isFinite(recipe.efficiencyCurvePower)
                    ? Math.max(recipe.efficiencyCurvePower, 0.05f)
                    : 1f;

            float base = Mathf.pow(Mathf.clamp(x), power);
            float over = Math.max(x - 1f, 0f);
            float result = base + over;

            if(!Float.isFinite(result)) return 0f;
            return Math.min(result, recipe.recipeMaxEfficiency);
        }

        /** 绕开原版 efficiencyScale()，在最终效率出口统一应用一次热量曲线。 */
        @Override
        public void updateEfficiencyMultiplier() {
            float scale = heatEfficiencyScale();
            if(!Float.isFinite(scale)) scale = 1f;
            efficiency *= scale;
            optionalEfficiency *= scale;
        }

        /** 返回绘制器使用的预热目标值。 */
        @Override
        public float warmupTarget() {
            Recipe recipe = getRecipe();
            if (recipe == null) return 0f;
            if (recipe.heatEnabled && recipe.heatRequirement > 0) {
                return Mathf.clamp(rawHeat / recipe.heatRequirement);
            }
            return efficiency;
        }

        /**
         * 让原版 {@code GenericCrafterBuild.warmup} 向 0 衰减一次。
         * 本类声明了同名字段 {@code warmup} 遮蔽了父类字段，而 DrawFlame 读取的是父类字段，
         * 因此这里必须通过 super 访问，不能写 {@code this.warmup}。
         */
        protected void fadeVanillaWarmup(){
            float speed = block instanceof GenericCrafter crafter ? crafter.warmupSpeed : 0.019f;
            super.warmup = Mathf.approachDelta(super.warmup, 0f, speed);
        }

        /** 读取原版 GenericCrafterBuild.warmup；StackedBuild 的 super 只到本类，不能直接越过本类字段。 */
        protected float vanillaWarmup(){
            return super.warmup;
        }

        /** 写回原版 GenericCrafterBuild.warmup，供堆叠体隔离空闲层对共享预热值的影响。 */
        protected void setVanillaWarmup(float value){
            super.warmup = value;
        }

        /**
         * 单条配方的输入满足度（0~1）：物品/载荷按整周期用量，液体按本帧需求量折算。
         * 用 delta() 而不是 edelta()，避免读取上一帧的全局 efficiency 形成反馈。
         */
        protected float inputEfficiency(Recipe recipe){
            if(recipe == null) return 0f;

            for(int i = 0; i < recipe.inputItem.size; i++){
                ItemStack stack = recipe.inputItem.get(i);
                if(items == null || items.get(stack.item) < stack.amount) return 0f;
            }
            for(int i = 0; i < recipe.inputPayload.size; i++){
                PayloadStack stack = recipe.inputPayload.get(i);
                if(getPayloads() == null || getPayloads().get(stack.item) < stack.amount) return 0f;
            }

            float delta = delta();
            if(delta <= 0.00000001f){
                return recipe.inputLiquid.isEmpty() ? 1f : 0f;
            }
            if(recipe.inputLiquid.isEmpty()) return 1f;
            if(liquids == null) return 0f;

            float eff = 1f;
            for(int i = 0; i < recipe.inputLiquid.size; i++){
                LiquidStack stack = recipe.inputLiquid.get(i);
                float req = stack.amount * delta;
                if(req <= 0.00000001f) return 0f;
                eff = Math.min(eff, liquids.get(stack.liquid) / req);
            }
            return eff;
        }

        /**
         * 单层实际生产效率：输入满足度 × 供电比例 × 该配方的热量曲线。
         * 堆叠体每层用自己的配方调用，避免共享字段 efficiency 被最后一层决定。
         */
        protected float layerEfficiency(Recipe recipe){
            if(recipe == null) return 0f;

            float eff = inputEfficiency(recipe);
            if(eff <= 0f) return 0f;

            if(recipe.powerUse > 0 && power != null){
                if(recipe.stopUndervoltage && power.status < 1f) return 0f;
                eff = Math.min(eff, power.status);
            }

            eff *= heatEfficiencyScale(recipe);
            return Math.max(eff, 0f);
        }

        /** 堆叠体覆写为 true：液体输入由 StackedBuild 逐层扣除，ConsumeRecipe.update 不再按单一配方扣。 */
        protected boolean handlesOwnLiquidInput(){
            return false;
        }

        /** 堆叠体覆写为所有具备运行条件的层的最大输入效率；-1 表示非堆叠，走原消费器逻辑。 */
        protected float stackedInputEfficiency(){
            return -1f;
        }

        /**
         * 严格配方检索：从后往前寻找输入（物品/液体/载荷）、热量、电力均满足且输出未满的配方。
         * 热量/电力只要低于需求即排除；找不到时 recipeIndex 置 -1。
         */
        public void updateRecipe() {
            // 首次选择还没有当前配方，不能先用热量过滤，否则热网需求为 0，永远选不中热配方。
            for (int i = recipes.size - 1; i >= 0; i--) {
                Recipe candidate = recipes.get(i);
                if (isLocked(candidate)) continue; // 跳过未解锁（需研究未研究）配方

                // 输入（物品 / 液体 / 载荷）是否够：统一走 Recipe.inputsAvailable
                boolean valid = candidate.inputsAvailable(this);

                // 检查热量输入：统一用真实热量 rawHeat 与配方布尔值判定，避免“有/无当前配方”
                // 两套标准导致热配方在边界反复“选中—排除”。stopLowTemperature=false 时允许低热选中（低速工作）。
                if (valid && candidate.heatEnabled && candidate.heatRequirement > 0
                        && candidate.stopLowTemperature && rawHeat < candidate.heatRequirement) {
                    valid = false;
                }

                // 检查电力输入：完全没电时排除；设置“欠压即停”的配方还必须在
                // power.status >= 1 时才允许被严格检索选中。
                if(valid && candidate.powerUse > 0){
                    if(power.status <= 0 || powerConsumer.isUndervoltage(this, candidate)){
                        valid = false;
                    }
                }

                // 检查输出容量
                if (valid && !canOutputForRecipe(candidate)) {
                    continue; // 如果输出满，则跳过此配方，继续寻找其他配方
                }

                if (valid) {
                    recipeIndex = i;
                    return;
                }
            }
            recipeIndex = -1;
        }

        /**
         * 宽松配方检索：仅当严格检索无结果时使用。
         * 热量/电力不足的配方，仅在其“即停”开关为 true 时排除；为 false 时仍可选（以低效率继续工作）。
         */
        public void trySelectRelaxed() {
            for (int i = recipes.size - 1; i >= 0; i--) {
                Recipe recipe = recipes.get(i);
                if (isLocked(recipe)) continue; // 跳过未解锁（需研究未研究）配方

                // 输入（物品 / 液体 / 载荷）是否够：与严格检索共用同一判定
                boolean valid = recipe.inputsAvailable(this);

                // 热量不足：配方“低温即停”(stopLowTemperature=true) 且真实热量不足时排除；
                // =false 时允许低速工作，统一用 rawHeat 判定，与严格检索/validRecipe 保持一致。
                if (valid && recipe.heatEnabled && recipe.heatRequirement > 0 && recipe.stopLowTemperature && rawHeat < recipe.heatRequirement) {
                    valid = false;
                }

                // 电力不足：仅当候选配方设定“欠压即停”时才排除。
                if (valid && powerConsumer.isUndervoltage(this, recipe)) {
                    valid = false;
                }

                // 检查输出容量
                if (valid && !canOutputForRecipe(recipe)) {
                    continue;
                }

                if (valid) {
                    recipeIndex = i;
                    return;
                }
            }
            // 无可用配方时不改动 recipeIndex（保持 -1）
        }

        /**
         * 自动模式重选：严格检索 → 无结果再按布尔放宽 → 仍无结果时保留原配方。
         *
         * <p>保留原配方是有意的：缺输入、低温、欠压、输出满都是**可恢复状态**，
         * 若把 recipeIndex 清成 -1，面板和状态会丢掉“当前在等什么配方”的上下文，
         * 而下一帧又会在 progress<=0 的边界反复检索。
         *
         * @return 是否换到了不同的配方（调用方据此决定是否重置 progress）
         */
        protected boolean reselectRecipe(){
            int previous = recipeIndex;

            updateRecipe();
            if(getRecipe() == null){
                trySelectRelaxed();
                // 两条检索都没结果：回到原配方，等它的条件自己恢复
                if(getRecipe() == null && previous >= 0 && previous < recipes.size
                        && !isLocked(recipes.get(previous))){
                    recipeIndex = previous;
                }
            }

            return recipeIndex != previous;
        }

        /** 检查该配方的物品/液体输出是否还有足够容量。 */
        public boolean canOutputForRecipe(Recipe recipe) {
            return MultiRecipeOutput.canOutput(this, recipe, checkItemOutputFullness, checkLiquidOutputFullness);
        }

        /**
         * 该配方在当前库存下是否已“输出满”（按下一周期是否会溢出判定）。
         * 供配置面板/状态图标标注“这条配方现在产不出来”使用。
         */
        public boolean isOutputFull(Recipe recipe) {
            return recipe != null && !canOutputForRecipe(recipe);
        }

        /**
         * 全部配方的输出满状态，索引与 {@code recipes} 对齐。
         * 供 UI 在切换配方/刷新面板时一次性取快照，避免逐条重复计算。
         */
        public boolean[] outputFullStates() {
            boolean[] states = new boolean[recipes.size];
            for (int i = 0; i < recipes.size; i++) {
                states[i] = !canOutputForRecipe(recipes.get(i));
            }
            return states;
        }

        /** 当前配方是否仍可用：输入充足、未锁定，且未触发低温/欠压跳闸。 */
        public boolean validRecipe() {
            if (recipeIndex < 0) return false;
            if (undervoltageLock > 0f) return false;

            Recipe current = recipes.get(recipeIndex);
            if (isLocked(current)) return false; // 未解锁配方视为无效

            // 输入（物品 / 液体 / 载荷）统一判定
            if (!current.inputsAvailable(this)) return false;

            // 热量门槛用真实热量判定：stopLowTemperature=false 的配方允许低热低速生产，
            // 不受这里影响；用 rawHeat 而不是 heat，避免读到上一帧抬升后的值。
            if (current.heatEnabled && current.heatRequirement > 0 && current.stopLowTemperature
                    && rawHeat < current.heatRequirement) {
                return false;
            }

            if (powerConsumer.isUndervoltage(this)) {
                return false;
            }

            // 输出仓满：当前配方无法继续产出
            return canOutputForRecipe(current);
        }

        /**
         * 每帧逻辑：先确定配方（自动模式下严格检索，失败后宽松检索；手动模式锁定 selectedIndex），
         * 再更新热量并交给父类推进生产，最后补充液体输出与物品容量钳制。
         */
        @Override
        public void updateTile() {
            updateUndervoltageLock();

            // 真实热量只依赖周围供热方块，先算出来：配方检索（validRecipe）与 heat 抬升都以本帧值为准。
            // 不能沿用上一帧被抬升过的 heat，否则“当前配方已失效”会被误判成仍可用，自动重选不触发。
            rawHeat = calculateHeat(sideHeat);

            if (auto) {
                // === 自动匹配模式 ===
                // 只在“周期边界(progress<=0)”重选是不够的：当前配方一旦失效（缺输入/低温跳闸/欠压/输出满），
                // 引擎会在 Building.updateConsumption() 里把 efficiency 置 0，progress 就永远停在半截，
                // 边界条件再也满足不了 —— 自动模式会锁死在这条配方上（如石油化工的热/无热配方切换、
                // 碱电解池水配方满仓后切纯碱）。因此“当前配方不可用”本身也是重选时机。
                // 当前配方仍可用时不检索，避免每帧换配方造成的进度断档。
                if (undervoltageLock <= 0f && !validRecipe()) {
                    // 换配方才重置进度：不同配方 craftTime 不同，沿用别的配方的半截进度会串味；
                    // 没换（包括“没有任何可产配方、保留原配方等待恢复”）时保持进度。
                    if (reselectRecipe()) progress = 0f;
                }
            } else {
                // === 手动选定模式：锁定 selectedIndex 配方制作 ===
                // 输入不足时由 ConsumeRecipe.efficiency() 使效率归零，super.updateTile() 不会推进生产
                if (selectedIndex >= 0 && selectedIndex < recipes.size && !isLocked(recipes.get(selectedIndex))) {
                    // 玩家切换了选定配方：重置生产进度，避免沿用上一配方的半截进度
                    if (recipeIndex != selectedIndex) {
                        progress = 0f;
                    }
                    recipeIndex = selectedIndex;
                } else {
                    recipeIndex = -1;
                }
            }

            Recipe current = getRecipe();

            // 有效热量：配方设定“低温不停机”(stopLowTemperature=false)时，
            // 将 heat 抬升到需求值，使原可用性/消耗检查(updateRecipe、shouldConsume)视为热量已满足；
            // 真实效率仍按 rawHeat 计算（见 efficiencyScale），因此低热时是低速工作而非满速。
            if (current != null && current.heatEnabled && current.heatRequirement > 0 && !current.stopLowTemperature) {
                heat = Math.max(rawHeat, current.heatRequirement);
            } else {
                heat = rawHeat;
            }

            // 调用父类更新逻辑
            super.updateTile();

            // craft() 可能在本帧内完成一个周期并切换了配方，这里必须重新取：
            // 否则会用旧配方的产物表写一帧（液体写错配方、物品钳制错条目）。
            current = getRecipe();
            if (current == null) return;

            // 先钳制：无论下面哪个分支提前返回，库存都不会留下超过 itemCapacity 的数值
            MultiRecipeOutput.clampItems(this, current);

            if (!canOutputForRecipe(current)) {
                return; // 输出已被填满（下一周期放不下），停止生产
            }

            // 处理液体输出
            if (efficiency > 0 && !current.outputLiquid.isEmpty()) {
                float inc = getProgressIncrease(craftTime / current.craftTime);
                if (!Float.isFinite(inc) || inc <= 0f) return;
                MultiRecipeOutput.outputLiquids(this, current, inc);
            }
        }

        /** 输出物品、载荷与液体；物品/载荷按统一计时器投放。 */
        @Override
        public void dumpOutputs() {
            boolean timer = timer(timerDump, dumpTime / timeScale);
            if (timer) {
                itemOutput.each(this::dump);
                payloadOutput.each(output -> {
                    BuildPayload payload = new BuildPayload((Block) output, team);
                    payload.set(x, y, rotdeg());
                    dumpPayload(payload);
                });
            }
            Recipe current = getRecipe();
            if (current != null) {
                int index = 0;
                for (LiquidStack output : current.outputLiquid) {
                    int direction = liquidOutputDirections != null && index < liquidOutputDirections.length
                            ? liquidOutputDirections[index]
                            : -1;
                    // 镜像位只翻转方向面，随后 dumpLiquid 再叠加 rotation 的 90° 朝向
                    direction = mirrorDirection(direction, liquidMirror);
                    dumpLiquid(output.liquid, 2f, direction);
                    index++;
                }
            } else {
                liquidOutput.each(output -> dumpLiquid(output, 2f, -1));
            }
        }

        /** 是否允许消耗输入：无配方、断电、低温跳闸或液体输出已满时停止。 */
        @Override
        public boolean shouldConsume() {
            if (getRecipe() == null) return false;
            if (undervoltageLock > 0f) return false;

            // 输出仓满（物品/液体/载荷任一达到容量上限）时停止生产，避免无视建筑库存上限继续产出
            if (!canOutputForRecipe(getRecipe())) return false;

            // 检查电力供应：彻底没电停止；或配方设“欠压即停”且供电不足(status<1)时停止
            Recipe currentRecipe = getRecipe();
            if (currentRecipe != null) {
                if (power.status <= 0 || powerConsumer.isUndervoltage(this)) {
                    return false;
                }
            }

            // 检查热量供应（配方“低温即停”且真实热量不足时停止；
            // stopLowTemperature=false 的配方允许低热低速工作，不在这里拦）
            if (currentRecipe != null && currentRecipe.heatEnabled && currentRecipe.heatRequirement > 0
                    && currentRecipe.stopLowTemperature && rawHeat < currentRecipe.heatRequirement) {
                return false;
            }

            if (!ignoreLiquidFullness) {
                if (getRecipe().outputLiquid.isEmpty()) return true;
                boolean allFull = true;
                for (var output : getRecipe().outputLiquid) {
                    if (!MultiRecipeOutput.liquidFull(this, output.liquid)) {
                        allFull = false;
                        break;
                    }
                }
                if (allFull) {
                    return false;
                }
            }
            return enabled;
        }

        /** 按当前配方的 craftTime 缩放生产进度。 */
        @Override
        public float getProgressIncrease(float baseTime) {
            float scl = 1f;
            if (getRecipe() != null) scl = getRecipe().craftTime / craftTime;
            if (!Float.isFinite(scl) || scl <= 0f) scl = 1f;
            if (!Float.isFinite(baseTime) || baseTime <= 0f) return 0f;

            // 不能调用 GenericCrafterBuild.getProgressIncrease：它会在任意一种
            // outputLiquids 满时把进度直接压成 0，导致一种产物满后整厂停摆。
            // 输出是否允许继续由 canOutputForRecipe（全满才停）与逐液体写入控制。
            float result = edelta() / baseTime / scl;
            return Float.isFinite(result) ? result : 0f;
        }

        /** 完成一个生产周期：消耗输入、输出物品，并在自动模式下重新匹配配方。 */
        @Override
        public void craft() {
            if (getRecipe() == null) return;
            // 输出已满（下一周期放不下）时不结算：否则 offload 会把物品累加到 itemCapacity 之上
            if (!canOutputForRecipe(getRecipe())) return;

            consume(); // 消耗输入

            // 输出物品
            getRecipe().outputItem.each(stack -> {
                for (int i = 0; i < stack.amount; i++) {
                    offload(stack.item);
                }
            });

            progress %= 1f;

            if (wasVisible) craftEffect.at(x, y);
            if (auto) {
                // 自动模式：周期结束重选；找不到别的可产配方时保留原配方，等条件恢复
                reselectRecipe();
            }
            // 手动模式：保持锁定 selectedIndex，不重新选择配方
        }

        /**
         * 配置面板里的“堆叠 / 降层”按钮。点击后走原版放置流程（{@link Build#beginPlace}），
         * 因此生成的是 ConstructBuild 建造蓝图：按变体 requirements 扣资源、按 buildTime 计时，
         * 而不是直接替换方块。
         *
         * <p>按钮会登记到 logicButtons / baseDisabled，由面板的 refresh 统一套用
         * “逻辑控制时置灰”的规则。
         */
        protected void addStackButtons(Table table, Seq<Button> logicButtons, Seq<Boolp> baseDisabled){
            int layers = StackedMultiRecipeFactory.layersOf(block);
            int limit = StackedMultiRecipeFactory.stackLimitFor(block);
            if(layers <= 0 || limit <= 0) return;

            boolean canUp = StackedMultiRecipeFactory.canStackUp(block);
            boolean canDown = StackedMultiRecipeFactory.canStackDown(block);

            TextButton upBtn = new TextButton(
                Core.bundle.format("tri.stack.upgrade", layers, limit), Styles.defaultt);
            upBtn.setDisabled(!canUp);
            upBtn.clicked(() -> {
                if(!canUp || logicControlled() || !isValid()) return;
                // 先收起面板：本建筑随后会被建造蓝图替换
                if(Vars.control != null && Vars.control.input != null){
                    Vars.control.input.config.hideConfig();
                }
                Core.app.post(() -> StackedMultiRecipeFactory.stackUp(this));
            });

            TextButton downBtn = new TextButton(
                Core.bundle.format("tri.stack.downgrade", layers, limit), Styles.defaultt);
            downBtn.setDisabled(!canDown);
            downBtn.clicked(() -> {
                if(!canDown || logicControlled() || !isValid()) return;
                if(Vars.control != null && Vars.control.input != null){
                    Vars.control.input.config.hideConfig();
                }
                Core.app.post(() -> StackedMultiRecipeFactory.stackDown(this));
            });

            logicButtons.add(upBtn);
            baseDisabled.add(() -> !canUp);
            logicButtons.add(downBtn);
            baseDisabled.add(() -> !canDown);

            table.table(row -> {
                row.add(upBtn).size(116f, 46f).padRight(8f);
                row.add(downBtn).size(116f, 46f);
            }).padBottom(8f).row();
        }

        /**
         * 配置界面：顶部 AUTO 开关，下方一行一个配方（输入图标 → 输出图标 + 选中指示灯）。
         * 点击已选中的配方会取消选择并恢复自动模式；打开面板时刷新一次，之后仅在状态变化时刷新。
         */
        @Override
        public void buildConfiguration(Table table) {
            // AUTO 按钮样式：自动模式(激活)=button-over，手动模式(未激活)=button-trans
            TextButton.TextButtonStyle autoActive = new TextButton.TextButtonStyle(Styles.defaultt);
            autoActive.up = Tex.buttonOver;
            autoActive.over = Tex.buttonOver;
            autoActive.down = Tex.buttonOver;
            autoActive.fontColor = Color.white; // button-over 为蓝底，白字才可读（标记失效时兜底）

            TextButton.TextButtonStyle autoInactive = new TextButton.TextButtonStyle(Styles.defaultt);
            autoInactive.up = Tex.buttonTrans;
            autoInactive.over = Tex.buttonTrans;
            autoInactive.down = Tex.buttonTrans;
            autoInactive.fontColor = Color.white; // 半透明底，白字可读（标记失效时兜底）

            Seq<Image> checks = new Seq<>();
            // 所有受“逻辑控制”影响的按钮：逻辑接管配方选择时统一置灰，面板只做状态显示
            Seq<Button> logicButtons = new Seq<>();
            Seq<Boolp> baseDisabled = new Seq<>();
            TextButton autoBtn = new TextButton("AUTO", autoActive);
            logicButtons.add(autoBtn);
            baseDisabled.add(() -> false);

            // 逻辑控制提示：一个可点击的紫色按钮，背景沿用“置灰按钮”的 Tex.buttonDisabled，
            // 外观与被禁用的按钮一致；点击在“被逻辑处理器控制 / 超控状态”之间切换。
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
            }) {
                {
                    // 已打开时只同步文案与位置，绝不再次 show（避免重复打开或被重新排布导致收起）
                    tipSync[0] = () -> {
                        refillHintTooltip(this);
                        setContainerPosition(logicHint, 0f, 0f);
                    };
                    // 面板关闭、按钮离开场景后浮框若还挂着就自行收起，避免残留在屏幕上
                    container.update(() -> {
                        if (!logicHint.hasParent() && container.hasParent()) hide();
                    });
                }

                @Override
                public void show(Element target, float x, float y) {
                    // 每次显示前重填文案，保证与当前“受控 / 超控”状态一致
                    refillHintTooltip(this);
                    super.show(target, x, y);
                }

                @Override
                protected void setContainerPosition(Element target, float x, float y) {
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
            // 刷新按钮状态前，先按“即将显示的文案”实测宽度并适配单元格，避免用旧宽度排版导致换行
            Runnable applyHintText = () -> {
                String text = logicOverride ? Core.bundle.get("tri.stack.logicOverrideOn") : Core.bundle.get("tri.stack.logicControlled");
                if (text.equals(lastHintText[0]) && !hintSizeDirty[0]) return;
                lastHintText[0] = text;
                logicHint.setStyle(logicOverride ? hintStyleOverride : hintStyle);
                logicHint.setText(text);
                if (hintCell[0] != null) {
                    // 宽度 = 当前文案的实测宽度（含贴图内边距），单元格不会再按旧宽度挤成两行
                    hintCell[0].width(logicHint.getPrefWidth());
                }
                hintSizeDirty[0] = false;
            };

            // 状态刷新：AUTO 激活贴图/文本、配方指示灯、禁用灰显、逻辑提示
            Runnable refresh = () -> {
                if (auto) {
                    autoBtn.setStyle(autoActive);
                    autoBtn.setText("[white]" + Core.bundle.get("autoRecipe") + "[lightgray]" + Core.bundle.get("onRe"));
                } else {
                    autoBtn.setStyle(autoInactive);
                    autoBtn.setText("[white]" + Core.bundle.get("autoRecipe") + "[lightgray]" + Core.bundle.get("offRe"));
                }
                // 受逻辑控制、或玩家已启用超控时都要显示提示按钮（超控时按钮仍显示）
                boolean logic = logicControlled();
                boolean override = logicOverride;
                // 只在“显示/隐藏”状态真正翻转时重建该行：整格 clear + 重新 add，
                // 避免反复 remove 留下的空单元格把按钮越挤越偏（切换若干次后向右漂移）
                boolean show = logic || override;
                if (show != hintShown[0]) {
                    hintShown[0] = show;
                    hintBox.clearChildren();
                    hintCell[0] = null;
                    if (show) {
                        hintCell[0] = hintBox.add(logicHint).center().padBottom(6f);
                        hintSizeDirty[0] = true;
                    } else {
                        // 逻辑结束、按钮撤下时同时收起悬停描述，避免浮框残留
                        hintHover[0] = false;
                        hintTooltip.hide();
                    }
                }
                // 统一同步按钮文字/样式（内部按实测宽度适配单元格）
                applyHintText.run();
                for (int i = 0; i < logicButtons.size; i++) {
                    logicButtons.get(i).setDisabled(logic || baseDisabled.get(i).get());
                }
                for (int i = 0; i < checks.size; i++) {
                    boolean sel = !auto && selectedIndex == i;
                    checks.get(i).setDrawable(sel ? Tex.checkOnOver : Tex.checkOff);
                }
            };

            // 悬停状态自己维护：不挂 Tooltip 监听器（它的 touchDown 会先把浮框摘掉再重开，表现为“收回”）
            logicHint.addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                    if (pointer == -1) hintHover[0] = true;
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                    if (pointer == -1) hintHover[0] = false;
                }
            });
            // 浮框开关的唯一入口：只要鼠标在按钮上就尝试打开；已经打开时只同步文案/位置，不重复打开
            logicHint.update(() -> {
                if (!logicHint.hasParent()) {
                    if (hintTooltip.container.hasParent()) hintTooltip.hide();
                    return;
                }
                if (hintHover[0]) {
                    if (!hintTooltip.container.hasParent()) {
                        hintTooltip.show(logicHint, 0f, 0f);
                    } else if (tipSync[0] != null) {
                        tipSync[0].run();
                    }
                } else if (hintTooltip.container.hasParent()) {
                    hintTooltip.hide();
                }
            });
            // 点击切换超控状态（切换后通过 configure 同步并进入蓝图/存档）
            logicHint.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!logicHint.hasParent()) return;
                    logicOverride = !logicOverride;
                    // 编码里带上超控位：这一份编码同时用于 UI 配置、蓝图复制与存档
                    configure(configPayload());
                    applyHintText.run();
                    refresh.run();
                    // 若 tooltip 正在显示，就地换成切换后的描述（不重新显示、不影响布局）
                    refillHintTooltip(hintTooltip);
                }
            });

            autoBtn.clicked(() -> {
                if (logicControlled()) return;
                auto = true;
                selectedIndex = -1;
                // 用带超控位的编码下发布局：直接 configure(-1) 会把超控状态清掉
                configure(configPayload());
                refresh.run(); // 点击后立即反映黄底/指示灯
            });
            table.add(autoBtn).size(240f, 46f).padBottom(8f).row();

            // 堆叠 / 降层按钮（只对可堆叠工厂出现）
            addStackButtons(table, logicButtons, baseDisabled);

            // 逻辑控制提示：位于堆叠按钮组与配方列表之间，水平居中；不受控时整行高度为 0
            table.add(hintBox).growX().row();
            // 有逻辑控制指令到达时用它立刻重刷面板
            panelRefresh = refresh;

            // 配方选择区：一行一个配方，仅显示资源贴图，行尾带选中指示灯
            table.table(cont -> {
                for (int i = 0; i < recipes.size; i++) {
                    Recipe recipe = recipes.get(i);
                    int finalI = i;
                    Button row = new Button(Styles.defaultb);
                    row.left();
                    row.add("[accent][" + (finalI + 1) + "]:[]").width(48f);
                    row.table(inner -> {
                        inner.table(r -> {
                            r.left();
                            recipe.inputItem.each(stack -> r.add(MultiRecipeFactory.displayIcon(stack.item)));
                            recipe.inputLiquid.each(stack -> r.add(MultiRecipeFactory.displayIcon(stack.liquid)));
                            recipe.inputPayload.each(stack -> r.add(MultiRecipeFactory.displayIcon(stack.item)));
                            // 电力消耗显示（配方存在电力消耗时）
                            if (recipe.powerUse > 0) {
                                r.table(pow -> {
                                    pow.image(Icon.power);
                                    pow.add("[stat]" + Strings.autoFixed(recipe.powerUse * 60f, 2));
                                });
                            }
                            if (recipe.heatEnabled && recipe.heatRequirement > 0) {
                                r.table(heat ->{
                                    heat.image(Icon.waves).padLeft(15f).padRight(5f).color(Color.valueOf("FF6666"));
                                    heat.add(Strings.autoFixed(recipe.heatRequirement, 2));
                                });
                            }
                        }).growX();
                        inner.table(r -> {
                            r.left();
                            r.image(Icon.right).size(32f).padLeft(8f).padRight(12f);
                            recipe.outputItem.each(stack -> r.add(MultiRecipeFactory.displayIcon(stack.item)));
                            recipe.outputLiquid.each(stack -> r.add(MultiRecipeFactory.displayIcon(stack.liquid)));
                            recipe.outputPayload.each(stack -> r.add(MultiRecipeFactory.displayIcon(stack.item)));
                        }).growX();
                    }).growX();
                    row.clicked(() -> {
                        if (logicControlled() || isLocked(recipe)) return;
                        if (!auto && selectedIndex == finalI) {
                            // 再次点击已选中的配方：取消选择，恢复自动
                            auto = true;
                            selectedIndex = -1;
                        } else {
                            // 选中该配方：切到手动（旧选中自动被本按钮替代）
                            auto = false;
                            selectedIndex = finalI;
                        }
                        // 用带超控位的编码下发：直接 configure(index) 会把超控状态清掉
                        configure(configPayload());
                        refresh.run(); // 点击后立即同步指示灯与黄底
                    });
                    // 未解锁的配方保持置灰；逻辑控制时由 refresh 统一再置灰
                    logicButtons.add(row);
                    baseDisabled.add(() -> isLocked(recipes.get(finalI)));
                    // 选中指示灯（check-off=未选中，check-on-over=选中）
                    Image check = new Image(Tex.checkOff);
                    checks.add(check);
                    cont.table(c -> {
                        c.add(row).growX();
                        c.add(check).size(32f).padLeft(8f);
                    }).fillX().row();
                }
            });

            // 打开时立即刷新一次，避免短暂显示默认/错误状态
            refresh.run();
            // 状态快照：仅当 auto/选中配方变化时（本地点击或联机远程配置）才刷新 UI，
            // 避免配置面板打开期间每帧遍历所有配方行执行 setStyle/setText/setDisabled
            //（研究解锁不会在面板打开期间发生，锁定状态无需逐帧检查）
            boolean[] lastAuto = {auto};
            int[] lastSelected = {selectedIndex};
            boolean[] lastLogic = {logicControlled()};
            boolean[] lastOverride = {logicOverride};
            table.update(() -> {
                boolean logic = logicControlled();
                if(auto != lastAuto[0] || selectedIndex != lastSelected[0] || logic != lastLogic[0] || logicOverride != lastOverride[0]){
                    lastAuto[0] = auto;
                    lastSelected[0] = selectedIndex;
                    lastLogic[0] = logic;
                    lastOverride[0] = logicOverride;
                    refresh.run();
                }
            });
        }

        /**
         * 当前“可生产”的配方（输入、热量、电力门槛均满足）是否全部输出仓已满。
         * 用于自动模式的状态提示：此时不是缺输入，而是产得出来但没地方放。
         */
        private boolean allProducibleOutputsFull() {
            boolean anyProducible = false;
            for (int i = 0; i < recipes.size; i++) {
                Recipe recipe = recipes.get(i);
                if (isLocked(recipe)) continue;

                // 输入（物品 / 液体 / 载荷）统一判定
                if (!recipe.inputsAvailable(this)) continue;

                // 与严格检索一致：热量/电力门槛不满足时，该配方不算“现在能够生产”
                if (recipe.heatEnabled && recipe.heatRequirement > 0
                        && recipe.stopLowTemperature && rawHeat < recipe.heatRequirement) {
                    continue;
                }
                if (recipe.powerUse > 0 && (power.status <= 0 || powerConsumer.isUndervoltage(this, recipe))) {
                    continue;
                }

                anyProducible = true;
                // 只要还有一个能产出的配方，就不算“输出满”
                if (canOutputForRecipe(recipe)) return false;
            }
            return anyProducible;
        }

        /** 方块状态：自动模式下可生产配方全满时显示“输出满”，否则无配方/断电显示“缺少输入”，其余沿用父类逻辑。 */
        @Override
        public BlockStatus status() {
            Recipe current = getRecipe();
            if (enabled) {
                // 自动选择状态：输入与热量/电力都满足、但所有可生产配方输出仓已满 → 满
                if (auto && allProducibleOutputsFull()) {
                    return BlockStatus.noOutput;
                }
                if (current == null) {
                    return BlockStatus.noInput;
                }
                if (power.status <= 0) {
                    return BlockStatus.noInput;
                }
            }
            return super.status();
        }

        /**
         *  `control config <building> 3 0 0 0` 调用的是本方法
         * 编号按面板 1-based：1..n 选第 n 个配方，<=0 切回自动模式。
         */
        @Override
        public void control(LAccess access, double p1, double p2, double p3, double p4) {
            // 玩家启用超控：忽略一切逻辑控制指令（既不记录驱动时刻，也不改动配方选择）
            if (logicOverride) return;
            if (access == LAccess.config) {
                // 记录逻辑驱动时刻：面板据此把按钮全部置灰，只显示逻辑生效后的最终状态
                logicControlTime = Time.time;
                applyLogicRecipe((int) p1);
                // 立刻重刷面板（不等面板 update 的下一帧比较）：逻辑块一传入控制就显示最新方块状态
                Runnable panelRefresh = this.panelRefresh;
                if (panelRefresh != null) panelRefresh.run();
                return;
            }
            super.control(access, p1, p2, p3, p4);
        }

        /** 逻辑的对象型 control（如把某个单位/内容作为 config 值）；超控期间一并忽略。 */
        @Override
        public void control(LAccess access, Object p1, double p2, double p3, double p4) {
            if (logicOverride) return;
            super.control(access, p1, p2, p3, p4);
        }

        /** 逻辑的属性写入入口；超控期间一并忽略。 */
        @Override
        public void setProp(LAccess prop, double value) {
            if (logicOverride) return;
            super.setProp(prop, value);
        }

        @Override
        public void setProp(LAccess prop, Object value) {
            if (logicOverride) return;
            super.setProp(prop, value);
        }

        @Override
        public void setProp(UnlockableContent content, double value) {
            if (logicOverride) return;
            super.setProp(content, value);
        }

        /** 应用逻辑指令的配方选择：编号 1-based（对应面板 [n]），<=0 切自动；切换时重置生产进度。 */
        private void applyLogicRecipe(int n) {
            if (n <= 0) {
                auto = true;
                selectedIndex = -1;
                progress = 0f;
                return;
            }
            int idx = n - 1;
            if (idx < recipes.size && !isLocked(recipes.get(idx))) {
                auto = false;
                selectedIndex = idx;
                if (recipeIndex != idx) {
                    progress = 0f;
                }
                recipeIndex = idx;
            }
        }
    }
}
