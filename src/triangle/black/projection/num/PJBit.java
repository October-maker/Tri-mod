package triangle.black.projection.num;

import arc.Events;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.SaveLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.io.SaveFileReader;
import mindustry.io.SaveVersion;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Count of TriPJBit instances in the current game; persisted via custom save chunk. */
public class PJBit implements SaveFileReader.CustomChunk {
    private static final String CHUNK_NAME = "triangle-pj-bit";
    private static final short VERSION = 1;

    static int PJBitProduct = 64;

    static int PJCoreBest = 8;
    public static int getPJCoreBest() {
        return PJCoreBest;
    }

    static int updateInterval = 60;
    public static int getUpdateInterval() {
        return updateInterval;
    }

    public static final PJBit instance = new PJBit();

    public int value;

    static {
        SaveVersion.addCustomChunk(CHUNK_NAME, instance);
    }

    private PJBit() {
        Events.on(ResetEvent.class, e -> value = 0);
        Events.on(SaveLoadEvent.class, e -> refresh());
        Events.on(WorldLoadEvent.class, e -> refresh());
    }

    /** @return number of live PJBit buildings in the world. */
    public static int get() {
        return instance.value;
    }

    /** Re-count TriPJCore instances from the world. */
    public static void refresh() {
        instance.value = countTriPJBit();
    }

    private static int countTriPJBit() {
        PJCoreNumber.refresh();
        float PJBitMore = PJBitProduct * PJCoreBest + (PJCoreNumber.get() - PJCoreBest) * PJBitProduct * ((float) PJCoreBest / PJCoreNumber.get());
        if(PJCoreNumber.get() <= PJCoreBest) {
            return PJCoreNumber.get() * PJBitProduct;
        } else {
            return (int) PJBitMore;
        }
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
