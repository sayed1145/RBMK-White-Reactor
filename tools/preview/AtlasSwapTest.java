package preview;

import arc.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.world.blocks.production.*;
import rbmk.gfx.*;

import java.io.*;

/**
 * Regression test for "placed blocks render black": Mindustry calls Block.load() once against a temporary atlas
 * during mod sprite packing and again after the final atlas exists. Models must follow the final atlas.
 */
public class AtlasSwapTest{
    public static void main(String[] args) throws Exception{
        File sprites = new File(args[0]);
        Capture.install(sprites);
        Vars.content = new ContentLoader();
        for(BlockModel m : Models.all()) m.load();               //first load: temporary packing atlas
        Object temp = Core.atlas;
        Capture.install(sprites);                                 //final atlas replaces it
        if(Core.atlas == temp) throw new AssertionError("test setup: atlas not replaced");
        var f = BlockModel.class.getDeclaredField("loadedFrom");
        f.setAccessible(true);
        for(BlockModel m : Models.all()){
            GenericCrafter g = new GenericCrafter("swap-" + m.name);
            GenericCrafter.GenericCrafterBuild b = g.new GenericCrafterBuild();
            Capture.ops.clear();
            if(m instanceof ReactorModel){
                rbmk.world.RbmkReactor r = new rbmk.world.RbmkReactor("swap-reactor");
                r.new RbmkBuild().draw();
            }else m.draw(b);
            if(f.get(m) != Core.atlas) throw new AssertionError(m.name + " still uses the stale atlas");
        }
        System.out.println("atlas swap: PASS (" + Models.all().length + " models re-resolve their baked layers)");
    }
}
