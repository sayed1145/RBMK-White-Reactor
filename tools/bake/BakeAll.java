package bake;

import rbmk.gfx.*;

import java.awt.image.*;
import java.io.*;

/** Bakes every static layer / icon from the same model code the game runs. */
public class BakeAll{
    public static void main(String[] args) throws Exception{
        File sprites = new File(args.length > 0 ? args[0] : "assets/sprites");
        File preview = new File(args.length > 1 ? args[1] : "preview");
        String only = args.length > 2 ? args[2] : null;
        for(BlockModel model : Models.all()){
            if(only != null && !model.name.equals(only)) continue;
            long t0 = System.currentTimeMillis();
            Mesh stat = new Mesh(), env = new Mesh(), full = new Mesh();
            model.buildStatic(stat);
            model.buildEnvelope(env);
            model.buildStatic(full);
            model.buildRest(full);

            Baker hd = new Baker(model.cam, model.half, 8f, 3);
            hd.model = model;
            Baker.write(hd.bakeBase(stat), new File(sprites, model.name + "-hd.png"));
            BufferedImage front = hd.bakeFront(stat, env);
            Baker.write(front, new File(sprites, model.name + "-front-hd.png"));
            int frontPx = hd.frontPixels;

            Baker lo = new Baker(model.cam, model.half, 4f, 4);
            lo.model = model;
            Baker.write(lo.bakeBase(stat), new File(sprites, model.name + ".png"));
            Baker.write(lo.bakeBase(full), new File(sprites, model.name + "-preview.png"));

            Baker big = new Baker(model.cam, model.half, model.size >= 8 ? 12f : 20f, 2);
            big.model = model;
            Baker.write(big.bakeBase(full), new File(preview, model.name + "-3d.png"));
            System.out.printf("baked %-22s static %5d faces, envelope %6d faces, live-rest %5d faces, front px %d, %d ms%n",
                model.name, stat.faces, env.faces, full.faces - stat.faces, frontPx, System.currentTimeMillis() - t0);
        }
        if(only == null || only.equals("items")) ItemIcons.bake(sprites);
    }
}
