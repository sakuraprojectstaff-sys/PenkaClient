package fun.rich.display.hud;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import fun.rich.features.impl.render.Hud;
import fun.rich.utils.client.managers.api.draggable.AbstractDraggable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicInfoComponent extends AbstractDraggable {

    private static final float COVER_W = 32f;
    private static final float INFO_W = 170f;
    private static final float BOX_H = 32f;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "musicinfo-poll");
        t.setDaemon(true);
        return t;
    });

    public volatile IMediaSession session;

    private final Identifier artworkId = Identifier.of("mre", "musicinfo_artwork");
    private volatile int artworkHash;

    private volatile boolean pollBusy;
    private long nextPollAtMs;

    private long lastFrameAtMs = System.currentTimeMillis();
    private float progressValue;

    private volatile String dispTitle = "название трека";
    private volatile String dispArtist = "артист";
    private volatile long dispPosAny;
    private volatile long dispDurAny;
    private volatile boolean dispPlaying;

    private final Map<Integer, Long> lastPosMsBySession = new HashMap<>();
    private final Map<Integer, Long> lastSeenMsBySession = new HashMap<>();
    private final Map<Integer, Integer> stableScoreBySession = new HashMap<>();

    private Method mDrawTexFive;
    private Method mDrawTexSeven;
    private Method mDrawTexNineInt;
    private Method mDrawTexNineFloat;

    public MusicInfoComponent() {
        super("Music Info", 10, 10, (int) (COVER_W + INFO_W), (int) BOX_H, true);
    }

    @Override
    public void tick() {
        schedulePoll();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Hud hud = Hud.getInstance();
        if (hud == null) return;
        if (!canDraw(hud, this)) return;
        schedulePoll();
        drawDraggable(context);
    }

    @Override
    public void drawDraggable(DrawContext ctx) {
        if (ctx == null || mc == null) return;

        TextRenderer tr = mc.textRenderer;
        if (tr == null) return;

        long now = System.currentTimeMillis();
        float dt = (now - lastFrameAtMs) / 1000f;
        if (dt < 0f) dt = 0f;
        if (dt > 0.2f) dt = 0.2f;
        lastFrameAtMs = now;

        setWidth((int) (COVER_W + INFO_W));
        setHeight((int) BOX_H);

        float x = getX();
        float y = getY();
        float w = COVER_W + INFO_W;
        float h = BOX_H;

        int frame = 0xFF000000;
        int leftBg = 0xFF1B1B1B;
        int rightBg = 0xFF121212;

        int titleCol = 0xFFEDEDED;
        int artistCol = 0xFFB9B9B9;
        int timeCol = 0xFFB084FF;

        int progBg = 0xFF2C2C2C;
        int progFill = 0xFFB084FF;

        fill(ctx, x, y, x + w, y + h, frame);
        fill(ctx, x + 1f, y + 1f, x + w - 1f, y + h - 1f, rightBg);
        fill(ctx, x + 1f, y + 1f, x + COVER_W, y + h - 1f, leftBg);

        float imgPad = 4f;
        float imgSize = COVER_W - imgPad * 2f;
        float imgX = x + imgPad;
        float imgY = y + imgPad;

        boolean hasArt = artworkHash != 0;
        if (hasArt) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            drawTextureCompat(ctx, artworkId, imgX, imgY, imgSize, imgSize);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            fill(ctx, imgX, imgY, imgX + imgSize, imgY + imgSize, 0xFF202020);
            String s = "♪";
            ctx.drawText(tr, s, (int) (imgX + imgSize / 2f - tr.getWidth(s) / 2f), (int) (imgY + imgSize / 2f - tr.fontHeight / 2f), titleCol, false);
        }

        float padding = 6f;
        float cx = x + COVER_W + padding;
        float cw = INFO_W - padding * 2f;

        String title = safe(dispTitle);
        String artist = safe(dispArtist);

        if (title.isEmpty() && artist.isEmpty()) {
            title = "нет трека";
            artist = "ожидание...";
        }

        long posMs = normalizeAnyToMs(dispPosAny);
        long durMs = normalizeAnyToMs(dispDurAny);

        float progress = 0f;
        if (durMs > 0L) progress = (float) posMs / (float) durMs;
        progress = MathHelper.clamp(progress, 0f, 1f);
        progressValue = smooth(progressValue, progress, 18f, dt);

        float sliderY = y + h - 7f;
        fill(ctx, cx, sliderY, cx + cw, sliderY + 2f, progBg);
        fill(ctx, cx, sliderY, cx + cw * progressValue, sliderY + 2f, progFill);

        String time = formatTimeMs(posMs);
        float timeW = tr.getWidth(time);
        float maxTextW = Math.max(0f, cw - timeW - 6f);

        float titleY = y + 6f;
        float artistY = titleY + tr.fontHeight + 2f;

        drawScrolling(ctx, tr, title.toLowerCase(), cx, titleY, titleCol, maxTextW);
        drawScrolling(ctx, tr, artist.toLowerCase(), cx, artistY, artistCol, cw);

        ctx.drawText(tr, time, (int) (cx + cw - timeW), (int) titleY, timeCol, false);
    }

    private void schedulePoll() {
        if (mc == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now < nextPollAtMs) return;
        if (pollBusy) return;

        pollBusy = true;
        nextPollAtMs = now + 200L;

        executor.execute(() -> {
            long tnow = System.currentTimeMillis();
            try {
                invokeAny(MediaPlayerInfo.Instance, "update", "refresh", "tick");
                List<IMediaSession> sessions = MediaPlayerInfo.Instance.getMediaSessions();
                if (sessions == null || sessions.isEmpty()) {
                    decayOld(tnow);
                    return;
                }

                IMediaSession best = pickBestSession(sessions, tnow);
                if (best == null) {
                    decayOld(tnow);
                    return;
                }

                MediaInfo info = best.getMedia();
                if (info == null) {
                    decayOld(tnow);
                    return;
                }

                TrackMeta meta = buildMeta(best, info, tnow);
                if (!meta.valid) {
                    decayOld(tnow);
                    return;
                }

                dispTitle = meta.title;
                dispArtist = meta.artist;
                dispPlaying = meta.playing;
                dispPosAny = meta.posAny;
                dispDurAny = meta.durAny;
                session = best;

                if (meta.artBytes != null && meta.artBytes.length > 0) {
                    int hash = Arrays.hashCode(meta.artBytes);
                    if (artworkHash == 0 || artworkHash != hash) {
                        artworkHash = hash;
                        registerArtwork(meta.artBytes);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                pollBusy = false;
            }
        });
    }

    private IMediaSession pickBestSession(List<IMediaSession> sessions, long nowMs) {
        IMediaSession best = null;
        int bestScore = Integer.MIN_VALUE;

        for (IMediaSession s : sessions) {
            if (s == null) continue;

            invokeAny(s, "update", "refresh", "tick");

            MediaInfo info = s.getMedia();
            if (info == null) continue;

            String rawTitle = safe(info.getTitle());
            String rawArtist = safe(info.getArtist());
            boolean playing = info.getPlaying();

            long posMs = normalizeAnyToMs(info.getPosition());
            long durMs = normalizeAnyToMs(info.getDuration());

            byte[] art = firstArtworkBytes(info);
            String label = sessionLabel(s).toLowerCase(Locale.ROOT);

            int id = System.identityHashCode(s);
            long lastPos = lastPosMsBySession.getOrDefault(id, -1L);
            boolean posMoves = posMs > 0 && lastPos >= 0 && posMs != lastPos;

            lastPosMsBySession.put(id, posMs);
            lastSeenMsBySession.put(id, nowMs);

            int score = 0;

            if (playing) score += 2000;
            if (posMoves) score += 1600;
            if (durMs > 0) score += 400;
            if (posMs > 0) score += 200;
            if (!rawTitle.isEmpty()) score += 500;
            if (!rawArtist.isEmpty()) score += 350;
            if (art != null && art.length > 0) score += 80;

            if (label.contains("spotify")) score += 250;
            if (label.contains("yandex") || label.contains("яндекс")) score += 250;
            if (label.contains("edge")) score += 200;
            if (label.contains("chrome")) score += 50;
            if (label.contains("firefox")) score += 50;

            String combined = (rawTitle + " " + rawArtist).toLowerCase(Locale.ROOT);
            if (combined.contains("advert") || combined.contains("реклама") || combined.contains("ad ")) score -= 3000;

            int stable = stableScoreBySession.getOrDefault(id, 0);
            stable = clamp(stable + (playing || posMoves ? 3 : -2), 0, 30);
            stableScoreBySession.put(id, stable);
            score += stable * 30;

            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }

        return best;
    }

    private TrackMeta buildMeta(IMediaSession s, MediaInfo info, long nowMs) {
        String label = sessionLabel(s);

        String title = cleanTitle(safe(info.getTitle()), label);
        String artist = cleanArtist(safe(info.getArtist()), label);

        if (artist.isEmpty()) {
            String[] split = splitArtistTitle(title);
            if (split != null) {
                artist = split[0];
                title = split[1];
            }
        } else {
            String[] split = splitArtistTitle(title);
            if (split != null) {
                String a2 = split[0];
                String t2 = split[1];
                if (looksBetter(t2, title)) title = t2;
                if (looksBetter(a2, artist)) artist = a2;
            }
        }

        if (title.isEmpty()) title = fallbackTitle(label);
        if (artist.isEmpty()) artist = fallbackArtist(label);

        boolean playing = info.getPlaying();
        long posAny = info.getPosition();
        long durAny = info.getDuration();

        byte[] art = firstArtworkBytes(info);

        boolean valid = !(title.isEmpty() && artist.isEmpty());
        if (valid) {
            title = clampText(title, 96);
            artist = clampText(artist, 96);
        }

        TrackMeta m = new TrackMeta();
        m.valid = valid;
        m.title = title;
        m.artist = artist;
        m.playing = playing;
        m.posAny = posAny;
        m.durAny = durAny;
        m.artBytes = art;
        m.source = label;
        m.nowMs = nowMs;
        return m;
    }

    private byte[] firstArtworkBytes(MediaInfo info) {
        try {
            byte[] png = info.getArtworkPng();
            if (png != null && png.length > 0) return png;
        } catch (Throwable ignored) {
        }

        Object r;

        r = tryInvoke(info, "getArtwork", "getArtworkBytes", "getArtworkJpg", "getArtworkJpeg", "getArtworkImage");
        if (r instanceof byte[] b && b.length > 0) return b;

        r = tryInvoke(info, "artwork", "artworkBytes", "artworkPng");
        if (r instanceof byte[] b && b.length > 0) return b;

        return null;
    }

    private Object tryInvoke(Object target, String... names) {
        if (target == null) return null;
        for (String n : names) {
            try {
                Method m = target.getClass().getMethod(n);
                if (m.getParameterCount() == 0) return m.invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void decayOld(long nowMs) {
        long limit = nowMs - 9000L;
        lastSeenMsBySession.entrySet().removeIf(e -> e.getValue() < limit);
        lastPosMsBySession.keySet().retainAll(lastSeenMsBySession.keySet());
        stableScoreBySession.keySet().retainAll(lastSeenMsBySession.keySet());
    }

    private String sessionLabel(IMediaSession s) {
        String a = tryCallString(s, "getAppName", "getApplicationName", "getSourceAppName", "getSource", "getApp", "getId", "getIdentifier");
        if (!a.isEmpty()) return a;
        return String.valueOf(s);
    }

    private String tryCallString(Object target, String... names) {
        if (target == null) return "";
        for (String n : names) {
            try {
                Method m = target.getClass().getMethod(n);
                if (m.getReturnType() == String.class && m.getParameterCount() == 0) {
                    Object r = m.invoke(target);
                    return r instanceof String ? (String) r : "";
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    private void invokeAny(Object target, String... names) {
        if (target == null) return;
        for (String n : names) {
            try {
                Method m = target.getClass().getMethod(n);
                if (m.getParameterCount() == 0 && m.getReturnType() == void.class) {
                    m.invoke(target);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private String cleanTitle(String t, String source) {
        String s = norm(t);
        if (s.isEmpty()) return s;

        s = stripSuffix(s, " - youtube");
        s = stripSuffix(s, " — youtube");
        s = stripSuffix(s, " | youtube");
        s = stripSuffix(s, " - yandex music");
        s = stripSuffix(s, " — yandex music");

        s = stripBracketJunk(s);
        s = stripCommonJunk(s);

        if (source != null) {
            String l = source.toLowerCase(Locale.ROOT);
            if (l.contains("youtube")) s = stripSuffix(s, " - youtube music");
        }

        return norm(s);
    }

    private String cleanArtist(String a, String source) {
        String s = norm(a);
        if (s.isEmpty()) return s;
        s = stripCommonJunk(s);
        return norm(s);
    }

    private String stripSuffix(String s, String suffix) {
        String x = s;
        String low = x.toLowerCase(Locale.ROOT);
        String suf = suffix.toLowerCase(Locale.ROOT);
        if (low.endsWith(suf)) return norm(x.substring(0, x.length() - suffix.length()));
        return x;
    }

    private String stripCommonJunk(String s) {
        String x = s;
        x = replaceIgnoreCase(x, "official video", "");
        x = replaceIgnoreCase(x, "official music video", "");
        x = replaceIgnoreCase(x, "lyrics", "");
        x = replaceIgnoreCase(x, "lyric video", "");
        x = replaceIgnoreCase(x, "audio", "");
        x = replaceIgnoreCase(x, "remastered", "");
        x = replaceIgnoreCase(x, "explicit", "");
        x = replaceIgnoreCase(x, "hd", "");
        x = replaceIgnoreCase(x, "4k", "");
        return norm(x);
    }

    private String stripBracketJunk(String s) {
        String x = s;
        x = stripBracketBlock(x, "(", ")");
        x = stripBracketBlock(x, "[", "]");
        return norm(x);
    }

    private String stripBracketBlock(String s, String l, String r) {
        String x = s;
        int loop = 0;
        while (loop++ < 6) {
            int a = x.indexOf(l);
            int b = x.indexOf(r, a + 1);
            if (a >= 0 && b > a) {
                String mid = x.substring(a + 1, b).toLowerCase(Locale.ROOT);
                if (mid.contains("official") || mid.contains("video") || mid.contains("lyrics") || mid.contains("audio") || mid.contains("remaster") || mid.contains("hd") || mid.contains("4k")) {
                    x = (x.substring(0, a) + " " + x.substring(b + 1));
                    continue;
                }
            }
            break;
        }
        return x;
    }

    private String replaceIgnoreCase(String s, String find, String repl) {
        String x = s;
        String low = x.toLowerCase(Locale.ROOT);
        String f = find.toLowerCase(Locale.ROOT);
        int idx = low.indexOf(f);
        if (idx < 0) return x;
        return norm(x.substring(0, idx) + " " + repl + " " + x.substring(idx + find.length()));
    }

    private String[] splitArtistTitle(String title) {
        String s = norm(title);
        if (s.isEmpty()) return null;

        String[] seps = new String[]{" - ", " — ", " – ", " | ", " • ", " · ", " : "};
        for (String sep : seps) {
            int idx = s.indexOf(sep);
            if (idx <= 0) continue;

            String left = norm(s.substring(0, idx));
            String right = norm(s.substring(idx + sep.length()));
            if (left.isEmpty() || right.isEmpty()) continue;

            if (isProbablyArtist(left) && isProbablyTitle(right)) return new String[]{left, right};
            if (isProbablyArtist(right) && isProbablyTitle(left)) return new String[]{right, left};

            if (left.length() <= 30 && right.length() <= 80) return new String[]{left, right};
        }

        return null;
    }

    private boolean isProbablyArtist(String s) {
        String x = s.toLowerCase(Locale.ROOT);
        if (x.contains("ft.") || x.contains("feat")) return true;
        if (x.length() <= 2) return false;
        if (x.length() <= 28) return true;
        return x.split(" ").length <= 4;
    }

    private boolean isProbablyTitle(String s) {
        String x = s.toLowerCase(Locale.ROOT);
        if (x.contains("official") || x.contains("video") || x.contains("lyrics")) return false;
        return s.length() >= 2;
    }

    private boolean looksBetter(String a, String b) {
        if (a == null) return false;
        if (b == null) return true;
        String x = norm(a);
        String y = norm(b);
        if (x.isEmpty()) return false;
        if (y.isEmpty()) return true;
        if (x.equalsIgnoreCase(y)) return false;
        if (x.length() > y.length() && x.length() <= 96) return true;
        return false;
    }

    private String fallbackTitle(String source) {
        String s = norm(source);
        if (s.isEmpty()) return "название трека";
        return "трек";
    }

    private String fallbackArtist(String source) {
        String s = norm(source);
        if (s.isEmpty()) return "артист";
        if (s.toLowerCase(Locale.ROOT).contains("spotify")) return "spotify";
        if (s.toLowerCase(Locale.ROOT).contains("yandex") || s.toLowerCase(Locale.ROOT).contains("яндекс")) return "yandex";
        if (s.toLowerCase(Locale.ROOT).contains("youtube")) return "youtube";
        return "плеер";
    }

    private String clampText(String s, int max) {
        String x = norm(s);
        if (x.length() <= max) return x;
        return x.substring(0, Math.max(0, max - 1));
    }

    private String norm(String s) {
        if (s == null) return "";
        String x = s.replace('\n', ' ').replace('\r', ' ').trim();
        while (x.contains("  ")) x = x.replace("  ", " ");
        return x;
    }

    private int clamp(int v, int a, int b) {
        if (v < a) return a;
        if (v > b) return b;
        return v;
    }

    private void registerArtwork(byte[] bytes) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (image == null) return;

            NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
            mc.execute(() -> {
                try {
                    mc.getTextureManager().registerTexture(artworkId, tex);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void drawScrolling(DrawContext ctx, TextRenderer tr, String text, float x, float y, int color, float maxWidth) {
        if (maxWidth <= 2f) return;

        float textW = tr.getWidth(text);
        float scroll = 0f;

        if (textW > maxWidth) {
            float scrollMax = textW - maxWidth;
            if (scrollMax < 0f) scrollMax = 0f;

            float pause = 900f;
            float dur = 4200f;
            float cycle = pause + dur + pause + dur;

            long now = System.currentTimeMillis();
            float tt = (now % (long) cycle);

            if (tt < pause) scroll = 0f;
            else if (tt < pause + dur) scroll = ((tt - pause) / dur) * scrollMax;
            else if (tt < pause + dur + pause) scroll = scrollMax;
            else scroll = scrollMax * (1f - ((tt - pause - dur - pause) / dur));
        }

        int sx1 = (int) Math.ceil(x - 1);
        int sy1 = (int) Math.ceil(y - 1);
        int sx2 = (int) Math.ceil((x - 1) + maxWidth + 2);
        int sy2 = (int) Math.ceil((y - 1) + tr.fontHeight + 4);

        ctx.enableScissor(sx1, sy1, sx2, sy2);
        ctx.drawText(tr, text, (int) (x - scroll), (int) y, color, false);
        ctx.disableScissor();
    }

    private void drawTextureCompat(DrawContext ctx, Identifier id, float x, float y, float w, float h) {
        tryInitDrawTexture();

        int ix = (int) x;
        int iy = (int) y;
        int iw = (int) w;
        int ih = (int) h;

        try {
            if (mDrawTexFive != null) {
                mDrawTexFive.invoke(ctx, id, ix, iy, iw, ih);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (mDrawTexSeven != null) {
                mDrawTexSeven.invoke(ctx, id, ix, iy, 0, 0, iw, ih);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (mDrawTexNineInt != null) {
                mDrawTexNineInt.invoke(ctx, id, ix, iy, 0, 0, iw, ih, iw, ih);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (mDrawTexNineFloat != null) {
                mDrawTexNineFloat.invoke(ctx, id, (float) ix, (float) iy, 0f, 0f, (float) iw, (float) ih, (float) iw, (float) ih);
            }
        } catch (Throwable ignored) {
        }
    }

    private void tryInitDrawTexture() {
        if (mDrawTexFive != null || mDrawTexSeven != null || mDrawTexNineInt != null || mDrawTexNineFloat != null) return;

        Method[] ms = DrawContext.class.getMethods();
        for (Method m : ms) {
            if (!m.getName().equals("drawTexture")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length < 2) continue;
            if (p[0] != Identifier.class) continue;

            if (p.length == 5 && p[1] == int.class && p[2] == int.class && p[3] == int.class && p[4] == int.class) {
                mDrawTexFive = m;
                continue;
            }

            if (p.length == 7 && p[1] == int.class && p[2] == int.class && p[3] == int.class && p[4] == int.class && p[5] == int.class && p[6] == int.class) {
                mDrawTexSeven = m;
                continue;
            }

            if (p.length == 9) {
                if (p[1] == int.class) mDrawTexNineInt = m;
                if (p[1] == float.class) mDrawTexNineFloat = m;
            }
        }
    }

    private void fill(DrawContext ctx, float x1, float y1, float x2, float y2, int color) {
        ctx.fill((int) Math.floor(x1), (int) Math.floor(y1), (int) Math.ceil(x2), (int) Math.ceil(y2), color);
    }

    private float smooth(float cur, float target, float speed, float dt) {
        float k = 1f - (float) Math.exp(-speed * dt);
        return cur + (target - cur) * k;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private long normalizeAnyToMs(long v) {
        if (v <= 0L) return 0L;
        if (v >= 10_000_000_000L) return v / 1_000_000L;
        if (v >= 10_000_000L) return v / 1_000L;
        if (v < 10_000L) return v * 1000L;
        return v;
    }

    private String formatTimeMs(long ms) {
        if (ms < 0L) ms = 0L;
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    private static final class TrackMeta {
        boolean valid;
        String title;
        String artist;
        boolean playing;
        long posAny;
        long durAny;
        byte[] artBytes;
        String source;
        long nowMs;
    }
}
