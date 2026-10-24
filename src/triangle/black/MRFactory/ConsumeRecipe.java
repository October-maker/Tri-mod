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

/**
 * 多配方工厂的动态输入消费器。
 *
 * <p>不同配方需要的物品种类不同，无法像原版那样在方块初始化时静态声明消费者，
 * 因此由本类在运行时读取当前配方：
 * <ul>
 *   <li>{@link #update(Building)}：按帧扣除液体输入；</li>
 *   <li>{@link #trigger(Building)}：每个生产周期扣除物品与载荷输入；</li>
 *   <li>{@link #efficiency(Building)}：返回输入满足度（0~1），驱动生产速度；</li>
 *   <li>{@link #build(Building, Table)}：在配置面板中按当前配方重建需求图标。</li>
 * </ul>
 */
class ConsumeRecipe extends Consume {
    /** 当前生效配方（用于实际结算）。 */
    public final Func<Building, Recipe> recipe;
    /** 用于界面展示的配方（可回退到首个配方）。 */
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

    /** 声明该方块需要物品、液体与载荷输入。 */
    @Override
    public void apply(Block block) {
        block.hasItems = true;
        block.hasLiquids = true;
        block.acceptsItems = true;
        block.acceptsPayload = true;
    }

    /** 每帧按效率倍率扣除当前配方的液体输入。 */
    @Override
    public void update(Building build) {
        Recipe r = recipe.get(build);
        if (r == null) return;
        float m = multiplier.get(build);
        for (LiquidStack stack : r.inputLiquid) {
            build.liquids.remove(stack.liquid, stack.amount * build.edelta() * m);
        }
    }

    /** 每个生产周期开始时扣除物品与载荷输入（液体已在 update 中按帧扣除）。 */
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

    /**
     * 计算输入满足度：取物品、载荷、液体的最紧缺项作为效率（0~1）。
     * 液体按“当前存量 / 本帧需求”折算，任一输入缺失即返回 0。
     */
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

    /**
     * 在配置面板中显示当前配方所需的输入图标。
     * 通过 update 回调监听配方切换，仅在配方变化时重建，避免每帧 clear + 新建 ReqImage。
     */
    @Override
    public void build(Building build, Table table) {
        if (display.get(build) == null) return;
        // 缓存上一次渲染的配方；仅当配方切换时才重建需求表
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