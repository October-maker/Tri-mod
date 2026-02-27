package triangle.black;

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
import mindustry.type.*;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.payloads.BuildPayload;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.*;

public class MultiRecipeFactory extends GenericCrafter {
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
        hasPower = true;
        consumesPower = true;
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

        consumePower(basePowerUse);

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
        return !itemOutput.isEmpty();
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.input, displayRecipes());
        stats.remove(Stat.output);
        stats.remove(Stat.productionTime);
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
                            inner.table(row -> {
                                row.left();
                                recipe.inputItem.each(stack -> row.add(display(stack.item, stack.amount, recipe.craftTime)));
                                recipe.inputLiquid.each(stack -> row.add(display(stack.liquid, stack.amount * 60, 60f)));
                                recipe.inputPayload.each(stack -> row.add(display(stack.item, stack.amount, recipe.craftTime)));
                                // 添加电力消耗显示
                                row.add("[stat]" + Strings.autoFixed(recipe.powerUse, 2) + " [lightgray]" + StatUnit.powerSecond.localized());
                            }).growX();

                            inner.table(row -> {
                                row.left();
                                row.image(Icon.right).size(32f).padLeft(8f).padRight(12f);
                                recipe.outputItem.each(stack -> row.add(display(stack.item, stack.amount, recipe.craftTime)));
                                recipe.outputLiquid.each(stack -> row.add(display(stack.liquid, stack.amount * 60, 60f)));
                                recipe.outputPayload.each(stack -> row.add(display(stack.item, stack.amount, recipe.craftTime)));
                            }).growX();
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
                        Strings.autoFixed(amount / (timePeriod / 60f), 2) + StatUnit.perSecond.localized())
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
    }

    // 4. 建筑实体类
    public class MultiRecipeFactoryBuild extends GenericCrafterBuild {
        public int recipeIndex = -1;
        public float currentPowerUse = 0f; // 当前配方的电力消耗

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

                // 检查电力输入
                Recipe recipe = recipes.get(i);
                if (power.graph.getLastPowerProduced() < recipe.powerUse * edelta()) {
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
                    currentPowerUse = recipes.get(i).powerUse;
                    return;
                }
            }
            recipeIndex = -1;
            currentPowerUse = 0f;
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
            return true;
        }

        @Override
        public void updateTile() {
            // 如果当前配方无效，尝试更新配方
            if (!validRecipe()) updateRecipe();

            Recipe current = getRecipe();

            // 设置当前配方的电力需求
            if (current != null) {
                // 直接设置consumePower的值
                float powerAvailable = power.graph.getBatteryStored() / power.graph.getBatteryCapacity();

                // 计算电力效率
                if (powerAvailable >= currentPowerUse * edelta()) {
                    power.status = 1f;
                } else {
                    power.status = powerAvailable / (currentPowerUse * edelta());
                }
            } else {
                power.status = 0f;
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
            updateRecipe(); // 尝试更新配方
        }

        @Override
        public BlockStatus status() {
            if (enabled && getRecipe() == null) {return BlockStatus.noInput;}
            else if (getRecipe() != null && power.status <= 0) {return BlockStatus.noInput;}
            else return super.status();
        }
    }
}
