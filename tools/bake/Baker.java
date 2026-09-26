package bake;

import rbmk.gfx.*;

import javax.imageio.*;
import java.awt.image.*;
import java.io.*;

/**
 * Offline renderer for the static half of every model. Uses exactly the same {@link Cam}, {@link Light} and
 * {@link Mesh} as the game, plus what a sprite batch cannot do cheaply at runtime: a perspective-correct
 * z-buffer, 3x/4x supersampling, height-based ambient occlusion, depth-edge outlines, rim highlights and
 * procedural materials (plates, rivets, concrete, grates, bolts, glass, water).
 */
public class Baker{
    final Cam cam;
    final float half;
    final int W, H, ss;
    final float k;
    final float[] zbuf, env;
    final int[] fid;
    Mesh mesh;
    float[] fnx, fny, fnz;
    BlockModel model;

    public Baker(Cam cam, float half, float ppu, int ss){
        this.cam = cam;
        this.half = half;
        this.ss = ss;
        this.k = ppu * ss;
        this.W = this.H = Math.round(half * 2f * ppu * ss);
        zbuf = new float[W * H];
        env = new float[W * H];
        fid = new int[W * H];
    }

    // --------------------------------------------------------------- raster

    float px(float x, float z){ return (cam.sx(x, z) + half) * k; }
    float py(float y, float z){ return (half - cam.sy(y, z)) * k; }

    void clear(){
        java.util.Arrays.fill(zbuf, -1e9f);
        java.util.Arrays.fill(env, -1e9f);
        java.util.Arrays.fill(fid, -1);
    }

    void normals(Mesh m){
        fnx = new float[m.faces]; fny = new float[m.faces]; fnz = new float[m.faces];
        for(int f = 0; f < m.faces; f++){
            int[] q = {m.f0[f], m.f1[f], m.f2[f], m.f3[f]};
            float nx = 0, ny = 0, nz = 0;
            for(int i = 0; i < 4; i++){
                int p = q[i], n = q[(i + 1) & 3];
                nx += (m.vy[p] - m.vy[n]) * (m.vz[p] + m.vz[n]);
                ny += (m.vz[p] - m.vz[n]) * (m.vx[p] + m.vx[n]);
                nz += (m.vx[p] - m.vx[n]) * (m.vy[p] + m.vy[n]);
            }
            float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if(l < 1e-9f) l = 1;
            fnx[f] = nx / l; fny[f] = ny / l; fnz[f] = nz / l;
        }
    }

    /** Rasterises the mesh; envelope=true writes the max-z envelope buffer instead of the colour z-buffer. */
    void raster(Mesh m, boolean envelope){
        if(!envelope){ mesh = m; normals(m); }
        for(int f = 0; f < m.faces; f++){
            int a = m.f0[f], b = m.f1[f], c = m.f2[f], d = m.f3[f];
            if(!envelope){
                //exact back-face culling with the real camera
                if(!cam.facing(fnx[f], fny[f], fnz[f], m.vx[a], m.vy[a], m.vz[a])) continue;
            }
            tri(m, a, b, c, f, envelope);
            if(c != d) tri(m, a, c, d, f, envelope);
        }
    }

