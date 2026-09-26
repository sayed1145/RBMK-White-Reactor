package bake;

import arc.graphics.Color;
import rbmk.gfx.*;

import java.io.*;

import static rbmk.gfx.Mesh.*;

/** 3D item / liquid icons rendered with the same camera, light and materials as the blocks. */
public class ItemIcons{
    static final Cam cam = new Cam(4f);

    public static void bake(File dir) throws IOException{
        write(dir, "uranium-pellets", pellets());
        write(dir, "zirconium-cladding", cladding());
        write(dir, "uranium-assembly", assembly());
        write(dir, "demineralized-water", droplet());
        System.out.println("baked item icons");
    }

    static void write(File dir, String name, Mesh m) throws IOException{
        Baker b = new Baker(cam, 4f, 8f, 6); //32 px
        Baker.write(b.bakeBase(m), new File(dir, name + ".png"));
    }

    static void pellet(Mesh m, Color c){
        m.color(c).style(0, 0).lathe(14, 0, 0, 0, 0.78f, 0, 0.9f, 0.1f, 0.9f, 1.05f, 0.78f, 1.15f, 0.45f, 1.15f, 0.4f, 1.1f, 0, 1.1f);
    }

    static Mesh pellets(){
        Mesh m = new Mesh();
        Color c = new Color(0.62f, 0.68f, 0.42f), c2 = new Color(0.55f, 0.61f, 0.37f);
        //two lying down, three standing
        m.at(-2.6f, -1.6f, 0.9f).rot(1, 90).rot(0, 10f); pellet(m, c2);
        m.at(0.2f, -2.6f, 0.9f).rot(1, 90).rot(2, 0).rot(0, -20f); pellet(m, c);
        m.at(-1.6f, 1.2f, 0); pellet(m, c);
        m.at(0.8f, 0.4f, 0); pellet(m, c2);
        m.at(2.3f, -1.3f, 0); pellet(m, c);
        m.at(1.2f, 2.5f, 0); pellet(m, c2);
        return m;
    }

    static void tube(Mesh m, float x0, float y0, float x1, float y1, float z){
        m.axis(x0, y0, z, x1, y1, z);
        float l = (float)Math.hypot(x1 - x0, y1 - y0);
        m.color(Pal.zirc).style(metal, 0).lathe(14, 0, 0.3f, 0, 0.55f, 0, 0.55f, l, 0.3f, l);
        m.color(Pal.graphite).style(0, 0).lathe(14, 0, 0, 0.02f, 0.3f, 0.02f);
        m.color(Pal.graphite).style(0, 0).lathe(14, 0, 0.3f, l - 0.02f, 0, l - 0.02f);
        m.color(new Color(0.40f, 0.62f, 0.86f)).style(0, 0).lathe(14, 0, 0.56f, l * 0.3f, 0.56f, l * 0.3f + 0.35f);
    }

    static Mesh cladding(){
        Mesh m = new Mesh();
        tube(m, -3.2f, 0.5f, 2.4f, 2.8f, 0.55f);
        tube(m, -3.0f, -1.0f, 3.0f, 1.4f, 0.55f);
        tube(m, -2.6f, -2.7f, 3.3f, -0.3f, 0.55f);
        return m;
    }

    static Mesh assembly(){
        Mesh m = new Mesh();
        float x0 = -3.0f, y0 = -2.9f, x1 = 2.7f, y1 = 2.2f, zc = 1.15f;
        float dx = x1 - x0, dy = y1 - y0, l = (float)Math.hypot(dx, dy);
        float nx = -dy / l, ny = dx / l;
        //seven elements in a hexagonal cluster
        float[][] off = {{0, 0}, {0.62f, 0}, {-0.62f, 0}, {0.31f, 0.54f}, {-0.31f, 0.54f}, {0.31f, -0.54f}, {-0.31f, -0.54f}};
        for(float[] o : off){
            float ox = nx * o[0], oy = ny * o[0], oz = o[1];
            m.axis(x0 + ox, y0 + oy, zc + oz, x1 + ox, y1 + oy, zc + oz);
            m.color(Pal.zirc).style(metal, 0).lathe(10, 0, 0, 0.4f, 0.28f, 0.4f, 0.28f, l - 0.4f, 0, l - 0.4f);
        }
        m.axis(x0, y0, zc, x1, y1, zc);
        for(int g = 0; g < 4; g++){
            float z = 1.0f + g * (l - 2.3f) / 3f;
            m.color(Pal.steel).style(metal, 0).lathe(6, 30f, 0.9f, z, 1.08f, z, 1.08f, z + 0.3f, 0.9f, z + 0.3f, 0.9f, z);
        }
        m.color(Pal.offWhite).style(metal, 0).lathe(6, 30f, 0, 0, 0.7f, 0, 0.95f, 0.25f, 0.95f, 0.5f, 0, 0.5f);
        m.color(new Color(1f, 0.70f, 0.28f)).style(0, 0).lathe(6, 30f, 0, l - 0.5f, 0.95f, l - 0.5f, 0.95f, l - 0.25f, 0.6f, l, 0, l);
        return m;
    }

    static Mesh droplet(){
        Mesh m = new Mesh();
        m.at(0, -3.4f, 1.9f).color(0.55f, 0.86f, 1f).style(glass, 0);
        //teardrop lying on its side slightly, apex pointing up/north
        m.rot(0, -90f);
        float[] prof = new float[26];
        for(int i = 0; i <= 12; i++){
            float t = i / 12f;
            float z = t * 6.4f;
            float r = t < 0.3f ? (float)Math.sqrt(Math.max(0, 1 - Math.pow((0.3f - t) / 0.3f, 2))) * 1.9f
                : 1.9f * (float)Math.pow(1f - (t - 0.3f) / 0.7f, 1.3f);
            prof[i * 2] = i == 12 ? 0 : r;
            prof[i * 2 + 1] = z;
        }
        m.lathe(20, 0, prof);
        return m;
    }
}
