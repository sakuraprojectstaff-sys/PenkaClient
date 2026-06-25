package fun.rich.features.impl.misc;

import com.mojang.blaze3d.systems.RenderCall;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

public final class CapeTextureCache {

    static final Map<Integer, Entry> map = new ConcurrentHashMap<>();
    static final ConcurrentLinkedQueue<UploadJob> uploads = new ConcurrentLinkedQueue<>();

    static final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CapeGifLoader");
        t.setDaemon(true);
        return t;
    });

    static volatile long lastMs = 0L;

    public static Identifier resolveCape(int idx, Identifier fallback) {
        if (idx < 1 || idx > 20) return fallback;

        Entry e = map.get(idx);
        if (e == null) {
            Identifier src = Identifier.of("minecraft", "textures/cape/cape" + idx + ".gif");
            Identifier base = Identifier.of("mre", "dyn/cape" + idx);
            e = new Entry(idx, src, base);
            map.put(idx, e);
        }

        if (!e.requested) requestLoad(e);

        Identifier cur = e.currentId;
        return cur != null ? cur : fallback;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        pumpUploads(mc);

        long now = System.currentTimeMillis();
        long prev = lastMs;
        lastMs = now;
        if (prev == 0L) return;

        long dt = now - prev;
        if (dt <= 0L) return;
        if (dt > 200L) dt = 50L;

        for (Entry e : map.values()) {
            if (!e.ready) continue;
            e.advance(dt);
        }
    }

    static void pumpUploads(MinecraftClient mc) {
        int budget = 2;
        while (budget-- > 0) {
            UploadJob j = uploads.poll();
            if (j == null) break;

            Entry e = j.entry;
            if (e == null) continue;
            if (e.uploaded != null && j.frame >= 0 && j.frame < e.uploaded.length) {
                if (e.uploaded[j.frame]) continue;
            }

            NativeImageBackedTexture tex = new NativeImageBackedTexture(j.image);
            mc.getTextureManager().registerTexture(j.id, tex);
            runOnRender(tex::upload);

            if (e.uploaded != null && j.frame >= 0 && j.frame < e.uploaded.length) {
                e.uploaded[j.frame] = true;
            }
            e.uploadedCount++;

            if (!e.ready && e.frameIds != null && e.uploadedCount >= e.frameIds.length) {
                e.ready = true;
                e.idx = 0;
                e.acc = 0L;
                e.currentId = e.frameIds[0];
            }
        }
    }

    static void requestLoad(Entry e) {
        synchronized (e) {
            if (e.requested) return;
            e.requested = true;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        mc.execute(() -> {
            byte[] bytes = readResourceBytes(mc, e.src);
            if (bytes == null || bytes.length == 0) return;

            exec.submit(() -> {
                Decoded d = decodeGif(bytes);
                if (d == null || d.frames == null || d.frames.length == 0) return;

                Identifier[] ids = new Identifier[d.frames.length];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = Identifier.of("mre", "dyn/cape" + e.num + "/f" + i);
                }

                e.frameIds = ids;
                e.delays = d.delays;
                e.uploaded = new boolean[ids.length];
                e.uploadedCount = 0;
                e.ready = false;
                e.currentId = null;

                for (int i = 0; i < d.frames.length; i++) {
                    uploads.add(new UploadJob(e, i, ids[i], d.frames[i]));
                }
            });
        });
    }

    static byte[] readResourceBytes(MinecraftClient mc, Identifier id) {
        try {
            Resource r = mc.getResourceManager().getResource(id).orElse(null);
            if (r == null) return null;
            try (InputStream in = r.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Decoded decodeGif(byte[] data) {
        try (ByteArrayInputStream bin = new ByteArrayInputStream(data);
             ImageInputStream iis = ImageIO.createImageInputStream(bin)) {

            ImageReader reader = pickGifReader();
            if (reader == null) return null;

            reader.setInput(iis, false, false);

            int count;
            try {
                count = reader.getNumImages(true);
            } catch (Throwable ignored) {
                count = 1;
            }
            if (count <= 0) count = 1;
            if (count > 512) count = 512;

            int cw = readCanvasW(reader);
            int ch = readCanvasH(reader);
            if (cw <= 0 || ch <= 0) {
                try {
                    cw = reader.getWidth(0);
                    ch = reader.getHeight(0);
                } catch (Throwable ignored) {
                }
            }
            if (cw <= 0 || ch <= 0) return null;

            BufferedImage canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setComposite(AlphaComposite.SrcOver);

            NativeImage[] frames = new NativeImage[count];
            int[] delays = new int[count];

            BufferedImage restore = null;
            int prevX = 0, prevY = 0, prevW = 0, prevH = 0;
            String prevDisp = "none";

            for (int i = 0; i < count; i++) {
                if ("restoreToBackgroundColor".equals(prevDisp)) {
                    clearRect(canvas, prevX, prevY, prevW, prevH);
                } else if ("restoreToPrevious".equals(prevDisp) && restore != null) {
                    canvas = copyImage(restore);
                    g.dispose();
                    g = canvas.createGraphics();
                    g.setComposite(AlphaComposite.SrcOver);
                }

                IIOMetadata meta = reader.getImageMetadata(i);

                int delay = readDelayMs(meta);
                if (delay <= 0) delay = 50;
                if (delay > 2000) delay = 2000;

                String disp = readDisposal(meta);
                int[] pos = readFramePos(meta);
                int x = pos[0], y = pos[1];

                if ("restoreToPrevious".equals(disp)) restore = copyImage(canvas);
                else restore = null;

                BufferedImage frame = reader.read(i);
                if (frame == null) return null;

                g.drawImage(frame, x, y, null);

                BufferedImage full = copyImage(canvas);
                NativeImage ni = toNative(full);
                if (ni == null) return null;

                frames[i] = ni;
                delays[i] = delay;

                prevDisp = disp;
                prevX = x;
                prevY = y;
                prevW = frame.getWidth();
                prevH = frame.getHeight();
            }

            g.dispose();
            return new Decoded(frames, delays);

        } catch (Throwable ignored) {
        }

        return null;
    }

    static ImageReader pickGifReader() {
        try {
            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
            if (it != null && it.hasNext()) return it.next();
        } catch (Throwable ignored) {
        }
        return null;
    }

    static int readCanvasW(ImageReader r) {
        try {
            IIOMetadata m = r.getStreamMetadata();
            if (m == null) return -1;
            Node root = m.getAsTree("javax_imageio_gif_stream_1.0");
            Node lsd = findNode(root, "LogicalScreenDescriptor");
            if (lsd == null) return -1;
            NamedNodeMap at = lsd.getAttributes();
            if (at == null) return -1;
            Node v = at.getNamedItem("logicalScreenWidth");
            if (v == null) return -1;
            return Integer.parseInt(v.getNodeValue());
        } catch (Throwable ignored) {
        }
        return -1;
    }

    static int readCanvasH(ImageReader r) {
        try {
            IIOMetadata m = r.getStreamMetadata();
            if (m == null) return -1;
            Node root = m.getAsTree("javax_imageio_gif_stream_1.0");
            Node lsd = findNode(root, "LogicalScreenDescriptor");
            if (lsd == null) return -1;
            NamedNodeMap at = lsd.getAttributes();
            if (at == null) return -1;
            Node v = at.getNamedItem("logicalScreenHeight");
            if (v == null) return -1;
            return Integer.parseInt(v.getNodeValue());
        } catch (Throwable ignored) {
        }
        return -1;
    }

    static int readDelayMs(IIOMetadata meta) {
        if (meta == null) return 50;
        try {
            Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
            Node gce = findNode(root, "GraphicControlExtension");
            if (gce == null) return 50;
            NamedNodeMap at = gce.getAttributes();
            if (at == null) return 50;
            Node d = at.getNamedItem("delayTime");
            if (d == null) return 50;
            String v = d.getNodeValue();
            if (v == null || v.isEmpty()) return 50;
            int hundredths = Integer.parseInt(v.trim());
            int ms = hundredths * 10;
            return ms <= 0 ? 50 : ms;
        } catch (Throwable ignored) {
        }
        return 50;
    }

    static String readDisposal(IIOMetadata meta) {
        if (meta == null) return "none";
        try {
            Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
            Node gce = findNode(root, "GraphicControlExtension");
            if (gce == null) return "none";
            NamedNodeMap at = gce.getAttributes();
            if (at == null) return "none";
            Node d = at.getNamedItem("disposalMethod");
            if (d == null) return "none";
            String v = d.getNodeValue();
            return v == null ? "none" : v;
        } catch (Throwable ignored) {
        }
        return "none";
    }

    static int[] readFramePos(IIOMetadata meta) {
        int[] out = new int[]{0, 0};
        if (meta == null) return out;
        try {
            Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
            Node id = findNode(root, "ImageDescriptor");
            if (id == null) return out;
            NamedNodeMap at = id.getAttributes();
            if (at == null) return out;
            Node lx = at.getNamedItem("imageLeftPosition");
            Node ty = at.getNamedItem("imageTopPosition");
            if (lx != null) out[0] = Integer.parseInt(lx.getNodeValue());
            if (ty != null) out[1] = Integer.parseInt(ty.getNodeValue());
        } catch (Throwable ignored) {
        }
        return out;
    }

    static Node findNode(Node n, String name) {
        if (n == null || name == null) return null;
        if (name.equals(n.getNodeName())) return n;
        Node c = n.getFirstChild();
        while (c != null) {
            Node r = findNode(c, name);
            if (r != null) return r;
            c = c.getNextSibling();
        }
        return null;
    }

    static void clearRect(BufferedImage img, int x, int y, int w, int h) {
        if (img == null) return;
        if (w <= 0 || h <= 0) return;
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(x, y, w, h);
        g.dispose();
    }

    static BufferedImage copyImage(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    static NativeImage toNative(BufferedImage bi) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
            if (!ImageIO.write(bi, "png", bos)) return null;
            byte[] png = bos.toByteArray();
            ByteArrayInputStream bin = new ByteArrayInputStream(png);

            Method m = findNativeReadOneArg();
            if (m != null) {
                m.setAccessible(true);
                Object r = m.invoke(null, bin);
                if (r instanceof NativeImage) return (NativeImage) r;
            }

            Method m2 = findNativeReadTwoArgs();
            if (m2 != null) {
                m2.setAccessible(true);
                Class<?> fmt = m2.getParameterTypes()[0];
                Object fmtVal = pickAnyEnum(fmt);
                Object r = m2.invoke(null, fmtVal, bin);
                if (r instanceof NativeImage) return (NativeImage) r;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Method findNativeReadOneArg() {
        try {
            Method m = NativeImage.class.getMethod("read", InputStream.class);
            if (m.getReturnType() == NativeImage.class) return m;
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : NativeImage.class.getDeclaredMethods()) {
                if (!m.getName().equals("read")) continue;
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] != InputStream.class) continue;
                if (m.getReturnType() == NativeImage.class) return m;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Method findNativeReadTwoArgs() {
        try {
            for (Method m : NativeImage.class.getMethods()) {
                if (!m.getName().equals("read")) continue;
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 2) continue;
                if (m.getParameterTypes()[1] != InputStream.class) continue;
                if (m.getReturnType() == NativeImage.class) return m;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : NativeImage.class.getDeclaredMethods()) {
                if (!m.getName().equals("read")) continue;
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 2) continue;
                if (m.getParameterTypes()[1] != InputStream.class) continue;
                if (m.getReturnType() == NativeImage.class) return m;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Object pickAnyEnum(Class<?> c) {
        try {
            if (c != null && c.isEnum()) {
                Object[] arr = c.getEnumConstants();
                if (arr != null && arr.length > 0) return arr[0];
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static void runOnRender(Runnable r) {
        try {
            if (RenderSystem.isOnRenderThread()) {
                r.run();
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            RenderSystem.recordRenderCall((RenderCall) r::run);
            return;
        } catch (Throwable ignored) {
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.execute(r);
    }

    static final class Entry {
        final int num;
        final Identifier src;
        final Identifier base;

        volatile boolean requested;
        volatile boolean ready;

        volatile Identifier[] frameIds;
        volatile int[] delays;
        volatile boolean[] uploaded;
        volatile int uploadedCount;

        volatile Identifier currentId;

        int idx;
        long acc;

        Entry(int num, Identifier src, Identifier base) {
            this.num = num;
            this.src = src;
            this.base = base;
        }

        void advance(long dt) {
            Identifier[] ids = frameIds;
            int[] d = delays;
            if (ids == null || d == null) return;
            if (ids.length <= 1) return;
            if (d.length != ids.length) return;
            if (currentId == null) currentId = ids[0];

            acc += dt;

            int guard = 0;
            while (acc >= d[idx]) {
                acc -= d[idx];
                idx++;
                if (idx >= ids.length) idx = 0;
                currentId = ids[idx];
                guard++;
                if (guard > 16) {
                    acc = 0L;
                    break;
                }
            }
        }
    }

    static final class UploadJob {
        final Entry entry;
        final int frame;
        final Identifier id;
        final NativeImage image;

        UploadJob(Entry entry, int frame, Identifier id, NativeImage image) {
            this.entry = entry;
            this.frame = frame;
            this.id = id;
            this.image = image;
        }
    }

    static final class Decoded {
        final NativeImage[] frames;
        final int[] delays;

        Decoded(NativeImage[] frames, int[] delays) {
            this.frames = frames;
            this.delays = delays;
        }
    }

    private CapeTextureCache() {
    }
}
