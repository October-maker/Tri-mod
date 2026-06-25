package triangle.black.projection;

import mindustry.world.blocks.storage.CoreBlock;

public class TriPJCore extends CoreBlock {

    static {
        PJCoreNumber.get();
    }

    public TriPJCore(String name) {
        super(name);
    }

    public class TriPJCoreBuild extends CoreBuild {

        @Override
        public void created() {
            super.created();
            PJCoreNumber.refresh();
        }

        @Override
        public void afterDestroyed() {
            super.afterDestroyed();
            PJCoreNumber.refresh();
        }
    }
}
