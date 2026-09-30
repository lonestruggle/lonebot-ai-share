package net.runelite.client.plugins.lonebot;

import com.lonebot.launcher.embed.ClientWindowIpc;
import com.lonebot.launcher.embed.WindowEmbedHelper;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFOHEADER;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.W32APIOptions;
import net.runelite.api.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;

/**
 * Periodieke snapshot → {@code ~/.lonebot/instances/&lt;RSN&gt;/preview.jpg}.
 * Elke client-JVM schrijft alleen zichzelf (geen screen-grab van een andere tab).
 */
public final class ClientPreviewHelper {

    private static final Logger log = LoggerFactory.getLogger(ClientPreviewHelper.class);
    private static final long INTERVAL_MS = 500L;
    private static final int MAX_WIDTH = 720;
    private static final int PW_RENDERFULLCONTENT = 0x00000002;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_WRITE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public interface User32Extra extends User32 {
        User32Extra INSTANCE = Native.load("user32", User32Extra.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean PrintWindow(HWND hwnd, HDC hdcBlt, int nFlags);
    }

    private ClientPreviewHelper() {
    }

    public static File previewFile(String accountDisplayName) {
        ManagedAccountsStore.ManagedAccount fake = new ManagedAccountsStore.ManagedAccount();
        fake.displayName = accountDisplayName != null ? accountDisplayName : "account";
        return new File(LoneBotClientLauncher.profileDirFor(fake), "preview.jpg");
    }

    public static void maybeWrite(Client client, String accountDisplayName) {
        if (client == null || accountDisplayName == null || accountDisplayName.trim().isEmpty()) {
            return;
        }
        String key = accountDisplayName.trim().toLowerCase();
        long now = System.currentTimeMillis();
        Long prev = LAST_WRITE.get(key);
        if (prev != null && now - prev < INTERVAL_MS) {
            return;
        }
        LAST_WRITE.put(key, now);
        try {
            BufferedImage raw = null;
            if (WindowEmbedHelper.isWindows()) {
                raw = captureOwnFrame(accountDisplayName.trim());
            }
            if (raw == null || isMostlyBlack(raw)) {
                java.awt.Canvas canvas = client.getCanvas();
                if (canvas != null && canvas.getWidth() >= 40 && canvas.getHeight() >= 40) {
                    BufferedImage painted = new BufferedImage(
                            canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = painted.createGraphics();
                    try {
                        canvas.paint(g);
                    } finally {
                        g.dispose();
                    }
                    if (!isMostlyBlack(painted)) {
                        raw = painted;
                    } else if (canvas.isShowing()) {
                        BufferedImage shot = captureCanvasOnScreen(canvas);
                        if (shot != null && !isMostlyBlack(shot)) {
                            raw = shot;
                        }
                    }
                }
            }
            if (raw == null || isMostlyBlack(raw)) {
                return;
            }
            BufferedImage scaled = scaleDownOnly(raw, MAX_WIDTH);
            File out = previewFile(accountDisplayName.trim());
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            writeJpeg(scaled, out, 0.92f);
            File ts = new File(parent, "preview.ts");
            try (FileOutputStream fos = new FileOutputStream(ts)) {
                fos.write(Long.toString(now).getBytes());
            }
        } catch (Throwable t) {
            log.debug("[Preview] write failed: {}", t.toString());
        }
    }

    private static BufferedImage captureOwnFrame(String displayName) {
        try {
            long ipc = ClientWindowIpc.readHwnd(displayName);
            HWND src = WindowEmbedHelper.fromLong(ipc);
            long myPid = ProcessHandle.current().pid();
            if (src == null || !WindowEmbedHelper.matchesAccount(src, displayName, myPid)) {
                src = WindowEmbedHelper.findClientWindow(displayName, myPid);
            }
            if (src == null || !User32.INSTANCE.IsWindow(src)) {
                return null;
            }
            RECT rc = new RECT();
            User32.INSTANCE.GetClientRect(src, rc);
            int w = Math.max(0, rc.right - rc.left);
            int h = Math.max(0, rc.bottom - rc.top);
            if (w < 64 || h < 64) {
                User32.INSTANCE.GetWindowRect(src, rc);
                w = Math.max(0, rc.right - rc.left);
                h = Math.max(0, rc.bottom - rc.top);
            }
            if (w < 64 || h < 64 || w > 8192 || h > 8192) {
                return null;
            }
            HDC hdcWindow = User32.INSTANCE.GetDC(src);
            if (hdcWindow == null) {
                return null;
            }
            HDC hdcMem = null;
            HBITMAP hbmp = null;
            HANDLE old = null;
            try {
                hdcMem = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);
                hbmp = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, w, h);
                if (hdcMem == null || hbmp == null) {
                    return null;
                }
                old = GDI32.INSTANCE.SelectObject(hdcMem, hbmp);
                boolean ok = User32Extra.INSTANCE.PrintWindow(src, hdcMem, PW_RENDERFULLCONTENT);
                if (!ok) {
                    ok = User32Extra.INSTANCE.PrintWindow(src, hdcMem, 0);
                }
                if (!ok) {
                    return null;
                }
                return dibToImage(hdcMem, hbmp, w, h);
            } finally {
                try {
                    if (old != null && hdcMem != null) {
                        GDI32.INSTANCE.SelectObject(hdcMem, old);
                    }
                    if (hbmp != null) {
                        GDI32.INSTANCE.DeleteObject(hbmp);
                    }
                    if (hdcMem != null) {
                        GDI32.INSTANCE.DeleteDC(hdcMem);
                    }
                    User32.INSTANCE.ReleaseDC(src, hdcWindow);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage dibToImage(HDC hdcMem, HBITMAP hbmp, int w, int h) {
        BITMAPINFO bmi = new BITMAPINFO();
        bmi.bmiHeader = new BITMAPINFOHEADER();
        bmi.bmiHeader.biSize = bmi.bmiHeader.size();
        bmi.bmiHeader.biWidth = w;
        bmi.bmiHeader.biHeight = -h;
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB;
        Memory buffer = new Memory((long) w * h * 4);
        if (GDI32.INSTANCE.GetDIBits(hdcMem, hbmp, 0, h, buffer, bmi, WinGDI.DIB_RGB_COLORS) == 0) {
            return null;
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[w * h];
        for (int i = 0; i < pixels.length; i++) {
            int bgr = buffer.getInt((long) i * 4);
            int b = bgr & 0xFF;
            int g = (bgr >> 8) & 0xFF;
            int r = (bgr >> 16) & 0xFF;
            pixels[i] = (r << 16) | (g << 8) | b;
        }
        img.setRGB(0, 0, w, h, pixels, 0, w);
        return img;
    }

    private static boolean isMostlyBlack(BufferedImage img) {
        int w = Math.min(img.getWidth(), 40);
        int h = Math.min(img.getHeight(), 40);
        int dark = 0;
        int n = 0;
        for (int y = 0; y < h; y += 4) {
            for (int x = 0; x < w; x += 4) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                n++;
                if (rgb < 0x181818) {
                    dark++;
                }
            }
        }
        return n > 0 && dark * 10 > n * 8;
    }

    private static BufferedImage captureCanvasOnScreen(java.awt.Canvas canvas) {
        try {
            if (!canvas.isShowing()) {
                return null;
            }
            java.awt.Point loc = canvas.getLocationOnScreen();
            java.awt.Robot robot = new java.awt.Robot();
            return robot.createScreenCapture(new java.awt.Rectangle(
                    loc.x, loc.y, canvas.getWidth(), canvas.getHeight()));
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage scaleDownOnly(BufferedImage src, int maxW) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxW) {
            return src;
        }
        int nw = maxW;
        int nh = Math.max(1, (int) Math.round(h * (maxW / (double) w)));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    private static void writeJpeg(BufferedImage img, File out, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(img, "jpg", out);
            return;
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