    void tri(Mesh m, int a, int b, int c, int f, boolean envelope){
        float x0 = px(m.vx[a], m.vz[a]), y0 = py(m.vy[a], m.vz[a]);
        float x1 = px(m.vx[b], m.vz[b]), y1 = py(m.vy[b], m.vz[b]);
        float x2 = px(m.vx[c], m.vz[c]), y2 = py(m.vy[c], m.vz[c]);
        float q0 = 1f / (cam.D - m.vz[a]), q1 = 1f / (cam.D - m.vz[b]), q2 = 1f / (cam.D - m.vz[c]);
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if(Math.abs(area) < 1e-6f) return;
        int minX = Math.max(0, (int)Math.floor(Math.min(x0, Math.min(x1, x2)))), maxX = Math.min(W - 1, (int)Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int)Math.floor(Math.min(y0, Math.min(y1, y2)))), maxY = Math.min(H - 1, (int)Math.ceil(Math.max(y0, Math.max(y1, y2))));
        float inv = 1f / area;
        for(int y = minY; y <= maxY; y++){
            float cy = y + 0.5f;
            for(int x = minX; x <= maxX; x++){
                float cx = x + 0.5f;
                float w0 = ((x1 - cx) * (y2 - cy) - (x2 - cx) * (y1 - cy)) * inv;
                float w1 = ((x2 - cx) * (y0 - cy) - (x0 - cx) * (y2 - cy)) * inv;
                float w2 = 1f - w0 - w1;
                if(w0 < -1e-5f || w1 < -1e-5f || w2 < -1e-5f) continue;
                float q = w0 * q0 + w1 * q1 + w2 * q2;
                float z = cam.D - 1f / q;
                int i = y * W + x;
                if(envelope){
                    if(z > env[i]) env[i] = z;
                }else if(z > zbuf[i]){
                    zbuf[i] = z;
                    fid[i] = f;
                }
            }
        }
    }

    // --------------------------------------------------------------- shading

    static float hash(int x, int y){
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535f;
    }

    static float noise(float x, float y){
        int ix = (int)Math.floor(x), iy = (int)Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        float a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    static float seam(float t, float period){
        float d = Math.abs(t - Math.round(t / period) * period);
        return d;
    }

    /** Shades pixel i into rgb (0..1+). Returns false if empty. */
    boolean shade(int i, float[] rgb){
        int f = fid[i];
        if(f < 0) return false;
        Mesh m = mesh;
        float z = zbuf[i];
        int X = i % W, Y = i / W;
        float sx = (X + 0.5f) / k - half, sy = half - (Y + 0.5f) / k;
        float s = cam.scale(z);
        float x = sx / s, y = cam.cy + (sy - cam.cy) / s;
        float nx = fnx[f], ny = fny[f], nz = fnz[f];
        int fl = m.flags[f], mat = m.mat[f];
        float r = m.cr[f], g = m.cg[f], b = m.cb[f];
        if((fl & Mesh.emissive) != 0){
            rgb[0] = r; rgb[1] = g; rgb[2] = b;
            return true;
        }
        //tangent coordinate for side faces
        float t = Math.abs(nx) > Math.abs(ny) ? y : x;
        boolean top = nz > 0.7f;
        float mul = 1f, metalK = (fl & Mesh.metal) != 0 ? 1f : 0f;
        switch(mat){
            case Mesh.matPlate -> {
                if(top){
                    float d = Math.min(seam(x, 4f), seam(y, 4f));
                    if(d < 0.06f) mul *= 0.78f; else if(d < 0.12f) mul *= 1.05f;
                    float tone = hash((int)Math.floor(x / 4f), (int)Math.floor(y / 4f));
                    mul *= 0.975f + tone * 0.05f;
                    float rx = seam(x - 0.45f, 4f), ry = seam(y - 0.45f, 4f), rx2 = seam(x + 0.45f, 4f), ry2 = seam(y + 0.45f, 4f);
                    float rd = Math.min(Math.min((float)Math.hypot(rx, ry), (float)Math.hypot(rx2, ry)), Math.min((float)Math.hypot(rx, ry2), (float)Math.hypot(rx2, ry2)));
                    if(rd < 0.11f) mul *= 0.72f; else if(rd < 0.16f) mul *= 1.08f;
                }else{
                    if(seam(t, 4f) < 0.05f) mul *= 0.82f;
                }
            }
            case Mesh.matConcrete -> {
                mul *= 0.93f + noise(x * 2.3f, y * 2.3f + z) * 0.1f + (hash((int)(x * 9), (int)(y * 9)) - 0.5f) * 0.04f;
                if(top && Math.min(seam(x, 8f), seam(y, 8f)) < 0.07f) mul *= 0.82f;
            }
            case Mesh.matGrate -> {
                float u = top ? x : t;
                if(((u * 2.5f) % 1f + 1f) % 1f < 0.38f) mul *= 0.62f;
            }
            case Mesh.matFins -> {
                float u = top ? y : z;
                if(((u * 3.2f) % 1f + 1f) % 1f < 0.42f) mul *= 0.70f;
            }
            case Mesh.matHazard -> {
                boolean yellow = ((((x + y) * 0.9f) % 1f) + 1f) % 1f < 0.5f;
                if(yellow){ r = 0.95f; g = 0.76f; b = 0.18f; }else{ r = 0.16f; g = 0.16f; b = 0.16f; }
            }
            case Mesh.matRubber -> mul *= 0.92f + noise(x * 5f, y * 5f) * 0.12f;
            case Mesh.matGlass -> {
                float streak = (((x * 0.7f - y * 0.7f + z) * 0.8f) % 1f + 1f) % 1f;
                mul *= 0.9f + (streak < 0.15f ? 0.35f : 0f);
                metalK = 1.4f;
            }
            case Mesh.matWater -> {
                float rr = (float)Math.hypot(x - (model == null ? 0 : model.waterCx), y - (model == null ? 0 : model.waterCy));
                mul *= 0.92f + 0.08f * (float)Math.sin(rr * 3.1f + noise(x, y) * 3f);
                metalK = 1.2f;
            }
            default -> {}
        }
        float light = Light.diffuse(nx, ny, nz);
        float sp = Light.spec(nx, ny, nz, metalK);
        rgb[0] = r * mul * light + sp;
        rgb[1] = g * mul * light + sp;
        rgb[2] = b * mul * light + sp;
        if(model != null) model.pattern(mat, x, y, z, nz, rgb);
        //micro surface noise so large panels never look like flat vector fills
        float mn = 1f + (hash((int)(x * 16f), (int)(y * 16f + z * 16f)) - 0.5f) * 0.03f;
        rgb[0] *= mn; rgb[1] *= mn; rgb[2] *= mn;
        return true;
    }


    /** Full shaded frame at supersampled resolution. mask selects which pixels are kept (null = all). */
    float[] render(boolean[] mask){
        float[] col = new float[W * H * 4];
        float[] rgb = new float[3];
        for(int i = 0; i < W * H; i++){
            if(mask != null && !mask[i]) continue;
            if(!shade(i, rgb)) continue;
            col[i * 4] = rgb[0]; col[i * 4 + 1] = rgb[1]; col[i * 4 + 2] = rgb[2]; col[i * 4 + 3] = 1f;
        }
        //height based ambient occlusion + edge outline / rim light
        float aoR = 1.35f * k / ss * ss; //in supersampled pixels ~1.35 world units * ppu
        int samples = 16;
        float[] ox = new float[samples], oy = new float[samples];
        for(int n = 0; n < samples; n++){
            float a = (float)(n * 2.39996323), rr = (float)Math.sqrt((n + 0.5f) / samples) * aoR;
            ox[n] = (float)Math.cos(a) * rr; oy[n] = (float)Math.sin(a) * rr;
        }
        float[] out = new float[W * H * 4];
        System.arraycopy(col, 0, out, 0, col.length);
        for(int Y = 0; Y < H; Y++){
            for(int X = 0; X < W; X++){
                int i = Y * W + X;
                if(col[i * 4 + 3] <= 0f) continue;
                int f = fid[i];
                if((mesh.flags[f] & Mesh.emissive) != 0) continue;
                float z = zbuf[i];
                float occ = 0f;
                for(int n = 0; n < samples; n++){
                    int sx2 = X + (int)ox[n], sy2 = Y + (int)oy[n];
                    if(sx2 < 0 || sy2 < 0 || sx2 >= W || sy2 >= H) continue;
                    int j = sy2 * W + sx2;
                    if(fid[j] < 0) continue;
                    float dz = zbuf[j] - z - 0.08f;
                    if(dz > 0f) occ += Math.min(dz / 1.6f, 1f);
                }
                float ao = 1f - 0.42f * occ / samples;
                //depth edges, 1 output pixel wide
                float edge = 1f;
                int[] dx = {ss, -ss, 0, 0}, dy = {0, 0, ss, -ss};
                for(int e = 0; e < 4; e++){
                    int ex = X + dx[e], ey = Y + dy[e];
                    if(ex < 0 || ey < 0 || ex >= W || ey >= H) continue;
                    int j = ey * W + ex;
                    if(fid[j] < 0 || fid[j] == f) continue;
                    float dz = zbuf[j] - z;
                    if(dz > 0.30f) edge = Math.min(edge, 0.70f);
                    else if(dz < -0.30f && fnz[f] > 0.5f) edge = Math.max(edge, 1.10f);
                }
                float mlt = ao * edge;
                out[i * 4] *= mlt; out[i * 4 + 1] *= mlt; out[i * 4 + 2] *= mlt;
            }
        }
        return out;
    }

    BufferedImage downsample(float[] col){
        int w = W / ss, h = H / ss;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for(int y = 0; y < h; y++){
            for(int x = 0; x < w; x++){
                float r = 0, g = 0, b = 0, a = 0;
                for(int j = 0; j < ss; j++){
                    for(int i = 0; i < ss; i++){
                        int p = ((y * ss + j) * W + (x * ss + i)) * 4;
                        float al = col[p + 3];
                        r += col[p] * al; g += col[p + 1] * al; b += col[p + 2] * al; a += al;
                    }
                }
                if(a <= 0f){ img.setRGB(x, y, 0); continue; }
                float n = ss * ss;
                int A = clamp(a / n), R = clamp(r / a), G = clamp(g / a), B = clamp(b / a);
                img.setRGB(x, y, (A << 24) | (R << 16) | (G << 8) | B);
            }
        }
        return img;
    }

    static int clamp(float v){
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    // --------------------------------------------------------------- api

    /** Static base layer. */
    public BufferedImage bakeBase(Mesh stat){
        clear();
        raster(stat, false);
        return downsample(render(null));
    }

    /** Static pixels that lie strictly in front of every live part's swept envelope. */
    public BufferedImage bakeFront(Mesh stat, Mesh envelope){
        clear();
        raster(stat, false);
        raster(envelope, true);
        boolean[] mask = new boolean[W * H];
        int count = 0;
        for(int i = 0; i < W * H; i++){
            if(fid[i] >= 0 && env[i] > -1e8f && zbuf[i] > env[i] + 0.02f){ mask[i] = true; count++; }
        }
        frontPixels = count;
        return downsample(render(mask));
    }

    public int frontPixels;

    public static void write(BufferedImage img, File f) throws IOException{
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
