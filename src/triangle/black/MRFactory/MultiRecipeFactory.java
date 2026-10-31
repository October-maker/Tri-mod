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
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.FloatSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Scaling;
import arc.util.Strings;
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
import mindustry.game.Objectives;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.world.blocks.heat.HeatConsumer;
import mindustry.world.blocks.payloads.BuildPayload;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.*;
import triangle.black.TriDraw;
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
        configurable = true; // 允许打开配置面板（手动选定配方 / 自动匹配开关）
    }

    /** 放置计划实际建造的方块；堆叠工厂在这里切换为固定层数变体。 */
    @Override
    public Block getReplacement(BuildPlan req, Seq<BuildPlan> plans){
        if(stackable){
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
        if(stackable && tile != null && tile.build != null){
            Block existing = tile.build.block;
            boolean chain = existing == this
                || (existing instanceof StackedMultiRecipeFactory variant && variant.baseFactory == this);
            if(chain && StackedMultiRecipeFactory.layersOf(existing) < stackLimitNow()) return true;
        }
        return super.canPlaceOn(tile, team, rotation);
    }

    @Override
    public boolean canReplace(Block other){
        if(stackable && other == this) return true;
        if(stackable && other instanceof StackedMultiRecipeFactory variant
            && variant.baseFactory == this && variant.stackable) return true;
        return super.canReplace(other);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        if((stackable || this instanceof StackedMultiRecipeFactory)
            && StackedMultiRecipeFactory.atStackLimit(this, x, y)){
            drawPlaceText(Core.bundle.get("tri.stack.limit"), x, y, false);
        }
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
        if(Vars.state != null && Vars.state.rules.infiniteResources) return stackLimit;
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
     * 根据配方表汇总输入过滤与输出列表，并设置父类所需的输出占位。
     * 配方为空时使用铜/水作为零数量占位，避免原版生产逻辑出现空数组。
     */
    @Override
    public void init() {
        super.init();

        // 设置输入过滤器和输出列表
        recipes.each(recipe -> {
            recipe.inputItem.each(stack -> itemFilter[stack.item.id] = true);
            recipe.inputLiquid.each(stack -> liquidFilter[stack.liquid.id] = true);

            recipe.outputItem.each(stack -> itemOutput.add(stack.item));
            recipe.outputLiquid.each(stack -> liquidOutput.add(stack.liquid));
            recipe.outputPayload.each(stack -> payloadOutput.add(stack.item));
        });

        // 初始化输出
        if (recipes.isEmpty()) {
            outputItems = new ItemStack[]{new ItemStack(Items.copper, 0)};
            outputLiquids = new LiquidStack[]{new LiquidStack(Liquids.water, 0f)};
        } else {
            Recipe firstRecipe = recipes.first();

            outputItems = new ItemStack[Math.max(firstRecipe.outputItem.size, 1)];
            for (int i = 0; i < outputItems.length; i++) {
                outputItems[i] = i < firstRecipe.outputItem.size
                        ? firstRecipe.outputItem.get(i)
                        : new ItemStack(Items.copper, 0);
            }

            outputLiquids = new LiquidStack[Math.max(firstRecipe.outputLiquid.size, 1)];
            for (int i = 0; i < outputLiquids.length; i++) {
                outputLiquids[i] = i < firstRecipe.outputLiquid.size
                        ? new LiquidStack(firstRecipe.outputLiquid.get(i).liquid, 0f)
                        : new LiquidStack(Liquids.water, 0f);
            }
        }

        craftTime = 60f;
        if (!liquidOutput.isEmpty()) outputsLiquid = true;
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
                                    rec.add("[#FF6666]" + Strings.autoFixed(recipe.heatRequirement, 2) + " [lightgray]" + StatUnit.heatUnits.localized()).row();
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
                    Core.bundle.format("bar.heatpercent", (int)(entity.rawHeat + 0.01f), (int)(entity.efficiencyScale() * 100 + 0.01f)),
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

        /** 当前生效配方；索引越界时返回 null。 */
        public Recipe getRecipe() {
            if (recipeIndex < 0 || recipeIndex >= recipes.size) return null;
            return recipes.get(recipeIndex);
        }

        /** 用于界面展示的配方：尚未选中时回退到第一条配方。 */
        public Recipe getDisplayRecipe() {
            if (recipeIndex < 0 && recipes.size > 0) {
                return recipes.first();
            }
            return getRecipe();
        }

        /** 配置序列化：自动模式编码为 -1，手动模式编码为选中的配方索引（供蓝图复制/粘贴）。 */
        @Override
        public Object config() {
            return auto ? -1 : selectedIndex;
        }

        /**
         * 配置处理入口：蓝图粘贴（configureAny）与 UI 配置（configure）都会经 Call.tileConfig 触发本方法。
         * 负数表示切回自动模式，其余值作为手动配方索引。
         */
        @Override
        public void configured(Unit unit, Object value) {
            if (value instanceof Integer v) {
                if (v < 0) {
                    auto = true;
                    selectedIndex = -1;
                } else if (v >= 0 && v < recipes.size) {
                    auto = false;
                    selectedIndex = v;
                }
            } else {
                super.configured(unit, value);
            }
        }

        /** 存档版本：1 起额外保存自动/手动配方状态。 */
        @Override
        public byte version() {
            return 1;
        }

        /** 写入自动/手动配方状态。 */
        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(auto ? -1 : selectedIndex);
        }

        /** 读取配方状态；旧存档（revision < 1）保持默认的自动模式。 */
        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            // 兼容旧存档：仅当版本 >= 1 时才存在额外 int，否则保持默认（自动模式）
            if (revision >= 1) {
                int v = read.i();
                if (v < 0) {
                    auto = true;
                    selectedIndex = -1;
                } else if (v >= 0 && v < recipes.size) {
                    auto = false;
                    selectedIndex = v;
                }
            }
        }

        /** 四面热量输入数组（HeatConsumer）。 */
        @Override
        public float[] sideHeat() {
            return sideHeat;
        }

        /** 当前配方的热量需求；未启用热量时返回 0。 */
        @Override
        public float heatRequirement() {
            Recipe recipe = getRecipe();
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

        /** 热量效率：以实际接收热量 rawHeat 计算，低热时按比例降速，超热按 recipeMaxEfficiency 封顶。 */
        @Override
        public float efficiencyScale() {
            Recipe recipe = getRecipe();
            if (recipe == null || !recipe.heatEnabled || recipe.heatRequirement <= 0) {
                return 1f;
            }
            float over = Math.max(rawHeat - recipe.heatRequirement, 0f);
            return Math.min(Mathf.clamp(rawHeat / recipe.heatRequirement) + over / recipe.heatRequirement, recipe.recipeMaxEfficiency);
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
         * 严格配方检索：从后往前寻找输入（物品/液体/载荷）、热量、电力均满足且输出未满的配方。
         * 热量/电力只要低于需求即排除；找不到时 recipeIndex 置 -1。
         */
        public void updateRecipe() {
            for (int i = recipes.size - 1; i >= 0; i--) {
                if (isLocked(recipes.get(i))) continue; // 跳过未解锁（需研究未研究）配方
                boolean valid = true;

                // 检查物品输入
                for (ItemStack input : recipes.get(i).inputItem) {
                    if (items.get(input.item) < input.amount) {
                        valid = false;
                        break;
                    }
                }

                // 检查液体输入
                for (LiquidStack input : recipes.get(i).inputLiquid) {
                    if (liquids.get(input.liquid) < input.amount) {
                        valid = false;
                        break;
                    }
                }

                // 检查载荷输入
                for (PayloadStack input : recipes.get(i).inputPayload) {
                    if (getPayloads().get(input.item) < input.amount) {
                        valid = false;
                        break;
                    }
                }

                // 检查热量输入（配方启用热量且需要热量输入时，热量不足则视为不可用）
                if (valid && recipes.get(i).heatEnabled && recipes.get(i).heatRequirement > 0) {
                    if (heat < recipes.get(i).heatRequirement) {
                        valid = false;
                    }
                }

                // 检查电力输入（配方需要电力且当前没有电力供应时，视为不可用）
                if (valid && recipes.get(i).powerUse > 0 && power.status <= 0) {
                    valid = false;
                }

                // 检查输出容量
                if (valid) {
                    if (!canOutputForRecipe(recipes.get(i))) {
                        continue; // 如果输出满，则跳过此配方，继续寻找其他配方
                    }
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
                boolean valid = true;

                // 检查物品输入
                for (ItemStack input : recipe.inputItem) {
                    if (items.get(input.item) < input.amount) {
                        valid = false;
                        break;
                    }
                }

                // 检查液体输入
                if (valid) {
                    for (LiquidStack input : recipe.inputLiquid) {
                        if (liquids.get(input.liquid) < input.amount) {
                            valid = false;
                            break;
                        }
                    }
                }

                // 检查载荷输入
                if (valid) {
                    for (PayloadStack input : recipe.inputPayload) {
                        if (getPayloads().get(input.item) < input.amount) {
                            valid = false;
                            break;
                        }
                    }
                }

                // 热量不足：仅当配方设定“低温即停”时才排除
                if (valid && recipe.heatEnabled && recipe.heatRequirement > 0 && recipe.stopLowTemperature && rawHeat < recipe.heatRequirement) {
                    valid = false;
                }

                // 电力不足：仅当配方设定“欠压即停”时才排除（供电不足 power.status<1 即视为不满足）
                if (valid && powerConsumer.isUndervoltage(this)) {
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

        /** 检查该配方的物品/液体输出是否还有足够容量。 */
        public boolean canOutputForRecipe(Recipe recipe) {
            // 检查物品输出容量
            if (checkItemOutputFullness) {
                for (ItemStack output : recipe.outputItem) {
                    if (items.get(output.item) + output.amount > itemCapacity) {
                        return false;
                    }
                }
            }

            // 检查液体输出容量
            if (checkLiquidOutputFullness) {
                for (LiquidStack output : recipe.outputLiquid) {
                    if (liquids.get(output.liquid) + output.amount > liquidCapacity) {
                        return false;
                    }
                }
            }

            return true;
        }

        /** 当前配方是否仍可用：输入充足、未锁定，且未触发低温/欠压跳闸。 */
        public boolean validRecipe() {
            if (recipeIndex < 0) return false;
            if (isLocked(recipes.get(recipeIndex))) return false; // 未解锁配方视为无效

            for (ItemStack input : recipes.get(recipeIndex).inputItem) {
                if (items.get(input.item) < input.amount) {
                    return false;
                }
            }

            for (LiquidStack input : recipes.get(recipeIndex).inputLiquid) {
                if (liquids.get(input.liquid) < input.amount) {
                    return false;
                }
            }

            for (PayloadStack input : recipes.get(recipeIndex).inputPayload) {
                if (getPayloads().get(input.item) < input.amount) {
                    return false;
                }
            }

            // 当热量和电力消耗不足时立刻拉闸
            if (recipes.get(recipeIndex).heatEnabled && recipes.get(recipeIndex).heatRequirement > 0 && heat < recipes.get(recipeIndex).heatRequirement && recipes.get(recipeIndex).stopLowTemperature) {
                return false;
            }

            if (powerConsumer.isUndervoltage(this)) {
                return false;
            }

            return true;
        }

        /**
         * 每帧逻辑：先确定配方（自动模式下严格检索，失败后宽松检索；手动模式锁定 selectedIndex），
         * 再更新热量并交给父类推进生产，最后补充液体输出与物品容量钳制。
         */
        @Override
        public void updateTile() {
            if (auto) {
                // === 自动匹配模式 ===
                // 如果当前配方无效，尝试更新配方（严格检索，保持原逻辑）
                if (!validRecipe()) updateRecipe();

                // 严格检索无结果时，按配方布尔值放宽热量/电力要求再检索一次
                if (getRecipe() == null) trySelectRelaxed();
            } else {
                // === 手动选定模式：锁定 selectedIndex 配方制作 ===
                // 输入不足时由 ConsumeRecipe.efficiency() 使效率归零，super.updateTile() 不会推进生产
                if (selectedIndex >= 0 && selectedIndex < recipes.size && !isLocked(recipes.get(selectedIndex))) {
                    recipeIndex = selectedIndex;
                } else {
                    recipeIndex = -1;
                }
            }

            Recipe current = getRecipe();

            // 调用父类的 calculateHeat 方法来接收周围热量方块的热量（原始热量）
            rawHeat = calculateHeat(sideHeat);

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

            if (current == null) return;

            boolean outputFull = !canOutputForRecipe(current);
            if (outputFull) {
                return; // 如果输出满了，停止生产
            }

            // 处理液体输出
            if (efficiency > 0 && !current.outputLiquid.isEmpty()) {
                float inc = getProgressIncrease(craftTime / current.craftTime);
                current.outputLiquid.each(stack -> {
                    handleLiquid(this, stack.liquid,
                            Math.min(stack.amount * inc, liquidCapacity - liquids.get(stack.liquid)));
                });
            }

            // 处理物品输出
            current.outputItem.each(stack -> {
                if (items.get(stack.item) >= itemCapacity) {
                    items.set(stack.item, itemCapacity);
                }
            });
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
            liquidOutput.each(output -> dumpLiquid(output, 2f, -1));
        }

        /** 是否允许消耗输入：无配方、断电、低温跳闸或液体输出已满时停止。 */
        @Override
        public boolean shouldConsume() {
            if (getRecipe() == null) return false;

            // 检查电力供应：彻底没电停止；或配方设“欠压即停”且供电不足(status<1)时停止
            Recipe currentRecipe = getRecipe();
            if (currentRecipe != null) {
                if (power.status <= 0 || powerConsumer.isUndervoltage(this)) {
                    return false;
                }
            }

            // 检查热量供应（配方需要热量且有效热量不足时停止；
            // stopLowTemperature=false 时 heat 已被抬升到需求，不触发；=true 时 heat=rawHeat，不足则触发）
            if (currentRecipe != null && currentRecipe.heatEnabled && currentRecipe.heatRequirement > 0) {
                if (heat < currentRecipe.heatRequirement) {
                    return false;
                }
            }

            if (!ignoreLiquidFullness) {
                if (getRecipe().outputLiquid.isEmpty()) return true;
                boolean allFull = true;
                for (var output : getRecipe().outputLiquid) {
                    if (liquids.get(output.liquid) >= liquidCapacity - 0.001f) {
                        if (!dumpExtraLiquid) {
                            return false;
                        }
                    } else {
                        allFull = false;
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
            return super.getProgressIncrease(baseTime) / scl;
        }

        /** 完成一个生产周期：消耗输入、输出物品，并在自动模式下重新匹配配方。 */
        @Override
        public void craft() {
            if (getRecipe() == null) return;

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
                updateRecipe(); // 自动模式：尝试更新配方（严格检索，保持原逻辑）
                if (getRecipe() == null) trySelectRelaxed(); // 严格检索无结果时按配方布尔值放宽
            }
            // 手动模式：保持锁定 selectedIndex，不重新选择配方
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

            Seq<Button> rows = new Seq<>();
            Seq<Image> checks = new Seq<>();

            TextButton autoBtn = new TextButton("AUTO", autoActive);

            // 状态刷新：AUTO 激活贴图/文本、配方指示灯、禁用灰显
            Runnable refresh = () -> {
                if (auto) {
                    autoBtn.setStyle(autoActive);
                    autoBtn.setText("[white]" + Core.bundle.get("autoRecipe") + "[lightgray]" + Core.bundle.get("onRe"));
                } else {
                    autoBtn.setStyle(autoInactive);
                    autoBtn.setText("[white]" + Core.bundle.get("autoRecipe") + "[lightgray]" + Core.bundle.get("offRe"));
                }
                for (int i = 0; i < rows.size; i++) {
                    rows.get(i).setDisabled(isLocked(recipes.get(i)));
                    boolean sel = !auto && selectedIndex == i;
                    checks.get(i).setDrawable(sel ? Tex.checkOnOver : Tex.checkOff);
                }
            };

            autoBtn.clicked(() -> {
                auto = true;
                selectedIndex = -1;
                configure(-1);
                refresh.run(); // 点击后立即反映黄底/指示灯
            });
            table.add(autoBtn).size(240f, 46f).padBottom(8f).row();

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
                                }).row();
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
                        if (isLocked(recipe)) return;
                        if (!auto && selectedIndex == finalI) {
                            // 再次点击已选中的配方：取消选择，恢复自动
                            configure(-1);
                        } else {
                            // 选中该配方：切到手动（旧选中自动被本按钮替代）
                            configure(finalI);
                        }
                        refresh.run(); // 点击后立即同步指示灯与黄底
                    });
                    rows.add(row);
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
            table.update(() -> {
                if(auto != lastAuto[0] || selectedIndex != lastSelected[0]){
                    lastAuto[0] = auto;
                    lastSelected[0] = selectedIndex;
                    refresh.run();
                }
            });
        }

        /** 方块状态：无配方或断电时显示“缺少输入”，其余沿用父类逻辑。 */
        @Override
        public BlockStatus status() {
            if (enabled && getRecipe() == null) {return BlockStatus.noInput;}
            else if (getRecipe() != null && power.status <= 0) {return BlockStatus.noInput;}
            else return super.status();
        }
    }
}
