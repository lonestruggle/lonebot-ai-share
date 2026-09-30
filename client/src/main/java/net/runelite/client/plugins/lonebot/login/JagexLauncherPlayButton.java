package net.runelite.client.plugins.lonebot.login;

import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.storm.sdk.game.Client;
import net.storm.sdk.game.Game;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Klikt op de <strong>Play</strong>-knop op het <strong>RuneLite game-canvas</strong> ({@link Client#getCanvas()}),
 * dus het scherm <em>in de client</em> (o.a. "Welcome to RuneScape" met grijze <strong>Play Now</strong>).
 * Dit is <strong>niet</strong> het aparte Jagex Launcher-programma buiten RuneLite om.
 * <p>
 * Grijze Jagex-login "Play Now": proportionele {@link net.storm.sdk.input.Mouse#click} — geen widget, geen screenshot.
 * Welkomst-lobby (CLICK HERE TO PLAY): {@link WelcomeScreenPlayHelper} iface 378,77 + SDK.
 */
public final class JagexLauncherPlayButton {

    private static final Random RANDOM = new Random();
    private static Robot robot;

    /** Vaste coördinaten als fallback (zelfde als oorspronkelijke snippet). */
    private static final int BUTTON_WIDTH = 210;
    private static final int BUTTON_HEIGHT = 55;
    private static final int BUTTON_Y = 235;

    /** Kleurdetectie: groen = G dominant en voldoende helder (Play-knop groen). */
    private static final int GREEN_MIN = 80;
    private static final double GREEN_RATIO = 1.1; // G >= GREEN_RATIO * max(R,B)

    /** Detectie grijze "Play Now"-knop: witte tekst (R,G,B allemaal hoog). */
    private static final int WHITE_MIN = 200;       // pixel is "wit" als R,G,B >= WHITE_MIN
    private static final double MIN_WHITE_FRAC = 0.08; // min. fractie witte pixels (tekst op knop)

    /** Zoekgebied: midden van het scherm, waar de knop typisch zit. */
    private static final double SEARCH_WIDTH_FRAC = 0.8;
    private static final int SEARCH_Y_START = 160;
    private static final int SEARCH_Y_END = 360;
    /** Min. fractie groene pixels in een rechthoek om als knop te tellen. */
    private static final double MIN_GREEN_FRAC = 0.25;

    private JagexLauncherPlayButton() {
    }

    private static Robot getRobot() {
        if (robot == null) {
            try {
                robot = new Robot();
            } catch (AWTException e) {
                return null;
            }
        }
        return robot;
    }

    /**
     * Bepaalt de schermcoördinaten van het game-canvas (voor screenshot).
     */
    private static Rectangle getCanvasScreenBounds() {
        Canvas canvas = Client.getCanvas();
        if (canvas == null) {
            return null;
        }
        try {
            Point loc = canvas.getLocationOnScreen();
            Dimension size = canvas.getSize();
            if (size == null || size.width <= 0 || size.height <= 0) {
                return null;
            }
            return new Rectangle(loc.x, loc.y, size.width, size.height);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Maakt een screenshot van het game-canvas.
     */
    private static BufferedImage captureCanvas() {
        Robot r = getRobot();
        Rectangle bounds = getCanvasScreenBounds();
        if (r == null || bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return null;
        }
        try {
            return r.createScreenCapture(bounds);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Zoekt naar een groene "Play"-achtige knop in het midden van de afbeelding.
     * Retourneert de canvas-coördinaten van het midden van de gevonden rechthoek, of null.
     */
    public static Point findPlayButtonByColor(BufferedImage capture) {
        if (capture == null) {
            return null;
        }
        int w = capture.getWidth();
        int h = capture.getHeight();
        if (w < BUTTON_WIDTH || h < BUTTON_HEIGHT) {
            return null;
        }

        int searchXStart = (int) ((1 - SEARCH_WIDTH_FRAC) / 2 * w);
        int searchXEnd = w - searchXStart;
        int yStart = Math.max(0, SEARCH_Y_START);
        int yEnd = Math.min(h - BUTTON_HEIGHT, SEARCH_Y_END);
        if (yEnd <= yStart) {
            yEnd = Math.min(h - BUTTON_HEIGHT, SEARCH_Y_END);
            yStart = Math.max(0, SEARCH_Y_START);
        }

        int[] pixels = getPixels(capture);
        if (pixels == null) {
            return null;
        }

        int bestScore = 0;
        int bestX = 0, bestY = 0;

        for (int by = yStart; by <= yEnd; by += 4) {
            for (int bx = searchXStart; bx + BUTTON_WIDTH <= searchXEnd; bx += 4) {
                int greenCount = 0;
                int total = 0;
                for (int dy = 0; dy < BUTTON_HEIGHT; dy += 2) {
                    for (int dx = 0; dx < BUTTON_WIDTH; dx += 2) {
                        int x = bx + dx;
                        int y = by + dy;
                        if (x >= 0 && x < w && y >= 0 && y < h) {
                            total++;
                            int rgb = pixels[y * w + x];
                            int r = (rgb >> 16) & 0xFF;
                            int g = (rgb >> 8) & 0xFF;
                            int b = rgb & 0xFF;
                            if (g >= GREEN_MIN && g >= GREEN_RATIO * Math.max(r, b)) {
                                greenCount++;
                            }
                        }
                    }
                }
                if (total > 0 && (double) greenCount / total >= MIN_GREEN_FRAC) {
                    int score = greenCount;
                    if (score > bestScore) {
                        bestScore = score;
                        bestX = bx + BUTTON_WIDTH / 2;
                        bestY = by + BUTTON_HEIGHT / 2;
                    }
                }
            }
        }

        if (bestScore == 0) {
            return null;
        }
        return new Point(bestX, bestY);
    }

    /**
     * Zoekt naar de grijze "Play Now"-knop met witte tekst (geen groen).
     * Detecteert een rechthoek met veel witte pixels (= de "Play Now" tekst).
     */
    public static Point findPlayButtonByWhiteText(BufferedImage capture) {
        if (capture == null) {
            return null;
        }
        int w = capture.getWidth();
        int h = capture.getHeight();
        if (w < BUTTON_WIDTH || h < BUTTON_HEIGHT) {
            return null;
        }

        int searchXStart = (int) ((1 - SEARCH_WIDTH_FRAC) / 2 * w);
        int searchXEnd = w - searchXStart;
        int yStart = Math.max(0, SEARCH_Y_START);
        int yEnd = Math.min(h - BUTTON_HEIGHT, SEARCH_Y_END);
        if (yEnd <= yStart) {
            yEnd = Math.min(h - BUTTON_HEIGHT, SEARCH_Y_END);
            yStart = Math.max(0, SEARCH_Y_START);
        }

        int[] pixels = getPixels(capture);
        if (pixels == null) {
            return null;
        }

        int bestScore = 0;
        int bestX = 0, bestY = 0;

        for (int by = yStart; by <= yEnd; by += 3) {
            for (int bx = searchXStart; bx + BUTTON_WIDTH <= searchXEnd; bx += 3) {
                int whiteCount = 0;
                int total = 0;
                for (int dy = 0; dy < BUTTON_HEIGHT; dy += 2) {
                    for (int dx = 0; dx < BUTTON_WIDTH; dx += 2) {
                        int x = bx + dx;
                        int y = by + dy;
                        if (x >= 0 && x < w && y >= 0 && y < h) {
                            total++;
                            int rgb = pixels[y * w + x];
                            int r = (rgb >> 16) & 0xFF;
                            int g = (rgb >> 8) & 0xFF;
                            int b = rgb & 0xFF;
                            if (r >= WHITE_MIN && g >= WHITE_MIN && b >= WHITE_MIN) {
                                whiteCount++;
                            }
                        }
                    }
                }
                if (total > 0 && (double) whiteCount / total >= MIN_WHITE_FRAC) {
                    if (whiteCount > bestScore) {
                        bestScore = whiteCount;
                        bestX = bx + BUTTON_WIDTH / 2;
                        bestY = by + BUTTON_HEIGHT / 2;
                    }
                }
            }
        }

        if (bestScore == 0) {
            return null;
        }
        return new Point(bestX, bestY);
    }

    private static int[] getPixels(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_ARGB || img.getType() == BufferedImage.TYPE_INT_RGB) {
            return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        return pixels;
    }

    /**
     * Eenvoudige template matching: zoekt het beste overeenkomende gebied (genormaliseerde correlatie).
     * template en capture moeten hetzelfde pixelformaat hebben (INT_ARGB/INT_RGB) voor snelle toegang.
     *
     * @return canvas-coördinaten van het midden van de beste match, of null
     */
    public static Point findPlayButtonByTemplate(BufferedImage capture, BufferedImage template) {
        if (capture == null || template == null) {
            return null;
        }
        int cw = capture.getWidth();
        int ch = capture.getHeight();
        int tw = template.getWidth();
        int th = template.getHeight();
        if (tw > cw || th > ch || tw < 10 || th < 10) {
            return null;
        }

        int[] capPx = getPixels(capture);
        int[] tplPx = getPixels(template);
        if (capPx == null || tplPx == null) {
            return null;
        }

        double bestScore = Double.NEGATIVE_INFINITY;
        int bestX = 0, bestY = 0;

        int yEnd = Math.min(ch - th, SEARCH_Y_END + 50);
        int yStart = Math.max(0, SEARCH_Y_START - 20);
        int xStart = (cw - tw) / 2 - (cw / 4);
        int xEnd = (cw + tw) / 2 + (cw / 4);
        if (xStart < 0) xStart = 0;
        if (xEnd > cw - tw) xEnd = cw - tw;

        for (int y = yStart; y <= yEnd; y += 2) {
            for (int x = xStart; x <= xEnd; x += 2) {
                double score = matchTemplateAt(capPx, cw, ch, tplPx, tw, th, x, y);
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x + tw / 2;
                    bestY = y + th / 2;
                }
            }
        }

        if (bestScore < 0.3) {
            return null;
        }
        return new Point(bestX, bestY);
    }

    private static double matchTemplateAt(int[] cap, int cw, int ch, int[] tpl, int tw, int th, int ox, int oy) {
        double sumT = 0, sumC = 0, sumT2 = 0, sumC2 = 0, sumTC = 0;
        int n = 0;
        for (int dy = 0; dy < th; dy++) {
            for (int dx = 0; dx < tw; dx++) {
                int cx = ox + dx;
                int cy = oy + dy;
                if (cx >= cw || cy >= ch) continue;
                int tc = tpl[dy * tw + dx];
                int cc = cap[cy * cw + cx];
                double tg = gray(tc);
                double cg = gray(cc);
                sumT += tg;
                sumC += cg;
                sumT2 += tg * tg;
                sumC2 += cg * cg;
                sumTC += tg * cg;
                n++;
            }
        }
        if (n < 10) return -1;
        double nD = n;
        double meanT = sumT / nD;
        double meanC = sumC / nD;
        double varT = sumT2 / nD - meanT * meanT;
        double varC = sumC2 / nD - meanC * meanC;
        if (varT <= 0 || varC <= 0) return -1;
        double cov = sumTC / nD - meanT * meanC;
        return cov / (Math.sqrt(varT) * Math.sqrt(varC));
    }

    private static double gray(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }

    /**
     * Scherm uit / DPMS: {@link Robot#createScreenCapture} levert vaak zwart beeld — template-match dan misleidend.
     */
    private static boolean isUnreliableCapture(BufferedImage capture) {
        if (capture == null) {
            return true;
        }
        int w = capture.getWidth();
        int h = capture.getHeight();
        if (w <= 0 || h <= 0) {
            return true;
        }
        int[] pixels = getPixels(capture);
        if (pixels == null) {
            return true;
        }
        int step = Math.max(1, Math.min(w, h) / 32);
        int dark = 0;
        int total = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                total++;
                int rgb = pixels[y * w + x];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r < 25 && g < 25 && b < 25) {
                    dark++;
                }
            }
        }
        return total > 0 && (double) dark / total > 0.85;
    }

    /**
     * Probeert een template te laden van de classpath (bijv. resources/play_button.png).
     */
    private static BufferedImage loadTemplate() {
        try {
            java.io.InputStream in = JagexLauncherPlayButton.class.getResourceAsStream("/play_button.png");
            if (in == null) {
                in = JagexLauncherPlayButton.class.getResourceAsStream("play_button.png");
            }
            if (in != null) {
                BufferedImage img = javax.imageio.ImageIO.read(in);
                in.close();
                return img;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static BufferedImage templateCache = null;

    private static BufferedImage getTemplate() {
        if (templateCache == null) {
            templateCache = loadTemplate();
        }
        return templateCache;
    }

    /**
     * Zoekt de Play-knop op het game-canvas en klikt.
     * <p>Eerst {@link WelcomeScreenPlayHelper} (iface 378,77 CLICK HERE TO PLAY + SDK), daarna visueel:
     * template → witte tekst → groen → vaste coördinaten. Pixel-klik kan tweede logout/inlog-cyclus onstabieler maken
     * dan een echte widget-{@code interact}.
     *
     * @return true als er een klik is uitgevoerd
     */
    private static long lastCanvasFocusAttemptMs;
    private static final long CANVAS_FOCUS_INTERVAL_MS = 12_000L;
    private static long lastJagexPlayClickMs;
    private static int jagexLoginStuckRetries;
    private static final long JAGEX_PLAY_STUCK_WINDOW_MS = 10_000L;

    public static boolean clickPlayButton() {
        tickJagexLoginStuckState();
        WelcomeScreenPlayHelper.LoginPhase phase = WelcomeScreenPlayHelper.resolveLoginPhase();
        if (phase == WelcomeScreenPlayHelper.LoginPhase.WELCOME_LOBBY) {
            int delay = WelcomeScreenPlayHelper.advanceWelcomeLobbyClick();
            return delay > 0;
        }
        if (phase != WelcomeScreenPlayHelper.LoginPhase.LOGIN_SCREEN
                && !(Game.isOnLoginScreen() && !Game.isLoggedIn())) {
            return false;
        }
        if (!WelcomeScreenPlayHelper.shouldAttemptPlayClick()) {
            return false;
        }

        // Jagex login-scherm (grijze "Play Now"): proportioneel (width-fixed-y), geen screenshot.
        if (Game.isOnLoginScreen() && !Game.isLoggedIn()) {
            if (WelcomeScreenPlayHelper.isPostJagexLoginScreenPlaySettling()) {
                return false;
            }
            boolean clicked = jagexLoginStuckRetries >= 1
                    ? clickJagexLoginPlayNowFallback("login retry")
                    : clickJagexLoginPlayNow();
            if (clicked) {
                recordJagexPlayClickAttempt();
                WelcomeScreenPlayHelper.notifyJagexLoginScreenPlayClicked();
            }
            return clicked;
        }

        if (WelcomeScreenPlayHelper.tryClickPlay()) {
            return true;
        }

        if (!WelcomeScreenPlayHelper.mayUseCanvasPlayFallback()) {
            return false;
        }
        return clickPlayNowOnCanvas("welkomst-lobby canvas");
    }

    /** OSRS fixed-mode — login-UI Y is relatief aan 503px game-hoogte, niet volledige vensterhoogte. */
    private static final int REF_CANVAS_W = 765;
    private static final int REF_CANVAS_H = 503;

    /**
     * Berekende klikzone voor grijze Jagex Play Now (referentie 765×503 → canvas, geen screenshot).
     */
    public static final class JagexPlayNowTarget {
        public final int canvasWidth;
        public final int canvasHeight;
        public final int left;
        public final int top;
        public final int width;
        public final int height;
        public final int centerX;
        public final int centerY;
        public final int centerYPct;
        public final int btnWidthPct;
        public final int btnHeightPct;
        public final double scale;
        public final int offsetX;
        public final int offsetY;
        public final String mapMode;

        JagexPlayNowTarget(int canvasWidth, int canvasHeight, int left, int top, int width, int height,
                           int centerX, int centerY, int centerYPct, int btnWidthPct, int btnHeightPct,
                           double scale, int offsetX, int offsetY, String mapMode) {
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerYPct = centerYPct;
            this.btnWidthPct = btnWidthPct;
            this.btnHeightPct = btnHeightPct;
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.mapMode = mapMode;
        }

        @Override
        public String toString() {
            return "canvas=" + canvasWidth + "x" + canvasHeight
                    + " zone=[" + left + "," + top + " " + width + "x" + height + "]"
                    + " center=" + centerX + "," + centerY
                    + " ref=" + REF_CANVAS_W + "x" + REF_CANVAS_H + " scale=" + String.format("%.3f", scale)
                    + " offset=" + offsetX + "," + offsetY + " mode=" + mapMode
                    + " Y%=" + centerYPct + " W%=" + btnWidthPct + " H%=" + btnHeightPct;
        }
    }

    private static int[] resolveJagexPlayNowPercents() {
        int yPct = 52;
        int wPct = 28;
        int hPct = 11;
        LoneBotConfig cfg = RelogRuntime.config;
        if (cfg != null) {
            yPct = clampPct(cfg.jagexLoginPlayNowCenterYPct(), 35, 70);
            wPct = clampPct(cfg.jagexLoginPlayNowBtnWidthPct(), 12, 50);
            hPct = clampPct(cfg.jagexLoginPlayNowBtnHeightPct(), 5, 25);
        }
        return new int[] { yPct, wPct, hPct };
    }

    private static int clampPct(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static LoneBotConfig.JagexLoginPlayNowMapMode resolveConfiguredMapMode() {
        LoneBotConfig cfg = RelogRuntime.config;
        if (cfg != null) {
            LoneBotConfig.JagexLoginPlayNowMapMode mode = cfg.jagexLoginPlayNowMapMode();
            if (mode != null) {
                return mode;
            }
        }
        return LoneBotConfig.JagexLoginPlayNowMapMode.AUTO;
    }

    private static final class MapTransform {
        final double scaleX;
        final double scaleY;
        final int offsetX;
        final int offsetY;
        /** Y = refCenterY + offsetY (geen scaleY) — Play Now blijft op ~262 bij breed fullscreen. */
        final boolean fixedRefY;
        final String modeLabel;

        MapTransform(double scaleX, double scaleY, int offsetX, int offsetY,
                     boolean fixedRefY, String modeLabel) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.fixedRefY = fixedRefY;
            this.modeLabel = modeLabel;
        }

        double scale() {
            return scaleX;
        }
    }

    private static LoneBotConfig.JagexLoginPlayNowMapMode effectiveMapMode(
            int canvasWidth, int canvasHeight, LoneBotConfig.JagexLoginPlayNowMapMode configured) {
        if (configured != LoneBotConfig.JagexLoginPlayNowMapMode.AUTO) {
            return configured;
        }
        boolean tallFixedWidth = canvasWidth <= REF_CANVAS_W * 1.08
                && canvasHeight > REF_CANVAS_H * 1.06;
        if (tallFixedWidth) {
            return LoneBotConfig.JagexLoginPlayNowMapMode.TOP_ANCHOR;
        }
        if (canvasWidth > REF_CANVAS_W * 1.08) {
            return LoneBotConfig.JagexLoginPlayNowMapMode.WIDTH_STRETCH;
        }
        return LoneBotConfig.JagexLoginPlayNowMapMode.LETTERBOX;
    }

    private static MapTransform mapTransform(int canvasWidth, int canvasHeight,
                                             LoneBotConfig.JagexLoginPlayNowMapMode mode) {
        LoneBotConfig.JagexLoginPlayNowMapMode effective = effectiveMapMode(canvasWidth, canvasHeight, mode);
        String autoPrefix = mode == LoneBotConfig.JagexLoginPlayNowMapMode.AUTO
                ? "auto→" : "";
        switch (effective) {
            case TOP_ANCHOR:
                return new MapTransform(
                        canvasWidth / (double) REF_CANVAS_W,
                        1.0, 0, 0, true,
                        autoPrefix + "top-anchor");
            case WIDTH_STRETCH:
                return new MapTransform(
                        canvasWidth / (double) REF_CANVAS_W,
                        1.0, 0, 0, true,
                        autoPrefix + "width-fixed-y");
            case LETTERBOX:
            default:
                double scaleX = canvasWidth / (double) REF_CANVAS_W;
                double scaleY = canvasHeight / (double) REF_CANVAS_H;
                double scale = Math.min(scaleX, scaleY);
                int gameW = (int) Math.round(REF_CANVAS_W * scale);
                int gameH = (int) Math.round(REF_CANVAS_H * scale);
                return new MapTransform(scale, scale, (canvasWidth - gameW) / 2,
                        (canvasHeight - gameH) / 2, false,
                        autoPrefix + "letterbox");
        }
    }

    private static JagexPlayNowTarget buildTarget(int canvasWidth, int canvasHeight, int centerYPct,
                                                  int btnWidthPct, int btnHeightPct, MapTransform transform) {
        int refCenterX = REF_CANVAS_W / 2;
        int refCenterY = (int) Math.round(REF_CANVAS_H * (centerYPct / 100.0));
        int refBtnW = Math.max(72, (int) Math.round(REF_CANVAS_W * (btnWidthPct / 100.0)));
        int refBtnH = Math.max(20, (int) Math.round(REF_CANVAS_H * (btnHeightPct / 100.0)));

        int centerX = transform.offsetX + (int) Math.round(refCenterX * transform.scaleX);
        int centerY = transform.fixedRefY
                ? transform.offsetY + refCenterY
                : transform.offsetY + (int) Math.round(refCenterY * transform.scaleY);
        double btnScaleY = transform.fixedRefY ? 1.0 : transform.scaleY;
        int btnW = Math.max(24, (int) Math.round(refBtnW * transform.scaleX));
        int btnH = Math.max(14, (int) Math.round(refBtnH * btnScaleY));

        int left = centerX - btnW / 2;
        int top = centerY - btnH / 2;
        if (left < 0) {
            left = 0;
        }
        if (top < 0) {
            top = 0;
        }
        int maxX = Math.min(left + btnW, canvasWidth);
        int maxY = Math.min(top + btnH, canvasHeight);
        int w = maxX - left;
        int h = maxY - top;
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new JagexPlayNowTarget(canvasWidth, canvasHeight, left, top, w, h,
                centerX, centerY, centerYPct, btnWidthPct, btnHeightPct,
                transform.scale(), transform.offsetX, transform.offsetY, transform.modeLabel);
    }

    public static JagexPlayNowTarget computeJagexLoginPlayNowTarget(Canvas canvas) {
        if (canvas == null) {
            return null;
        }
        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return null;
        }
        int[] pct = resolveJagexPlayNowPercents();
        MapTransform transform = mapTransform(canvasWidth, canvasHeight, resolveConfiguredMapMode());
        return buildTarget(canvasWidth, canvasHeight, pct[0], pct[1], pct[2], transform);
    }

    /** Logt scherm/venster/canvas + alle schaal-kandidaten voor kalibratie (Debug bron Login). */
    public static void logJagexPlayNowCalibration(Canvas canvas) {
        List<String> lines = new ArrayList<>();
        lines.add("=== Jagex Play Now kalibratie ===");
        if (canvas == null) {
            lines.add("canvas: null");
            RelogLog.logBlock("Login", lines);
            return;
        }
        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        lines.add("canvas(client): " + cw + "x" + ch
                + " aspect=" + String.format("%.3f", cw / (double) Math.max(1, ch))
                + " ref=" + REF_CANVAS_W + "x" + REF_CANVAS_H
                + " refAspect=" + String.format("%.3f", REF_CANVAS_W / (double) REF_CANVAS_H));

        try {
            GraphicsConfiguration gc = canvas.getGraphicsConfiguration();
            if (gc != null) {
                lines.add("dpiScale: " + String.format("%.3f", gc.getDefaultTransform().getScaleX()));
            }
        } catch (Throwable ignored) {
        }

        try {
            java.awt.Window win = javax.swing.SwingUtilities.getWindowAncestor(canvas);
            if (win != null) {
                lines.add("gameWindow: " + win.getWidth() + "x" + win.getHeight()
                        + " @ screen(" + win.getX() + "," + win.getY() + ")"
                        + (win instanceof java.awt.Frame && ((java.awt.Frame) win).getExtendedState()
                        == java.awt.Frame.MAXIMIZED_BOTH ? " MAXIMIZED" : ""));
            }
        } catch (Throwable ignored) {
        }

        try {
            Point canvasOnScreen = canvas.getLocationOnScreen();
            lines.add("canvasScreen: " + cw + "x" + ch + " @ (" + canvasOnScreen.x + "," + canvasOnScreen.y + ")");
        } catch (Throwable ignored) {
        }

        try {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            lines.add("screen(dev): " + screen.width + "x" + screen.height);
        } catch (Throwable ignored) {
        }

        int[] pct = resolveJagexPlayNowPercents();
        LoneBotConfig.JagexLoginPlayNowMapMode configured = resolveConfiguredMapMode();
        lines.add("config mapMode=" + configured + " Y%=" + pct[0] + " W%=" + pct[1] + " H%=" + pct[2]);
        lines.add("--- schaal-kandidaten (center X,Y) ---");

        for (LoneBotConfig.JagexLoginPlayNowMapMode candidate
                : LoneBotConfig.JagexLoginPlayNowMapMode.values()) {
            if (candidate == LoneBotConfig.JagexLoginPlayNowMapMode.AUTO) {
                continue;
            }
            MapTransform t = mapTransform(cw, ch, candidate);
            JagexPlayNowTarget tgt = buildTarget(cw, ch, pct[0], pct[1], pct[2], t);
            if (tgt != null) {
                lines.add(candidate.name() + ": center=" + tgt.centerX + "," + tgt.centerY
                        + " scaleX=" + String.format("%.3f", t.scaleX)
                        + (t.fixedRefY ? " fixedY" : " scaleY=" + String.format("%.3f", t.scaleY))
                        + " offset=" + t.offsetX + "," + t.offsetY);
            }
        }

        JagexPlayNowTarget active = computeJagexLoginPlayNowTarget(canvas);
        if (active != null) {
            lines.add("--- actief ---");
            lines.add(active.toString());
        }
        lines.add("=== einde kalibratie ===");
        RelogLog.logBlock("Login", lines);
    }

    /**
     * Debug-tab: log login-state + klikzone + één proportionele klik op grijze Play Now.
     */
    public static void debugTestJagexLoginPlayNow() {
        WelcomeScreenPlayHelper.debugLogLoginState();
        if (!Game.isOnLoginScreen() || Game.isLoggedIn()) {
            RelogLog.log("Login", "Test Jagex Play Now: open eerst het grijze login-scherm "
                    + "(Welcome to RuneScape + Play Now + accountnaam). isOnLoginScreen="
                    + safeBool(() -> Game.isOnLoginScreen()) + " isLoggedIn=" + safeBool(Game::isLoggedIn));
            return;
        }
        Canvas canvas = Client.getCanvas();
        logJagexPlayNowCalibration(canvas);
        JagexPlayNowTarget target = computeJagexLoginPlayNowTarget(canvas);
        if (target == null) {
            RelogLog.log("Login", "Test Jagex Play Now: geen klikzone");
            return;
        }
        WelcomeScreenPlayHelper.tryFocusGameWindow();
        boolean clicked = clickJagexLoginPlayNow();
        RelogLog.log("Login", "Test Jagex Play Now: Mouse.click=" + clicked
                + " — werkt fullscreen niet? probeer schaal-modus WIDTH_STRETCH of LETTERBOX in Debug/Accounts");
    }

    private static boolean safeBool(java.util.function.BooleanSupplier s) {
        try {
            return s.getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void tickJagexLoginStuckState() {
        try {
            if (!Game.isOnLoginScreen() || Game.isLoggedIn()) {
                jagexLoginStuckRetries = 0;
                lastJagexPlayClickMs = 0L;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void recordJagexPlayClickAttempt() {
        long now = System.currentTimeMillis();
        if (lastJagexPlayClickMs > 0L && now - lastJagexPlayClickMs < JAGEX_PLAY_STUCK_WINDOW_MS) {
            jagexLoginStuckRetries++;
        } else {
            jagexLoginStuckRetries = 0;
        }
        lastJagexPlayClickMs = now;
    }

    private static boolean performJagexLoginPlayNowClick(JagexPlayNowTarget target, String logTag) {
        if (target == null) {
            return false;
        }
        int x = target.centerX + RANDOM.nextInt(15) - 7;
        int y = target.centerY + RANDOM.nextInt(15) - 7;
        x = Math.max(target.left, Math.min(target.left + target.width - 1, x));
        y = Math.max(target.top, Math.min(target.top + target.height - 1, y));
        boolean clicked = RelogClick.leftClickAt(x, y, true);
        if (!clicked) {
            RelogClick.mouseClick(x, y, true);
            clicked = true;
        }
        RelogLog.log("Login", logTag + " @ " + x + "," + y + " (center±7) | " + target);
        return clicked;
    }

    /**
     * Grijze "Play Now" — primair pad (ref 765×503, width-fixed-y, geen screenshot).
     */
    public static boolean clickJagexLoginPlayNow() {
        WelcomeScreenPlayHelper.tryFocusGameWindow();
        return performJagexLoginPlayNowClick(
                computeJagexLoginPlayNowTarget(Client.getCanvas()),
                "Jagex Play Now");
    }

    /**
     * Fallback als screenshot-canvas of eerdere klik niet doorzet (account-switch / scherm uit / fullscreen).
     */
    public static boolean clickJagexLoginPlayNowFallback(String reason) {
        WelcomeScreenPlayHelper.tryFocusGameWindow();
        Canvas canvas = Client.getCanvas();
        logJagexPlayNowCalibration(canvas);
        JagexPlayNowTarget target = computeJagexLoginPlayNowTarget(canvas);
        if (target == null) {
            RelogLog.log("Login", "Jagex Play Now fallback (" + reason + "): geen klikzone");
            return false;
        }
        return performJagexLoginPlayNowClick(target, "Jagex Play Now fallback (" + reason + ")");
    }

    /**
     * Klik op grijze/groene Play-knop via canvas-screenshot (template, kleur).
     * Jagex-login (grijze Play Now) → altijd {@link #clickJagexLoginPlayNowFallback} (geen screenshot).
     * Welkomst-lobby: bij mislukte capture → zelfde fallback.
     */
    public static boolean clickPlayNowOnCanvas(String reason) {
        if (Game.isOnLoginScreen() && !Game.isLoggedIn()) {
            return clickJagexLoginPlayNowFallback("canvas-screenshot overgeslagen (" + reason + ")");
        }

        long now = System.currentTimeMillis();
        if (now - lastCanvasFocusAttemptMs >= CANVAS_FOCUS_INTERVAL_MS) {
            lastCanvasFocusAttemptMs = now;
            WelcomeScreenPlayHelper.tryFocusGameWindow();
        }

        Canvas canvas = Client.getCanvas();
        if (canvas == null) {
            return false;
        }

        BufferedImage capture = captureCanvas();
        boolean unreliableCapture = isUnreliableCapture(capture);
        if (capture != null && !unreliableCapture) {
            BufferedImage template = getTemplate();
            if (template != null) {
                Point p = findPlayButtonByTemplate(capture, template);
                if (p != null) {
                    int x = p.x + RANDOM.nextInt(11) - 5;
                    int y = p.y + RANDOM.nextInt(11) - 5;
                    RelogClick.mouseClick(Math.max(0, x), Math.max(0, y), true);
                    RelogLog.log("Login", "Play Now (" + reason + ") template @ " + x + "," + y);
                    return true;
                }
            }
            Point p = findPlayButtonByWhiteText(capture);
            if (p == null) {
                p = findPlayButtonByColor(capture);
            }
            if (p != null) {
                int x = p.x + RANDOM.nextInt(11) - 5;
                int y = p.y + RANDOM.nextInt(11) - 5;
                RelogClick.mouseClick(Math.max(0, x), Math.max(0, y), true);
                RelogLog.log("Login", "Play Now (" + reason + ") detectie @ " + x + "," + y);
                return true;
            }
        } else if (unreliableCapture) {
            RelogLog.log("Login", "Play Now (" + reason + ") capture onbetrouwbaar — Jagex proportioneel fallback");
            return clickJagexLoginPlayNowFallback("capture onbetrouwbaar");
        }

        if (Game.isOnLoginScreen() && !Game.isLoggedIn()) {
            return clickJagexLoginPlayNowFallback("geen template-match");
        }

        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();
        int halfWidth = BUTTON_WIDTH / 2;
        int buttonX = (canvasWidth / 2) - halfWidth;
        int buttonY = BUTTON_Y;
        if (buttonX < 0) {
            buttonX = 0;
        }
        if (buttonY < 0) {
            buttonY = 0;
        }
        int maxX = Math.min(buttonX + BUTTON_WIDTH, canvasWidth);
        int maxY = Math.min(buttonY + BUTTON_HEIGHT, canvasHeight);
        int w = maxX - buttonX;
        int h = maxY - buttonY;
        if (w <= 0 || h <= 0) {
            return false;
        }
        int x = buttonX + RANDOM.nextInt(w);
        int y = buttonY + RANDOM.nextInt(h);
        RelogClick.mouseClick(x, y, true);
        RelogLog.log("Login", "Play Now (" + reason + ") vaste coords @ " + x + "," + y);
        return true;
    }
}
