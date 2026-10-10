package triangle.black;

import arc.Core;
import arc.Events;
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
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.TechTree;
import mindustry.core.UI;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.world.blocks.heat.HeatConsumer;
import mindustry.world.blocks.payloads.BuildPayload;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.*;

import java.awt.*;

public class MultiRecipeFactory extends GenericCrafter{
    public Seq<Recipe> recipes = new Seq<>();

    public Seq<Item> itemOutput = new Seq<>();
    public Seq<Liquid> liquidOutput = new Seq<>();
    public Seq<UnlockableContent> payloadOutput = new Seq<>();

    public boolean checkItemOutputFullness = true;
    public boolean checkLiquidOutputFullness = true;

    public MultiRecipeFactory(String name) {
        super(name);
        // 注册自定义消费器
        consume(new ConsumeRecipe(MultiRecipeFactoryBuild::getRecipe, MultiRecipeFactoryBuild::getDisplayRecipe));
        // 注册动态电力消耗器
        consume(new ConsumePowerRecipe(build -> {
            if (build instanceof MultiRecipeFactoryBuild mrb) {
                return mrb.getRecipe();
            }
            return null;
        }));
        hasPower = true;
        consumesPower = true;
        rotate = true;
        rotateDraw = false;
        configurable = true; // 允许打开配置面板（手动选定配方 / 自动匹配开关）
    }

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

    // 将 Drawable 包装为带白色(#FFFFFF)描边的版本
    public static Drawable whiteOutlined(Drawable base) {
        return new Drawable() {
            @Override
            public void draw(float x, float y, float width, float height) {
                base.draw(x, y, width, height);
                stroke(x, y, width, height);
            }

            @Override
            public void draw(float x, float y, float width, float height, float color, float u1, float v1, float u2, float v2) {
                base.draw(x, y, width, height, color, u1, v1, u2, v2);
                stroke(x, y, width, height);
            }

            private void stroke(float x, float y, float width, float height) {
                Draw.color(Color.white);
                Lines.stroke(2.5f);
                roundRectStroke(x, y, width, height, 8f);
                Draw.reset();
            }

            @Override public float getLeftWidth() { return base.getLeftWidth(); }
            @Override public void setLeftWidth(float leftWidth) { base.setLeftWidth(leftWidth); }
            @Override public float getRightWidth() { return base.getRightWidth(); }
            @Override public void setRightWidth(float rightWidth) { base.setRightWidth(rightWidth); }
            @Override public float getTopHeight() { return base.getTopHeight(); }
            @Override public void setTopHeight(float topHeight) { base.setTopHeight(topHeight); }
            @Override public float getBottomHeight() { return base.getBottomHeight(); }
            @Override public void setBottomHeight(float bottomHeight) { base.setBottomHeight(bottomHeight); }
            @Override public float getMinWidth() { return base.getMinWidth(); }
            @Override public void setMinWidth(float minWidth) { base.setMinWidth(minWidth); }
            @Override public float getMinHeight() { return base.getMinHeight(); }
            @Override public void setMinHeight(float minHeight) { base.setMinHeight(minHeight); }
        };
    }

