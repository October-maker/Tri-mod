package triangle.black.projection.num;

import arc.Events;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.SaveLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.io.SaveFileReader;
import mindustry.io.SaveVersion;
import triangle.black.projection.TriPJCore;
import triangle.black.projection.TriPJFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Count of {@link TriPJFactory} instances in the current game; persisted via custom save chunk. */
public class PJFactoryNumber implements SaveFileReader.CustomChunk {
    private static final String CHUNK_NAME = "triangle-pj-factory-number";
    private static final short VERSION = 1;

    public static final PJFactoryNumber instance = new PJFactoryNumber();

    public int value;

    static {
        SaveVersion.addCustomChunk(CHUNK_NAME, instance);
    }

    private PJFactoryNumber() {
        Events.on(ResetEvent.class, e -> value = 0);
        Events.on(SaveLoadEvent.class, e -> refresh());
        Events.on(WorldLoadEvent.class, e -> refresh());
    }

    /** @return number of live TriPJFactory buildings in the world. */
    public static int get() {
        return instance.value;
    }

    /** Re-count TriPJFactory instances from the world. */
    public static void refresh() {
        instance.value = countTriPJFactory();
    }

    private static int countTriPJFactory() {
        int count = 0;
        for (Building build : Groups.build) {
            if (build.block instanceof TriPJFactory && build.isValid()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void write(DataOutput stream) throws IOException {
        refresh();
        stream.writeShort(VERSION);
        stream.writeInt(value);
    }

    @Override
    public void read(DataInput stream) throws IOException {
        stream.readShort();
        value = stream.readInt();
    }
}
