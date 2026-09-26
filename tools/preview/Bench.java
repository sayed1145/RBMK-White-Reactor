package preview;

import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.world.blocks.production.*;
import rbmk.gfx.*;
import rbmk.world.*;

/** CPU cost of one draw() call per model (capture batch only records vertices; this is an upper bound for the CPU side). */
public class Bench{
    public static void main(String[] args) throws Exception{
        Capture.install(new java.io.File(args[0]));
        Vars.content = new ContentLoader();
        RbmkReactor block = new RbmkReactor("bench-reactor");
        RbmkReactor.RbmkBuild r = block.new RbmkBuild();
        for(int i = 0; i < 211; i++) r.rodActual[i] = 50 + 40 * (float)Math.sin(i);
        r.powerLevel = 1f; r.productionEfficiency = 1f;
        time("rbmk-plant", () -> r.draw());
        for(BlockModel m : Models.all()){
            if(m instanceof ReactorModel) continue;
            GenericCrafter g = new GenericCrafter("bench-" + m.name);
            GenericCrafter.GenericCrafterBuild b = g.new GenericCrafterBuild();
            b.warmup = 1f; b.progress = 0.4f; b.totalProgress = 100f;
            time(m.name, () -> m.draw(b));
        }
    }

    static void time(String name, Runnable r){
        for(int i = 0; i < 300; i++){ Time.time = i; Capture.ops.clear(); r.run(); }
        long q0 = Capture.quads; long t0 = System.nanoTime(); int n = 1000;
        for(int i = 0; i < n; i++){ Time.time = i; Capture.ops.clear(); r.run(); }
        System.out.printf("%-22s %7.3f ms/draw  %6d quads/draw%n", name, (System.nanoTime() - t0) / 1e6 / n, (Capture.quads - q0) / n);
    }
}
