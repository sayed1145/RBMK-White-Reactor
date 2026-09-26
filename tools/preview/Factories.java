package preview;

import arc.util.*;
import mindustry.world.blocks.production.*;
import rbmk.gfx.*;

import javax.imageio.*;
import java.io.*;

/** Factory preview scenario: warm-up from idle, then steady production; the real draw code is captured per frame. */
public class Factories{
    public static void run(String which, File out, int frames, float scale) throws Exception{
        BlockModel model = null;
        for(BlockModel m : Models.all()) if(m.name.equals(which)) model = m;
        if(model == null) throw new IllegalArgumentException("unknown model " + which);
        GenericCrafter block = new GenericCrafter("preview-" + which);
        GenericCrafter.GenericCrafterBuild b = block.new GenericCrafterBuild();
        b.x = 0; b.y = 0;
        boolean assembly = which.equals("fuel-assembly-plant");
        float total = 0f;
        for(int f = 0; f < frames; f++){
            float u = f / (float)frames;
            Time.time = f * 3f;
            b.warmup = Math.min(1f, u * 5f);
            total += b.warmup * 3f;
            b.totalProgress = total;
            b.progress = assembly ? 0.3f + u * (2f / 18f) : u;
            Capture.ops.clear();
            model.draw(b);
            float hw = model.half + 1f;
            ImageIO.write(Capture.render(0, 0, hw, hw, scale, 0x2a2e33), "png", new File(out, String.format("f%03d.png", f)));
        }
    }
}
