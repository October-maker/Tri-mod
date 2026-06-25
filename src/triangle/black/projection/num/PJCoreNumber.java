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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Count of {@link TriPJCore} instances in the current game; persisted via custom save chunk. */
public class PJCoreNumber implements SaveFileReader.CustomChunk {
    private static final String CHUNK_NAME = "triangle-pj-number";
    private static final short VERSION = 1;

    public static final PJCoreNumber instance = new PJCoreNumber();

    public int value;

    static {
        SaveVersion.addCustomChunk(CHUNK_NAME, instance);
    }

    private PJCoreNumber() {
        Events.on(ResetEvent.class, e -> value = 0);
        Events.on(SaveLoadEvent.class, e -> refresh());
        Events.on(WorldLoadEvent.class, e -> refresh());
    }

    /** @return number of live TriPJCore buildings in the world. */
    public static int get() {
        return instance.value;
    }

    /** Re-count TriPJCore instances from the world. */
    public static void refresh() {
        instance.value = countTriPJCores();
    }

    private static int countTriPJCores() {
        int count = 0;
        for (Building build : Groups.build) {
            if (build.block instanceof TriPJCore && build.isValid()) {
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
