package triangle.black.MRFactory;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.BlockBuildBeginEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
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
    /** 放置前捕获的旧建筑，用于迁移库存和层状态。 */
    private static final IntMap<Building> pending = new IntMap<>();

    static{
        Events.on(BlockBuildBeginEvent.class, event -> {
            if(!event.breaking && event.tile != null && event.tile.build != null){
                pending.put(event.tile.pos(), event.tile.build);
            }
        });
    }

    public final MultiRecipeFactory baseFactory;
    public final int layerCount;

    public StackedMultiRecipeFactory(MultiRecipeFactory base, int layerCount){
        super(base.name + "-stack" + layerCount);
        this.baseFactory = base;
        this.layerCount = layerCount;
        copyBase(base);
    }

    /** 为基础工厂预生成 2..stackLimit 层变体。 */
    public static void registerVariants(MultiRecipeFactory base){
        if(base == null || !base.stackable || variants.containsKey(base)) return;
        Seq<StackedMultiRecipeFactory> list = new Seq<>();
        int max = Math.max(2, base.stackLimit);
        for(int i = 2; i <= max; i++){
            list.add(new StackedMultiRecipeFactory(base, i));
        }
        variants.put(base, list);
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
        return layersOf(tile.build.block) >= Math.max(2, base.stackLimit);
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

    @Override
    public void placeBegan(Tile tile, Block previous){
        if(tile != null && tile.build != null){
            pending.put(tile.pos(), tile.build);
        }
        super.placeBegan(tile, previous);
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        if(tile != null && tile.build != null && sameChain(tile.build.block)
            && layersOf(tile.build.block) + 1 == layerCount){
            return true;
        }
        return super.canPlaceOn(tile, team, rotation);
    }

    @Override
    public boolean canReplace(Block other){
        if(other != null && sameChain(other)){
            // 只允许“上一层覆盖下一层”。顶层变体仍可作为新方块覆盖 7 层，
            // 但它自己不能再被更高层覆盖（replaceable=false 负责拦截旧方块方向）。
            return layerCount == layersOf(other) + 1;
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

            Building old = pending.remove(tile.pos());
            if(old != null && old != this){
                if(items != null && old.items != null) items.set(old.items);
                if(liquids != null && old.liquids != null){
                    old.liquids.each((liquid, amount) -> liquids.add(liquid, amount));
                }
                if(old instanceof StackedBuild stacked){
                    for(int i = 0; i < Math.min(layers.size, stacked.layers.size); i++){
                        LayerState src = stacked.layers.get(i);
                        LayerState dst = layers.get(i);
                        dst.recipeIndex = src.recipeIndex;
                        dst.selectedIndex = src.selectedIndex;
                        dst.auto = src.auto;
                        dst.progress = src.progress;
                    }
                }else if(old instanceof MultiRecipeFactoryBuild base){
                    LayerState dst = layers.first();
                    dst.recipeIndex = base.recipeIndex;
                    dst.selectedIndex = base.selectedIndex;
                    dst.auto = base.auto;
                    dst.progress = base.progress;
                }
            }
            clampStorage();
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
            clampStorage();
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
            return liquids != null && liquidTotal() < liquidCapacity && super.acceptLiquid(source, liquid);
        }

        private float liquidTotal(){
            return liquids == null ? 0f : liquids.sum((liquid, amount) -> amount);
        }

        @Override
        public boolean canOutputForRecipe(Recipe recipe){
            if(checkItemOutputFullness){
                for(ItemStack output : recipe.outputItem){
                    if(items.get(output.item) + output.amount > itemCapacity) return false;
                }
            }
            if(checkLiquidOutputFullness){
                for(LiquidStack output : recipe.outputLiquid){
                    if(liquids.get(output.liquid) + output.amount > liquidCapacity) return false;
                }
            }
            return true;
        }

        @Override
        public boolean shouldConsume(){
            if(getRecipe() == null) return false;
            Recipe currentRecipe = getRecipe();
            if(power.status <= 0 || powerConsumer.isUndervoltage(this)) return false;
            if(currentRecipe.heatEnabled && currentRecipe.heatRequirement > 0
                && heat < currentRecipe.heatRequirement) return false;

            if(!ignoreLiquidFullness){
                if(currentRecipe.outputLiquid.isEmpty()) return true;
                boolean allFull = true;
                for(LiquidStack output : currentRecipe.outputLiquid){
                    if(liquids.get(output.liquid) >= liquidCapacity - 0.001f){
                        if(!dumpExtraLiquid) return false;
                    }else{
                        allFull = false;
                    }
                }
                if(allFull) return false;
            }
            return enabled;
        }

        /** 缩容/替换时销毁超出容量的部分；物品按每种独立计算。 */
        public void clampStorage(){
            if(items != null){
                Seq<ItemStack> stacks = new Seq<>();
                items.each((item, amount) -> stacks.add(new ItemStack(item, amount)));
                for(ItemStack stack : stacks){
                    if(stack.amount > itemCapacity){
                        items.remove(stack.item, stack.amount - itemCapacity);
                    }
                }
            }

            if(liquids == null) return;
            while(liquidTotal() > liquidCapacity + 0.0001f){
                Seq<LiquidStack> stacks = new Seq<>();
                liquids.each((liquid, amount) -> stacks.add(new LiquidStack(liquid, amount)));
                if(stacks.isEmpty()) break;
                LiquidStack first = stacks.first();
                liquids.remove(first.liquid, Math.min(first.amount, liquidTotal() - liquidCapacity));
            }
        }

        @Override
        public Object config(){
            StringBuilder out = new StringBuilder();
            for(int i = 0; i < layers.size; i++){
                if(i > 0) out.append(',');
                out.append(layers.get(i).auto ? -1 : layers.get(i).selectedIndex);
            }
            return out.toString();
        }

        @Override
        public void configured(Unit unit, Object value){
            if(value instanceof String text){
                String[] parts = text.isEmpty() ? new String[0] : text.split(",");
                ensureLayers();
                for(int i = 0; i < parts.length && i < layers.size; i++){
                    try{
                        setSelection(layers.get(i), Integer.parseInt(parts[i].trim()));
                    }catch(NumberFormatException ignored){
                        setSelection(layers.get(i), -1);
                    }
                }
            }else if(value instanceof Integer integer){
                LayerState layer = layers.first();
                setSelection(layer, integer);
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
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            ensureLayers();
            write.i(layers.size);
            for(LayerState layer : layers){
                write.i(layer.auto ? -1 : layer.selectedIndex);
                write.f(layer.progress);
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
                    setSelection(layer, value);
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
        }

        @Override
        public void buildConfiguration(Table table){
            table.clearChildren();

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
                        recipeRows.get(r).setDisabled(isLocked(recipes.get(r)));
                        boolean selected = !layer.auto && layer.selectedIndex == r;
                        checks.get(r).setDrawable(selected ? Tex.checkOnOver : Tex.checkOff);
                    }
                };

                autoBtn.clicked(() -> {
                    layer.auto = true;
                    layer.selectedIndex = -1;
                    configure(config());
                    refresh.run();
                });
                panel.add(autoBtn).size(240f, 46f).padBottom(6f).row();

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
                            if(isLocked(recipe)) return;
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
                boolean[] lastAuto = {layer.auto};
                int[] lastSelected = {layer.selectedIndex};
                panel.update(() -> {
                    if(layer.auto != lastAuto[0] || layer.selectedIndex != lastSelected[0]){
                        lastAuto[0] = layer.auto;
                        lastSelected[0] = layer.selectedIndex;
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
