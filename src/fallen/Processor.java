/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  arc.graphics.Color
 *  arc.graphics.Pixmap
 *  arc.struct.ObjectMap
 *  arc.struct.Seq
 */
package fallen;

import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import fallen.Main;
import fallen.RectInt;
import java.util.Iterator;

public class Processor {
    static boolean[] used;

    public static ObjectMap<String, Seq<RectInt>> process(Pixmap pixmap) {
        Pixmap work = new Pixmap(pixmap.width, pixmap.height);
        work.draw(pixmap, 0, 0);
        if (Main.coreQuality < 255) {
            Processor.index(work);
        }
        Color tmpColor = new Color();
        for (int x = 0; x < work.width; ++x) {
            for (int y = 0; y < work.height; ++y) {
                int rgba = work.get(x, y);
                int alpha = rgba & 0xFF;
                if (alpha == 255) continue;
                tmpColor.set(rgba);
                float a = (float)alpha / 255.0f;
                if (Main.coreUseGray) {
                    float oldA = tmpColor.a;
                    tmpColor.a = 1.0f;
                    tmpColor.lerp(Color.valueOf((String)"3d3d43"), 1.0f - oldA);
                } else {
                    tmpColor.r *= a;
                    tmpColor.g *= a;
                    tmpColor.b *= a;
                }
                tmpColor.a = 1.0f;
                work.set(x, y, tmpColor.rgba8888());
            }
        }
        int w = work.width;
        int h = work.height;
        ObjectMap out = new ObjectMap();
        used = new boolean[w * h];
        for (int x = 0; x < w; ++x) {
            for (int y = 0; y < h; ++y) {
                if (used[x + y * w]) continue;
                int color = work.get(x, y);
                RectInt rect = new RectInt(x, y, 1, 1);
                Processor.expand(work, rect, color, w, h);
                String colorCmd = "draw color " + (color >> 24 & 0xFF) + " " + (color >> 16 & 0xFF) + " " + (color >> 8 & 0xFF);
                ((Seq)out.get((Object)colorCmd, Seq::new)).add((Object)rect);
            }
        }
        work.dispose();
        return out;
    }

    static void expand(Pixmap pixmap, RectInt r, int color, int w, int h) {
        int cy;
        int cx;
        int i;
        boolean canExpand;
        while (r.x + r.width < w) {
            canExpand = true;
            for (i = 0; i < r.height; ++i) {
                cx = r.x + r.width;
                cy = r.y + i;
                if (pixmap.get(cx, cy) == color && !used[cx + cy * w]) continue;
                canExpand = false;
                break;
            }
            if (!canExpand) break;
            ++r.width;
        }
        while (r.y + r.height < h) {
            canExpand = true;
            for (i = 0; i < r.width; ++i) {
                cx = r.x + i;
                cy = r.y + r.height;
                if (pixmap.get(cx, cy) == color && !used[cx + cy * w]) continue;
                canExpand = false;
                break;
            }
            if (!canExpand) break;
            ++r.height;
        }
        for (int ix = r.x; ix < r.x + r.width; ++ix) {
            for (int iy = r.y; iy < r.y + r.height; ++iy) {
                Processor.used[ix + iy * w] = true;
            }
        }
    }

    static void index(Pixmap pixmap) {
        Seq palette = new Seq();
        Seq hsvPalette = new Seq();
        int quality = (255 - Main.coreQuality) * 3;
        Color t = new Color();
        for (int x = 0; x < pixmap.width; ++x) {
            for (int y = 0; y < pixmap.height; ++y) {
                int pixel = pixmap.get(x, y);
                boolean found = false;
                if (Main.coreHsv) {
                    t.set(pixel);
                    float h = t.hue();
                    float s = t.saturation();
                    float v = t.value();
                    for (int i = 0; i < hsvPalette.size; ++i) {
                        float[] other = (float[])hsvPalette.get(i);
                        if (!(Math.abs(h - other[0]) * 360.0f + Math.abs(s - other[1]) + Math.abs(v - other[2]) < (float)quality)) continue;
                        pixmap.set(x, y, ((Integer)palette.get(i)).intValue());
                        found = true;
                        break;
                    }
                    if (found) continue;
                    palette.add((Object)pixel);
                    hsvPalette.add((Object)new float[]{h, s, v});
                    continue;
                }
                int r1 = pixel >> 24 & 0xFF;
                int g1 = pixel >> 16 & 0xFF;
                int b1 = pixel >> 8 & 0xFF;
                Iterator iterator = palette.iterator();
                while (iterator.hasNext()) {
                    int other = (Integer)iterator.next();
                    int r2 = other >> 24 & 0xFF;
                    int g2 = other >> 16 & 0xFF;
                    int b2 = other >> 8 & 0xFF;
                    if (Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2) >= quality) continue;
                    pixmap.set(x, y, other);
                    found = true;
                    break;
                }
                if (found) continue;
                palette.add((Object)pixel);
            }
        }
    }
}

