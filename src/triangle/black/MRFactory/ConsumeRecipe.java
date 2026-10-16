package triangle.black.MRFactory;

import arc.func.Func;
import arc.scene.ui.layout.Table;
import mindustry.gen.Building;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.type.PayloadStack;
import mindustry.ui.ReqImage;
import mindustry.world.Block;
import mindustry.world.consumers.Consume;
import mindustry.world.meta.StatValues;

class ConsumeRecipe extends Consume {
    public final Func<Building, Recipe> recipe;
    public final Func<Building, Recipe> display;

    @SuppressWarnings("unchecked")
    public <T extends Building> ConsumeRecipe(Func<T, Recipe> recipe, Func<T, Recipe> display) {
        this.recipe = (Func<Building, Recipe>) recipe;
        this.display = (Func<Building, Recipe>) display;
    }

    @SuppressWarnings("unchecked")
    public <T extends Building> ConsumeRecipe(Func<T, Recipe> recipe) {
        this.recipe = (Func<Building, Recipe>) recipe;
        this.display = (Func<Building, Recipe>) recipe;
    }

    @Override
    public void apply(Block block) {
        block.hasItems = true;
        block.hasLiquids = true;
        block.acceptsItems = true;
        block.acceptsPayload = true;
    }

    @Override
    public void update(Building build) {
        Recipe r = recipe.get(build);
        if (r == null) return;
        float m = multiplier.get(build);
        for (LiquidStack stack : r.inputLiquid) {
            build.liquids.remove(stack.liquid, stack.amount * build.edelta() * m);
        }
    }

    @Override
    public void trigger(Building build) {
        Recipe r = recipe.get(build);
        if (r == null) return;
        float m = multiplier.get(build);
        for (ItemStack stack : r.inputItem) {
            build.items.remove(stack.item, Math.round(stack.amount * m));
        }
        for (PayloadStack stack : r.inputPayload) {
            build.getPayloads().remove(stack.item, Math.round(stack.amount * m));
        }
    }

    @Override
    public float efficiency(Building build) {
        float ed = build.edelta() * build.efficiencyScale();
        if (ed <= 0.00000001f) return 0f;
        Recipe r = recipe.get(build);
        if (r == null) return 0f;
        float m = multiplier.get(build);

        float min = 1f;

        // 检查载荷输入
        for (PayloadStack stack : r.inputPayload) {
            if (!build.getPayloads().contains(stack.item, Math.round(stack.amount * m))) {
                min = 0f;
                break;
            }
        }

        // 检查物品输入
        for (ItemStack stack : r.inputItem) {
            if (!build.items.has(stack.item, Math.round(stack.amount * m))) {
                min = 0f;
                break;
            }
        }

        // 检查液体输入
        for (LiquidStack stack : r.inputLiquid) {
            float req = stack.amount * ed * m;
            // 防止 multiplier=0 导致 0/0=NaN 污染整体效率
            if (req <= 0.00000001f) {
                min = 0f;
                break;
            }
            min = Math.min(build.liquids.get(stack.liquid) / req, min);
        }

        return min;
    }

    @Override
    public void build(Building build, Table table) {
        if (display.get(build) == null) return;
        // 缓存上一次渲染的配方；仅当配方切换时才重建需求表，
        // 避免配置面板打开期间每帧 clear + 新建 ReqImage（原实现每帧全量重建）。
        Recipe[] lastShown = {null};
        table.update(() -> {
            Recipe current = display.get(build);
            if (current == null || lastShown[0] == current) return;
            lastShown[0] = current;
            table.clear();
            table.left();

            ItemStack[] currentItem = current.inputItem.toArray(ItemStack.class);
            LiquidStack[] currentLiquid = current.inputLiquid.toArray(LiquidStack.class);
            PayloadStack[] currentPayload = current.inputPayload.toArray(PayloadStack.class);

            table.table(cont -> {
                int i = 0;

                if (currentItem != null) {
                    for (ItemStack stack : currentItem) {
                        cont.add(new ReqImage(StatValues.stack(stack.item, Math.round(stack.amount * multiplier.get(build))),
                                        () -> build.items != null && build.items.has(stack.item, Math.round(stack.amount * multiplier.get(build)))))
                                .padRight(8).left();
                        if (++i % 4 == 0) cont.row();
                    }
                }

                if (currentLiquid != null) {
                    for (LiquidStack stack : currentLiquid) {
                        cont.add(new ReqImage(stack.liquid.uiIcon,
                                        () -> build.liquids != null && build.liquids.get(stack.liquid) > 0))
                                .size(32).padRight(8);
                        if (++i % 4 == 0) cont.row();
                    }
                }

                if (currentPayload != null) {
                    for (PayloadStack stack : currentPayload) {
                        cont.add(new ReqImage(StatValues.stack(stack.item, Math.round(stack.amount * multiplier.get(build))),
                                        () -> build.getPayloads() != null && build.getPayloads().contains(stack.item, Math.round(stack.amount * multiplier.get(build)))))
                                .padRight(8);
                        if (++i % 4 == 0) cont.row();
                    }
                }
            });
        });
    }
}