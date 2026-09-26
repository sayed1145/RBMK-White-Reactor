package rbmk;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import rbmk.content.*;

public class RbmkMod extends Mod{
    public RbmkMod(){
        Log.info("[rbmk-white-reactor] constructor loaded");
        Events.on(ClientLoadEvent.class, e -> Log.info("[rbmk-white-reactor] software 3D renderer ready"));
    }
    @Override public void loadContent(){
        RbmkItems.load();
        RbmkLiquids.load();
        RbmkBlocks.load();
        RbmkTech.load();
        Log.info("[rbmk-white-reactor] content loaded for build 160+");
    }
}