    // 绘制圆角矩形描边（四个角用弧线采样，直边由相邻弧线端点自然连成）
    private static void roundRectStroke(float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) / 2f);
        int segs = 8; // 每个角四分一圆弧的采样段数
        FloatSeq pts = new FloatSeq();
        // 顺时针依次画出四个圆角
        addArc(pts, x + w - r, y + r, r, 270f, 360f, segs); // 右下角
        addArc(pts, x + w - r, y + h - r, r, 0f, 90f, segs); // 右上角
        addArc(pts, x + r, y + h - r, r, 90f, 180f, segs); // 左上角
        addArc(pts, x + r, y + r, r, 180f, 270f, segs); // 左下角
        Lines.polyline(pts, true);
    }

    // 向点集追加一段圆弧上的采样点（角度为度，0° 指向 +x，顺时针增加）
    private static void addArc(FloatSeq pts, float cx, float cy, float r, float a0, float a1, int segs) {
        for (int i = 0; i <= segs; i++) {
            float a = Mathf.lerp(a0, a1, i / (float) segs);
            pts.add(cx + Mathf.cosDeg(a) * r);
            pts.add(cy + Mathf.sinDeg(a) * r);
        }
    }

    // 进度条：禁用 Bar 自带的值平滑（value 逐渐逼近 fraction），
    // 每次绘制前 snap() 使填充严格等于 fraction，保证按时长从左到右循环填充
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

    // 显示所有配方
    public StatValue displayRecipes() {
        return table -> {
            table.row();
            table.table(cont -> {
                for (int i = 0; i < recipes.size; i++) {
                    Recipe recipe = recipes.get(i);
                    int finalI = i;
                    cont.table(t -> {
                        t.left().marginLeft(12f).add("[accent][" + (finalI + 1) + "]:[]").width(48f);
                        t.table(inner -> {//加小背景
                            inner.table(whiteOutlined(Styles.grayPanel), rec -> {//输入框背景（带白色描边）
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

                            inner.table(whiteOutlined(Styles.grayPanel), rec -> {
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

    // 创建物品/液体显示
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
                        Strings.autoFixed(amount / (timePeriod / 60f), 2) + StatUnit.perSecond.localized() )//+ Strings.autoFixed(amount / (timePeriod / 60f), 2) + " " + StatUnit.powerSecond.localized() 显示电量消耗
                .padLeft(2).padRight(5).style(Styles.outlineLabel);
        return table;
    }

    // 仅显示资源贴图（用于配置面板中的配方行），悬停仍显示资源名
    public static Table displayIcon(UnlockableContent content) {
        Table table = new Table();
        Stack stack = new Stack();
        stack.add(new Table(o -> {
            o.left();
            o.add(new Image(content.uiIcon)).size(32f).scaling(Scaling.fit);
        }));
        StatValues.withTooltip(stack, content);
        table.add(stack);
        return table;
    }

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
            if (r.requiresResearch) {
                r.research = createResearch(this, r, i + 1);
            }
        }

        // 挂载研究条目到科技树节点的时机延后到 ContentInitEvent 之后：
        // 此时原版科技树（SerpuloTechTree）已完全加载，且工厂自身的科技树节点已就位，
        // 避免在 init() 阶段 TechTree.all 尚未包含工厂节点导致 find 返回 null 而无法挂载。
        Events.on(ContentInitEvent.class, e -> {
            for (int i = 0; i < recipes.size; i++) {
                Recipe r = recipes.get(i);
                if (r.requiresResearch && r.research != null) {
                    addResearchNode(this, r.research);
                }
            }
        });
    }

    // 研究条目：配方所需的 UnlockableContent（用于科技树研究与解锁判断）
    public static class RecipeResearch extends UnlockableContent {
        public ItemStack[] cost;
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
    }

    // 为单个配方创建研究条目（研究消耗默认 inputItem，可 researchCost 覆盖；图标可用"工厂名-配方编号.png"覆盖）
    public static UnlockableContent createResearch(UnlockableContent factory, Recipe r, int index) {
        String rname = factory.name + "-" + index;
        RecipeResearch c = new RecipeResearch(rname);
        c.cost = r.researchCost != null ? r.researchCost : r.inputItem.toArray(ItemStack.class);
        c.localizedName = researchName(factory, r, index);
        c.uiIcon = researchIcon(factory, r, rname);
        // 研究条目不注册进 Vars.content；hideDatabase=true 使数据库的 select 过滤将其排除（官方隐藏方式），databaseTabs 清空确保不归属任何 tab。
        // 仅通过科技树（TechNode）显示。挂载（addResearchNode）在 ContentInitEvent 之后进行。
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

    private static TextureRegion researchIcon(UnlockableContent factory, Recipe r, String rname) {
        // 用户覆盖文件："工厂名-配方编号.png" → atlas 名
        if (Core.atlas.has(rname)) return Core.atlas.find(rname);
        if (!r.outputItem.isEmpty()) return r.outputItem.first().item.uiIcon;
        if (!r.outputLiquid.isEmpty()) return r.outputLiquid.first().liquid.uiIcon;
        if (!r.outputPayload.isEmpty()) return r.outputPayload.first().item.uiIcon;
        return factory.uiIcon;
    }

    // 将研究条目节点挂到工厂科技树节点之后（参考 addNode 改进，增加工厂节点缺失的防御）
    public static void addResearchNode(UnlockableContent parent, UnlockableContent child) {
        TechTree.TechNode context = TechTree.all.find(t -> t.content == parent);
        if (context == null) {
            Log.warn("[triangle] factory '@' not found in tech tree; research node '@' not attached.", parent.name, child.name);
            return;
        }
        TechTree.TechNode node = new TechTree.TechNode(null, child, child.researchRequirements());
        if (!context.children.contains(node)) {
            context.children.add(node);
        }
        node.parent = context;
        node.planet = context.planet;
    }

    // 配方是否处于未解锁（需研究且未研究）
    public static boolean isLocked(Recipe r) {
        return r.requiresResearch && (r.research == null || !r.research.unlocked());
    }

    // 4. 建筑实体类
    public class MultiRecipeFactoryBuild extends GenericCrafterBuild implements HeatConsumer, HeatBlock {
        public int recipeIndex = -1;
        public float currentPowerUse = 0f;  //当前配方的电力消耗
        public float[] sideHeat = new float[4]; // 四面热量输入
        public float heat = 0f; // 当前热量值（有效热量，供可用性/消耗检查使用）
        public float rawHeat = 0f; // 实际接收的热量（供效率计算与显示使用）
        public float warmup = 0f; // 预热值（用于热量产出）
        public boolean auto = true; // true=自动匹配配方；false=手动选定 selectedIndex 配方制作
        public int selectedIndex = -1; // 手动模式下选定的配方索引（-1 表示未选定）

        public Recipe getRecipe() {
            if (recipeIndex < 0 || recipeIndex >= recipes.size) return null;
            return recipes.get(recipeIndex);
        }

        public Recipe getDisplayRecipe() {
            if (recipeIndex < 0 && recipes.size > 0) {
                return recipes.first();
            }
            return getRecipe();
        }

        // 配置序列化：自动模式编码为 -1，手动模式编码为选中的配方索引（供蓝图复制/粘贴）
        @Override
        public Object config() {
            return auto ? -1 : selectedIndex;
        }

        // 真正的配置处理入口：蓝图粘贴(configureAny)、UI(configure)都会经 Call.tileConfig 触发本方法。
        // 注意 configure()/configureAny() 本身只广播，不应用配置。
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

        // 存档读写：保存自动模式 / 选中配方索引（version 1 起包含该额外数据，旧存档自动用默认值）
        @Override
        public byte version() {
            return 1;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(auto ? -1 : selectedIndex);
        }

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

        // HeatConsumer 接口实现
        @Override
        public float[] sideHeat() {
            return sideHeat;
        }

        @Override
        public float heatRequirement() {
            Recipe recipe = getRecipe();
            if (recipe != null && recipe.heatEnabled && recipe.heatRequirement > 0) {
                return recipe.heatRequirement;
            }
            return 0f;
        }

        // HeatBlock 接口实现
        @Override
        public float heat() {
            Recipe recipe = getRecipe();
            if (recipe != null && recipe.heatEnabled && recipe.heatOutput > 0) {
                return warmup * recipe.heatOutput;
            }
            return 0f;
        }

        @Override
        public float heatFrac() {
            Recipe recipe = getRecipe();
            if (recipe != null && recipe.heatEnabled && recipe.heatOutput > 0) {
                return warmup;
            }
            return 0f;
        }

        // 热量效率计算（使用实际接收热量 rawHeat，低热时按比例降速）
        @Override
        public float efficiencyScale() {
            Recipe recipe = getRecipe();
            if (recipe == null || !recipe.heatEnabled || recipe.heatRequirement <= 0) {
                return 1f;
            }
            float over = Math.max(rawHeat - recipe.heatRequirement, 0f);
            return Math.min(Mathf.clamp(rawHeat / recipe.heatRequirement) + over / recipe.heatRequirement, recipe.recipeMaxEfficiency);
        }

        // 返回用于drawer的warmup值
        @Override
        public float warmupTarget() {
            Recipe recipe = getRecipe();
            if (recipe == null) return 0f;
            if (recipe.heatEnabled && recipe.heatRequirement > 0) {
                return Mathf.clamp(rawHeat / recipe.heatRequirement);
            }
            return efficiency;
        }

        // 更新配方 - 寻找可用配方
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
                    currentPowerUse = recipes.get(i).powerUse / 60;
                    return;
                }
            }
            recipeIndex = -1;
            currentPowerUse = 0f;
        }

        // 宽松配方检索：仅当严格检索(updateRecipe)无结果时使用。
        // 热量/电力不足的配方，仅在其“即停”布尔值为 true 时排除；为 false 时仍可选（以低效率继续工作）。
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
                if (valid && recipe.powerUse > 0 && recipe.stopUndervoltage && power.status < 1f) {
                    valid = false;
                }

                // 检查输出容量
                if (valid && !canOutputForRecipe(recipe)) {
                    continue;
                }

                if (valid) {
                    recipeIndex = i;
                    currentPowerUse = recipe.powerUse / 60;
                    return;
                }
            }
            // 无可用配方时不改动 recipeIndex（保持 -1）
        }

        // 检查特定配方的输出是否可用
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

        // 检查当前配方是否有效
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

            //当热量和电力消耗不足时立刻拉闸
            if (recipes.get(recipeIndex).heatEnabled && recipes.get(recipeIndex).heatRequirement > 0 && heat < recipes.get(recipeIndex).heatRequirement && recipes.get(recipeIndex).stopLowTemperature) {
                return false;
            }

            if (recipes.get(recipeIndex).powerUse > 0 && power.status < 1f && recipes.get(recipeIndex).stopUndervoltage) {
                return false;
            }

            return true;
        }

        @Override
        public void updateTile() {
            if (auto) {
                // === 自动匹配模式 ===
                // 如果当前配方无效，尝试更新配方（严格检索，保持原逻辑）
                if (!validRecipe()) updateRecipe();

                // 欠压即停：严格检索仅按“彻底没电”(power.status<=0)判断，供电不足(status<1)时可能把“欠压即停”配方重新选中；此处撤销该选中，使其进入宽松检索被排除（与热量低温即停旁路对应）
                Recipe gated = getRecipe();
                if (gated != null && gated.powerUse > 0 && gated.stopUndervoltage && power.status < 1f) {
                    recipeIndex = -1;
                    currentPowerUse = 0f;
                }

                // 严格检索无结果时，按配方布尔值放宽热量/电力要求再检索一次
                if (getRecipe() == null) trySelectRelaxed();
            } else {
                // === 手动选定模式：锁定 selectedIndex 配方制作 ===
                // 输入不足时由 ConsumeRecipe.efficiency() 使效率归零，super.updateTile() 不会推进生产
                if (selectedIndex >= 0 && selectedIndex < recipes.size && !isLocked(recipes.get(selectedIndex))) {
                    recipeIndex = selectedIndex;
                    currentPowerUse = recipes.get(selectedIndex).powerUse / 60f;
                } else {
                    recipeIndex = -1;
                    currentPowerUse = 0f;
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

        @Override
        public boolean shouldConsume() {
            if (getRecipe() == null) return false;

            // 检查电力供应：彻底没电停止；或配方设“欠压即停”且供电不足(status<1)时停止
            Recipe currentRecipe = getRecipe();
            if (currentRecipe != null) {
                if (power.status <= 0 || (currentRecipe.powerUse > 0 && currentRecipe.stopUndervoltage && power.status < 1f)) {
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

        @Override
        public float getProgressIncrease(float baseTime) {
            float scl = 1f;
            if (getRecipe() != null) scl = getRecipe().craftTime / craftTime;
            return super.getProgressIncrease(baseTime) / scl;
        }

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

        // 配置界面：自动匹配开关 + 手动选定配方（一行一个配方，仅贴图；点触按钮 + 选中指示灯）
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
            // 每帧兜底刷新（配置面板打开期间持续同步）
            table.update(refresh);
        }

        @Override
        public BlockStatus status() {
            if (enabled && getRecipe() == null) {return BlockStatus.noInput;}
            else if (getRecipe() != null && power.status <= 0) {return BlockStatus.noInput;}
            else return super.status();
        }
    }
}
