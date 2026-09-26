package preview;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;

import javax.imageio.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

/**
 * Fake Arc batch + atlas: runs the REAL mod draw code (Draw.rect, Fill.quad, Draw.color) without a GPU,
 * validates every vertex (finite, stride 24, no custom shader) and rasterises what the GPU would receive.
 */
public class Capture{
    public static final ArrayList<Object[]> ops = new ArrayList<>();
    public static long quads, regions, badVerts;
    public static boolean shaderUsed;
    static final HashMap<String, BufferedImage> images = new HashMap<>();
    static File spriteDir;

    public static void install(File sprites) throws Exception{
        spriteDir = sprites;
        Texture fake = fakeTexture();
        Core.atlas = new TextureAtlas(){
            final HashMap<String, AtlasRegion> cache = new HashMap<>();
            @Override public AtlasRegion white(){ return find("white-fake"); }
            @Override public AtlasRegion find(String name){
                return cache.computeIfAbsent(name, n -> {
                    AtlasRegion r = new AtlasRegion(fake, 0, 0, 8, 8);
                    r.name = n;
                    return r;
                });
            }
            @Override public AtlasRegion find(String name, TextureRegion def){ return has(name) ? find(name) : (AtlasRegion)def; }
            @Override public AtlasRegion find(String name, String def){ return has(name) ? find(name) : find(def); }
            @Override public boolean has(String name){ return file(name).exists() || name.equals("white-fake"); }
        };
        Core.batch = new CaptureBatch();
    }

    static File file(String name){
        String n = name.startsWith("rbmk-white-reactor-") ? name.substring("rbmk-white-reactor-".length()) : name;
        return new File(spriteDir, n + ".png");
    }

    static BufferedImage image(String name){
        return images.computeIfAbsent(name, n -> {
            try{ return ImageIO.read(file(n)); }catch(Exception e){ return null; }
        });
    }

    static Texture fakeTexture() throws Exception{
        java.lang.reflect.Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe)theUnsafe.get(null);
        Texture tex = (Texture)unsafe.allocateInstance(Texture.class);
        java.lang.reflect.Field handle = Class.forName("arc.graphics.GLTexture").getDeclaredField("glHandle");
        handle.setAccessible(true);
        handle.setInt(tex, 1);
        tex.width = tex.height = 8;
        return tex;
    }

    /** Renders captured ops: world rect (cx,cy,±hw) at scale px per world unit. */
    public static BufferedImage render(float cx, float cy, float hw, float hh, float scale, int bg){
        int w = Math.round(hw * 2 * scale), h = Math.round(hh * 2 * scale);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(new java.awt.Color(bg));
        g.fillRect(0, 0, w, h);
        float ox = w / 2f - cx * scale, oy = h / 2f + cy * scale;
        for(Object[] op : ops){
            if(op[0] instanceof String name){
                float[] r = (float[])op[1];
                BufferedImage src = image(name);
                if(src == null) continue;
                int x0 = Math.round(ox + r[0] * scale), y0 = Math.round(oy - (r[1] + r[3]) * scale);
                g.drawImage(src, x0, y0, Math.round(r[2] * scale), Math.round(r[3] * scale), null);
            }else{
                float[] v = (float[])op[0];
                Path2D.Float p = new Path2D.Float();
                float rr = 0, gg = 0, bb = 0, aa = 0;
                for(int i = 0; i < 4; i++){
                    float x = v[i * 6], y = v[i * 6 + 1];
                    if(i == 0) p.moveTo(ox + x * scale, oy - y * scale); else p.lineTo(ox + x * scale, oy - y * scale);
                    java.awt.Color c = unpack(v[i * 6 + 2]);
                    rr += c.getRed(); gg += c.getGreen(); bb += c.getBlue(); aa += c.getAlpha();
                }
                p.closePath();
                //vertex colours: approximate gourard with a gradient between the first and third vertex
                java.awt.Color c0 = unpack(v[2]), c2 = unpack(v[14]);
                if(c0.equals(c2)){
                    g.setPaint(new java.awt.Color((int)(rr / 4), (int)(gg / 4), (int)(bb / 4), (int)(aa / 4)));
                }else{
                    java.awt.Color c1 = unpack(v[8]), c3 = unpack(v[20]);
                    float mx0 = (v[0] + v[6]) / 2, my0 = (v[1] + v[7]) / 2, mx1 = (v[12] + v[18]) / 2, my1 = (v[13] + v[19]) / 2;
                    java.awt.Color a = avg(c0, c1), b = avg(c2, c3);
                    if(Math.hypot(mx1 - mx0, my1 - my0) < 1e-3){ g.setPaint(a); }
                    else g.setPaint(new GradientPaint(ox + mx0 * scale, oy - my0 * scale, a, ox + mx1 * scale, oy - my1 * scale, b));
                }
                g.fill(p);
            }
        }
        g.dispose();
        return img;
    }

    static java.awt.Color avg(java.awt.Color a, java.awt.Color b){
        return new java.awt.Color((a.getRed() + b.getRed()) / 2, (a.getGreen() + b.getGreen()) / 2, (a.getBlue() + b.getBlue()) / 2, (a.getAlpha() + b.getAlpha()) / 2);
    }

    static java.awt.Color unpack(float packed){
        int abgr = Float.floatToRawIntBits(packed);
        int a = (abgr >>> 24) & 0xff, b = (abgr >>> 16) & 0xff, g = (abgr >>> 8) & 0xff, r = abgr & 0xff;
        return new java.awt.Color(r, g, b, Math.min(255, (int)(a * 255f / 254f)));
    }

    static class CaptureBatch extends Batch{
        @Override
        protected void draw(Texture texture, float[] v, int offset, int count){
            if(count % 24 != 0) throw new AssertionError("bad vertex stride: " + count);
            for(int i = offset; i < offset + count; i++){
                if(!Float.isFinite(v[i])){ badVerts++; throw new AssertionError("non-finite vertex " + v[i]); }
            }
            for(int q = 0; q < count / 24; q++){
                float[] copy = new float[24];
                System.arraycopy(v, offset + q * 24, copy, 0, 24);
                ops.add(new Object[]{copy});
                quads++;
            }
        }

        @Override
        protected void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float rotation){
            if(!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)) throw new AssertionError("NaN sprite");
            String name = region instanceof TextureAtlas.AtlasRegion a ? a.name : "?";
            ops.add(new Object[]{name, new float[]{x, y, width, height}});
            regions++;
        }

        @Override protected void setShader(arc.graphics.gl.Shader shader, boolean apply){ if(shader != null) shaderUsed = true; }
        @Override protected void flush(){}
        @Override public void dispose(){}
    }
}
