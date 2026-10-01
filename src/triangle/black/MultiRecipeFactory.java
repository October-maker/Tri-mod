package triangle.black;

import arc.Core;
import arc.math.Mathf;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Strings;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.core.UI;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;
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

public class MultiRecipeFactory extends GenericCrafter{
    public Seq<Recipe> recipes = new Seq<>();
    public float basePowerUse = 1.0f; // 基础电力消耗

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
                        t.table(inner -> {
                            inner.table(rec -> {
                                rec.left();
                                recipe.inputItem.each(stack -> rec.add(display(stack.item, stack.amount, recipe.craftTime)).row());
                                recipe.inputLiquid.each(stack -> rec.add(display(stack.liquid, stack.amount * 60, 60f)).row());
                                recipe.inputPayload.each(stack -> rec.add(display(stack.item, stack.amount, recipe.craftTime)).row());
                            }).growX();

                            inner.table(rec ->{
                                rec.left();
                                // 添加电力消耗显示
                                rec.add("[stat]" + Strings.autoFixed(recipe.powerUse * 60f, 2) + " [lightgray]" + StatUnit.powerSecond.localized()).row();
                                // 生产箭头
                                rec.image(Icon.right).size(32f).padLeft(8f).padRight(12f).row();
                                // 添加热量消耗显示（配方启用热量且需要输入热量时）
                                if (recipe.heatEnabled && recipe.heatRequirement > 0) {
                                    rec.add("[stat]" + Strings.autoFixed(recipe.heatRequirement, 2) + " [lightgray]" + StatUnit.heatUnits.localized()).row();
                                }
                            });

                            inner.table(rec -> {
                                recipe.outputItem.each(stack -> rec.add(display(stack.item, stack.amount, recipe.craftTime)).row());
                                recipe.outputLiquid.each(stack -> rec.add(display(stack.liquid, stack.amount * 60, 60f)).row());
                                recipe.outputPayload.each(stack -> rec.add(display(stack.item, stack.amount, recipe.craftTime)).row());
                                // 添加热量产出显示（配方启用热量且能产出热量时）
                                if (recipe.heatEnabled && recipe.heatOutput > 0) {
                                    rec.add("[stat]" + Strings.autoFixed(recipe.heatOutput, 2) + " [lightgray]" + StatUnit.heatUnits.localized()).row();
                                }
                            }).growX().row();

                            inner.table(rec -> {
                                rec.add("--------");
                            });
                        });
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
    }

    // 4. 建筑实体类
    public class MultiRecipeFactoryBuild extends GenericCrafterBuild implements HeatConsumer, HeatBlock {
        public int recipeIndex = -1;
        public float currentPowerUse = 0f;  //当前配方的电力消耗
        public float[] sideHeat = new float[4]; // 四面热量输入
        public float heat = 0f; // 当前热量值（有效热量，供可用性/消耗检查使用）
        public float rawHeat = 0f; // 实际接收的热量（供效率计算与显示使用）
        public float warmup = 0f; // 预热值（用于热量产出）

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

        // 预热目标
        @Override
        public float warmupTarget() {
            Recipe recipe = getRecipe();
            if (recipe == null || !recipe.heatEnabled || recipe.heatRequirement <= 0) {
                return 0f;
            }
            return Mathf.clamp(rawHeat / recipe.heatRequirement);
        }

        // 更新配方 - 寻找可用配方
        public void updateRecipe() {
            for (int i = recipes.size - 1; i >= 0; i--) {
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

                // 电力不足：仅当配方设定“欠压即停”时才排除
                if (valid && recipe.powerUse > 0 && recipe.stopUndervoltage && power.status <= 0) {
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

            if (recipes.get(recipeIndex).powerUse > 0 && power.status <= 0 && recipes.get(recipeIndex).stopUndervoltage) {
                return false;
            }

            return true;
        }

        @Override
        public void updateTile() {
            // 如果当前配方无效，尝试更新配方（严格检索，保持原逻辑）
            if (!validRecipe()) updateRecipe();
            // 严格检索无结果时，按配方布尔值放宽热量/电力要求再检索一次
            if (getRecipe() == null) trySelectRelaxed();

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
            
            // 更新预热值（用于热量产出）
            if (current != null && current.heatEnabled && current.heatOutput > 0) {
                warmup = Mathf.lerpDelta(warmup, efficiency, 0.1f);
            } else {
                warmup = 0f;
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

            // 检查电力供应
            Recipe currentRecipe = getRecipe();
            if (currentRecipe != null && power.status <= 0) {
                return false;
            }

            // 检查热量供应（如果配方需要热量）
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
            updateRecipe(); // 尝试更新配方（严格检索，保持原逻辑）
            if (getRecipe() == null) trySelectRelaxed(); // 严格检索无结果时按配方布尔值放宽
        }

        @Override
        public BlockStatus status() {
            if (enabled && getRecipe() == null) {return BlockStatus.noInput;}
            else if (getRecipe() != null && power.status <= 0) {return BlockStatus.noInput;}
            else return super.status();
        }
    }
}
