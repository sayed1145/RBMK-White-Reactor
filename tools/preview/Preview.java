package preview;

import arc.graphics.g2d.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import rbmk.gfx.*;
import rbmk.world.*;

import javax.imageio.*;
import java.io.*;

/** Drives the real draw code of every model through the capture batch and writes PNG frames. */
public class Preview{
    public static void main(String[] args) throws Exception{
        File sprites = new File(args[0]), out = new File(args[1]);
        String which = args.length > 2 ? args[2] : "rbmk-plant";
        int frames = args.length > 3 ? Integer.parseInt(args[3]) : 48;
        float scale = args.length > 4 ? Float.parseFloat(args[4]) : 8f;
        out.mkdirs();
        Capture.install(sprites);
        Vars.content = new ContentLoader();
        if(which.equals("rbmk-plant")) reactor(out, frames, scale);
        else Factories.run(which, out, frames, scale);
        System.out.println("quads=" + Capture.quads + " regions=" + Capture.regions + " shader=" + Capture.shaderUsed);
        if(Capture.shaderUsed || Capture.badVerts > 0) throw new AssertionError("draw validation failed");
    }

    static void reactor(File out, int frames, float scale) throws Exception{
        RbmkReactor block = new RbmkReactor("preview-reactor");
        RbmkReactor.RbmkBuild b = block.new RbmkBuild();
        b.x = 0; b.y = 0;
        for(int f = 0; f < frames; f++){
            float u = f / (float)frames;
            Time.time = f * 3f;
            //start-up, individual rod manoeuvres, then AZ-5 in the last quarter
            b.powerLevel = Math.min(1.6f, 0.2f + u * 3f);
            b.voidFraction = 0.2f;
            b.productionEfficiency = b.powerLevel;
            b.drumLevel = 0.5f + 0.1f * (float)Math.sin(u * 6.28f);
            for(int r = 0; r < 211; r++){
                float target = u < 0.75f ? 45f + 40f * (float)Math.sin(u * 9f + r * 0.37f) : Math.max(0f, 60f - (u - 0.75f) * 800f);
                b.rodActual[r] = Math.max(0, Math.min(100, target));
            }
            b.scrammed = u >= 0.75f;
            for(int i = 0; i < 8; i++){ b.pumpSet[i] = i == 5 ? 30 : 100; b.pumpPhase[i] = (f * 15f * b.pumpSet[i] / 100f) % 360f; }
            b.turbinePhase = (f * 21f) % 360f;
            Capture.ops.clear();
            b.draw();
            ImageIO.write(Capture.render(0, 0, 33, 33, scale, 0x2a2e33), "png", new File(out, String.format("f%03d.png", f)));
        }
    }
}
