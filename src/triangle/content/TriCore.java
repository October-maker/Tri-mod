package triangle.content;

import triangle.black.TriMoreCore;
import triangle.black.projection.TriPJCore;
import triangle.black.projection.num.PJCoreNumber;

public class TriCore {
    public static TriMoreCore watchtowerCore;
    public static TriPJCore PJSmallCore;
    public static void load(){
        watchtowerCore = new TriMoreCore("watchtowerCore"){{}};
        PJSmallCore = new TriPJCore("PJSmallCore"){{}};
    }
}
