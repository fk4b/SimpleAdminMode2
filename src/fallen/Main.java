/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  arc.Core
 *  arc.Events
 *  arc.graphics.Color
 *  arc.graphics.Pixmap
 *  arc.scene.style.Drawable
 *  arc.scene.ui.TextField
 *  arc.scene.ui.TextField$TextFieldFilter
 *  arc.util.Strings
 *  arc.util.Threads
 *  mindustry.Vars
 *  mindustry.content.Blocks
 *  mindustry.game.EventType$ClientLoadEvent
 *  mindustry.gen.Icon
 *  mindustry.gen.Tex
 *  mindustry.mod.Mod
 *  mindustry.ui.dialogs.BaseDialog
 *  mindustry.world.blocks.distribution.Sorter
 *  mindustry.world.blocks.logic.CanvasBlock
 *  mindustry.world.blocks.logic.LogicBlock
 *  mindustry.world.blocks.logic.LogicDisplay
 *  mindustry.world.blocks.logic.TileableLogicDisplay
 */
package fallen;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.scene.style.Drawable;
import arc.scene.ui.TextField;
import arc.util.Strings;
import arc.util.Threads;
import fallen.Exporter;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.mod.Mod;
import mindustry.ui.FileChooser;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.logic.CanvasBlock;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import mindustry.world.blocks.logic.TileableLogicDisplay;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

public class Main
extends Mod {
    public static LogicDisplay coreDisplay;
    public static int coreSize;
    public static int coreSpeed;
    public static int coreQuality;
    public static boolean coreHsv;
    public static boolean coreUseGray;
    public static int coreScaling;
    public static int coreGridWidth;
    public static int coreGridHeight;
    public static LogicBlock coreProcessor;
    public static boolean coreMixedProcessors;
    public static int coreOutputMode;
    public static CanvasBlock coreCanvas;
    public static Sorter coreSorter;
    public static Pixmap currentImage;

    public void init() {
        coreDisplay = (LogicDisplay)Blocks.largeLogicDisplay;
        coreProcessor = (LogicBlock)Blocks.microProcessor;
        coreCanvas = (CanvasBlock)Blocks.largeCanvas;
        coreSorter = (Sorter)Blocks.sorter;
        coreSize = Main.coreDisplay.displaySize;
        Events.on(EventType.ClientLoadEvent.class, e -> Vars.ui.schematics.buttons.button("Create Anime", (Drawable)Icon.paste, this::showMainDialog));
    }

    void showMainDialog() {
        BaseDialog ptl = new BaseDialog("CreateAnime");
        ptl.cont.pane(t -> {
            t.defaults().pad(4.0f).growX();
            t.table(Tex.buttonTrans, img -> {
                img.add((CharSequence)"[coral]1. Select Image:[]").left().row();
                img.table(row -> {
                    row.button(currentImage == null ? "Choose File..." : "Change Image", (Drawable)Icon.file, () -> FileChooser.open("png", "jpg", "jpeg", "gif", "bmp", "webp").title("Select Image").submit(file -> {
                        try {
                            this.applyImage(new Pixmap(file), ptl);
                        }
                        catch (Exception ex) {
                            Vars.ui.showException((Throwable)ex);
                        }
                    })).size(280.0f, 50.0f);
                    row.button((Drawable)Icon.copy, () -> this.pasteFromClipboard(ptl)).size(50.0f, 50.0f).tooltip("Paste from clipboard");
                });
            }).row();
            t.table(Tex.buttonTrans, sc -> {
                sc.add((CharSequence)"[coral]2. Scaling Mode:[]").left().row();
                sc.label(() -> {
                    if (coreScaling == 0) {
                        return "Stretch (Distort)";
                    }
                    if (coreScaling == 1) {
                        return "Crop (Fill Display)";
                    }
                    return "Letterbox (Fit Entirely)";
                }).color(Color.lightGray).row();
                sc.slider(0.0f, 2.0f, 1.0f, (float)coreScaling, n -> {
                    coreScaling = (int)n;
                }).width(280.0f);
            }).row();
            t.table(Tex.buttonTrans, tech -> {
                tech.add((CharSequence)"[coral]3. Quality & Speed:[]").left().row();
                tech.table(q -> {
                    q.add((CharSequence)"Quality: ").left();
                    q.label(() -> String.valueOf(coreQuality)).color(Color.gray);
                }).row();
                tech.slider(0.0f, 255.0f, 1.0f, (float)coreQuality, n -> {
                    coreQuality = (int)n;
                }).width(280.0f).row();
                tech.table(s -> {
                    s.add((CharSequence)"Proc. Lines: ").left();
                    ((TextField)s.field(String.valueOf(coreSpeed), str -> {
                        coreSpeed = Strings.parseInt((String)str, (int)1000);
                    }).width(100.0f).get()).setFilter(TextField.TextFieldFilter.digitsOnly);
                }).row();
                tech.check("Gray Transparency", coreUseGray, b -> {
                    coreUseGray = b;
                }).left().row();
                tech.check("Use HSV Indexing", coreHsv, b -> {
                    coreHsv = b;
                }).disabled(b -> coreQuality == 255).left().row();
            }).row();
            t.table(Tex.buttonTrans, out -> {
                out.add((CharSequence)"[coral]4. Output Type:[]").left().row();
                out.button(this.outputModeName(), () -> {
                    BaseDialog sel = new BaseDialog("Select Output");
                    sel.cont.button("\u0414\u0438\u0441\u043f\u043b\u0435\u0438", () -> {
                        coreOutputMode = 0;
                        sel.hide();
                        ptl.hide();
                        this.showMainDialog();
                    }).size(250.0f, 60.0f).row();
                    sel.cont.button("\u0425\u043e\u043b\u0441\u0442\u044b", () -> {
                        coreOutputMode = 1;
                        sel.hide();
                        ptl.hide();
                        this.showMainDialog();
                    }).size(250.0f, 60.0f).row();
                    sel.cont.button("\u0421\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u0449\u0438\u043a\u0438", () -> {
                        coreOutputMode = 2;
                        sel.hide();
                        ptl.hide();
                        this.showMainDialog();
                    }).size(250.0f, 60.0f).row();
                    sel.addCloseButton();
                    sel.show();
                }).size(280.0f, 60.0f);
            }).row();
            t.table(Tex.buttonTrans, proc -> {
                proc.add((CharSequence)"[coral]4. Processor Type:[]").left().row();
                proc.button(coreMixedProcessors ? "\u041c\u0438\u043a\u0440\u043e + \u043b\u043e\u0433\u0438\u0447\u0435\u0441\u043a\u0438\u0439" : Main.coreProcessor.localizedName, () -> {
                    BaseDialog sel = new BaseDialog("Select Processor");
                    sel.cont.button(Blocks.microProcessor.localizedName, () -> {
                        coreProcessor = (LogicBlock)Blocks.microProcessor;
                        coreMixedProcessors = false;
                        sel.hide();
                        ptl.hide();
                        this.showMainDialog();
                    }).size(250.0f, 60.0f).row();
                    sel.cont.button(Blocks.logicProcessor.localizedName, () -> {
                        coreProcessor = (LogicBlock)Blocks.logicProcessor;
                        coreMixedProcessors = false;
                        sel.hide();
                        ptl.hide();
                        this.showMainDialog();
                    }).size(250.0f, 60.0f).row();
                    sel.cont.button(Blocks.hyperProcessor.localizedName, () -> {
                        coreProcessor = (LogicBlock)Blocks.hyperProcessor;
                        coreMixedProcessors = false;
                        sel.hide();
                        ptl.hide();
                        this.showMainDialog();
                    }).size(250.0f, 60.0f).row();
                    sel.cont.button("\u041c\u0438\u043a\u0440\u043e + \u043b\u043e\u0433\u0438\u0447\u0435\u0441\u043a\u0438\u0439", () -> {
                        coreProcessor = (LogicBlock)Blocks.logicProcessor;
                        coreMixedProcessors = true;
                        sel.hide();
                        ptl.hide();
                        this.showMainDialog();
                    }).size(250.0f, 60.0f).row();
                    sel.addCloseButton();
                    sel.show();
                }).size(280.0f, 60.0f);
            }).row();
            t.table(Tex.buttonTrans, disp -> {
                disp.add((CharSequence)"[coral]5. Target Display:[]").left().row();
                disp.button(Main.coreDisplay.localizedName, () -> {
                    BaseDialog sel = new BaseDialog("Select Display");
                    Vars.content.blocks().each(b -> b instanceof LogicDisplay, b -> sel.cont.button(b.localizedName, () -> {
                        coreDisplay = (LogicDisplay)b;
                        coreSize = Main.coreDisplay.displaySize;
                        if (coreDisplay instanceof TileableLogicDisplay) {
                            coreGridWidth = Math.min(coreGridWidth, ((TileableLogicDisplay)Main.coreDisplay).maxDisplayDimensions);
                            coreGridHeight = Math.min(coreGridHeight, ((TileableLogicDisplay)Main.coreDisplay).maxDisplayDimensions);
                        }
                        sel.hide();
                        ptl.hide();
                        this.showMainDialog();
                    }).size(250.0f, 60.0f).row());
                    sel.addCloseButton();
                    sel.show();
                }).size(280.0f, 60.0f);
            }).row();
            t.table(Tex.buttonTrans, grid -> {
                grid.add((CharSequence)"[coral]6. Grid Size (Width x Height):[]").left().row();
                grid.label(() -> coreGridWidth + " x " + coreGridHeight).color(Color.lightGray).row();
                grid.table(w -> {
                    w.add((CharSequence)"W: ").left();
                    ((TextField)w.field(String.valueOf(coreGridWidth), str -> {
                        coreGridWidth = this.clampGridSize(Strings.parseInt((String)str, (int)1));
                    }).width(120.0f).get()).setFilter(TextField.TextFieldFilter.digitsOnly);
                }).row();
                grid.table(h -> {
                    h.add((CharSequence)"H: ").left();
                    ((TextField)h.field(String.valueOf(coreGridHeight), str -> {
                        coreGridHeight = this.clampGridSize(Strings.parseInt((String)str, (int)1));
                    }).width(120.0f).get()).setFilter(TextField.TextFieldFilter.digitsOnly);
                }).row();
            }).row();
        }).grow();
        ptl.addCloseButton();
        ptl.buttons.button("EXPORT", (Drawable)Icon.export, () -> Threads.daemon((String)"PickMe worker", () -> {
            try {
                Exporter.export(currentImage);
                Core.app.post(() -> ((BaseDialog)ptl).hide());
            }
            catch (Exception ex) {
                Core.app.post(() -> Vars.ui.showException((Throwable)ex));
            }
        })).size(180.0f, 60.0f).disabled(b -> currentImage == null);
        ptl.show();
    }

    void pasteFromClipboard(BaseDialog ptl) {
        try {
            Pixmap loaded = this.loadFromClipboard();
            if (loaded == null) {
                Vars.ui.showInfo("Clipboard does not contain an image or file.");
                return;
            }
            this.applyImage(loaded, ptl);
        }
        catch (Exception ex) {
            Vars.ui.showException((Throwable)ex);
        }
    }

    void applyImage(Pixmap loaded, BaseDialog ptl) {
        try {
            if (currentImage != null) {
                currentImage.dispose();
            }
            currentImage = loaded;
            ptl.hide();
            this.showMainDialog();
        }
        catch (Exception ex) {
            if (loaded != null && loaded != currentImage) {
                loaded.dispose();
            }
            Vars.ui.showException((Throwable)ex);
        }
    }

    /**
     * Clipboard image/file access uses reflection so Main has no hard AWT references.
     * Mindustry's child-first ModClassLoader cannot resolve java.awt.* from mod bytecode.
     */
    Pixmap loadFromClipboard() throws Exception {
        // 1) Windows Forms/PowerShell first: reliable for copied images AND files
        try {
            Pixmap fromWin = loadFromWindowsClipboard();
            if (fromWin != null) {
                return fromWin;
            }
        }
        catch (Throwable ignored) {
        }

        // 2) AWT clipboard via reflection (files + images)
        try {
            Pixmap fromAwt = loadFromAwtClipboard();
            if (fromAwt != null) {
                return fromAwt;
            }
        }
        catch (Throwable ignored) {
        }

        // 3) Plain text path in Arc clipboard
        String text = Core.app.getClipboardText();
        if (text != null) {
            text = text.trim();
            if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
                text = text.substring(1, text.length() - 1);
            }
            if (!text.isEmpty()) {
                Fi file = new Fi(text);
                if (file.exists() && !file.isDirectory()) {
                    return new Pixmap(file);
                }
            }
        }
        return null;
    }

    static Class<?> jdkClass(String name) throws ClassNotFoundException {
        // Platform CL sees java.desktop on modular JDKs; never use the mod loader.
        try {
            Method m = ClassLoader.class.getMethod("getPlatformClassLoader");
            ClassLoader platform = (ClassLoader)m.invoke(null);
            return Class.forName(name, true, platform);
        }
        catch (Throwable ignored) {
        }
        try {
            return Class.forName(name, true, ClassLoader.getSystemClassLoader());
        }
        catch (Throwable ignored) {
        }
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        while (cl != null) {
            try {
                return Class.forName(name, true, cl);
            }
            catch (Throwable ignored) {
            }
            cl = cl.getParent();
        }
        throw new ClassNotFoundException(name);
    }

    static Pixmap loadFromAwtClipboard() throws Exception {
        System.setProperty("java.awt.headless", "false");
        Class<?> toolkitClass = jdkClass("java.awt.Toolkit");
        Class<?> flavorClass = jdkClass("java.awt.datatransfer.DataFlavor");
        Object toolkit = toolkitClass.getMethod("getDefaultToolkit").invoke(null);
        Object clipboard = toolkitClass.getMethod("getSystemClipboard").invoke(toolkit);

        Object fileListFlavor = flavorClass.getField("javaFileListFlavor").get(null);
        Object imageFlavor = flavorClass.getField("imageFlavor").get(null);

        // Direct getData is more reliable than getContents on some systems.
        try {
            if (Boolean.TRUE.equals(clipboard.getClass().getMethod("isDataFlavorAvailable", flavorClass)
                .invoke(clipboard, fileListFlavor))) {
                List<?> files = (List<?>)clipboard.getClass().getMethod("getData", flavorClass)
                    .invoke(clipboard, fileListFlavor);
                if (files != null && !files.isEmpty()) {
                    Object first = files.get(0);
                    if (first instanceof File) {
                        return new Pixmap(new Fi((File)first));
                    }
                    if (first != null) {
                        Fi file = new Fi(String.valueOf(first));
                        if (file.exists() && !file.isDirectory()) {
                            return new Pixmap(file);
                        }
                    }
                }
            }
        }
        catch (Throwable ignored) {
        }

        try {
            if (Boolean.TRUE.equals(clipboard.getClass().getMethod("isDataFlavorAvailable", flavorClass)
                .invoke(clipboard, imageFlavor))) {
                Object image = clipboard.getClass().getMethod("getData", flavorClass)
                    .invoke(clipboard, imageFlavor);
                if (image != null) {
                    return pixmapFromAwtImage(image);
                }
            }
        }
        catch (Throwable ignored) {
        }

        Object contents = clipboard.getClass().getMethod("getContents", Object.class).invoke(clipboard, new Object[]{null});
        if (contents == null) {
            return null;
        }

        Method isSupported = contents.getClass().getMethod("isDataFlavorSupported", flavorClass);
        Method getData = contents.getClass().getMethod("getTransferData", flavorClass);

        if (Boolean.TRUE.equals(isSupported.invoke(contents, fileListFlavor))) {
            List<?> files = (List<?>)getData.invoke(contents, fileListFlavor);
            if (files != null && !files.isEmpty()) {
                Object first = files.get(0);
                if (first instanceof File) {
                    return new Pixmap(new Fi((File)first));
                }
                if (first != null) {
                    Fi file = new Fi(String.valueOf(first));
                    if (file.exists() && !file.isDirectory()) {
                        return new Pixmap(file);
                    }
                }
            }
        }

        if (Boolean.TRUE.equals(isSupported.invoke(contents, imageFlavor))) {
            Object image = getData.invoke(contents, imageFlavor);
            if (image != null) {
                return pixmapFromAwtImage(image);
            }
        }
        return null;
    }

    static Pixmap pixmapFromAwtImage(Object image) throws Exception {
        Class<?> imageClass = jdkClass("java.awt.Image");
        Class<?> observerClass = jdkClass("java.awt.image.ImageObserver");
        Class<?> bufferedClass = jdkClass("java.awt.image.BufferedImage");
        Class<?> renderedClass = jdkClass("java.awt.image.RenderedImage");
        Class<?> imageIoClass = jdkClass("javax.imageio.ImageIO");

        int width = ((Number)imageClass.getMethod("getWidth", observerClass).invoke(image, new Object[]{null})).intValue();
        int height = ((Number)imageClass.getMethod("getHeight", observerClass).invoke(image, new Object[]{null})).intValue();
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("Invalid clipboard image size.");
        }

        final int typeArgb = bufferedClass.getField("TYPE_INT_ARGB").getInt(null);
        final int typeRgb = bufferedClass.getField("TYPE_INT_RGB").getInt(null);
        Object buffered;
        if (bufferedClass.isInstance(image)) {
            buffered = image;
            int type = ((Number)bufferedClass.getMethod("getType").invoke(buffered)).intValue();
            if (type != typeArgb && type != typeRgb) {
                Object converted = bufferedClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(width, height, typeArgb);
                Object g = bufferedClass.getMethod("createGraphics").invoke(converted);
                g.getClass().getMethod("drawImage", imageClass, int.class, int.class, observerClass)
                    .invoke(g, image, 0, 0, null);
                g.getClass().getMethod("dispose").invoke(g);
                buffered = converted;
            }
        } else {
            buffered = bufferedClass.getConstructor(int.class, int.class, int.class)
                .newInstance(width, height, typeArgb);
            Object g = bufferedClass.getMethod("createGraphics").invoke(buffered);
            g.getClass().getMethod("drawImage", imageClass, int.class, int.class, observerClass)
                .invoke(g, image, 0, 0, null);
            g.getClass().getMethod("dispose").invoke(g);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Boolean ok = (Boolean)imageIoClass.getMethod("write", renderedClass, String.class, java.io.OutputStream.class)
            .invoke(null, buffered, "png", out);
        if (!Boolean.TRUE.equals(ok)) {
            throw new IllegalStateException("Failed to encode clipboard image.");
        }
        return new Pixmap(out.toByteArray());
    }

    static Pixmap loadFromWindowsClipboard() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return null;
        }

        // Write script to temp file — avoids quoting hell with -Command.
        // STA is required for WinForms clipboard image access.
        File scriptFile = File.createTempFile("pickme-clip-", ".ps1");
        String script =
            "Add-Type -AssemblyName System.Windows.Forms\r\n" +
            "Add-Type -AssemblyName System.Drawing\r\n" +
            "$ErrorActionPreference = 'Stop'\r\n" +
            "try {\r\n" +
            "  if ([System.Windows.Forms.Clipboard]::ContainsFileDropList()) {\r\n" +
            "    $files = [System.Windows.Forms.Clipboard]::GetFileDropList()\r\n" +
            "    if ($files -and $files.Count -gt 0 -and (Test-Path -LiteralPath $files[0])) {\r\n" +
            "      [Console]::Out.Write($files[0])\r\n" +
            "      exit 0\r\n" +
            "    }\r\n" +
            "  }\r\n" +
            "  $img = $null\r\n" +
            "  if ([System.Windows.Forms.Clipboard]::ContainsImage()) {\r\n" +
            "    $img = [System.Windows.Forms.Clipboard]::GetImage()\r\n" +
            "  }\r\n" +
            "  if ($img -eq $null) {\r\n" +
            "    $obj = [System.Windows.Forms.Clipboard]::GetDataObject()\r\n" +
            "    if ($obj -ne $null) {\r\n" +
            "      if ($obj.GetDataPresent([System.Windows.Forms.DataFormats]::Bitmap)) {\r\n" +
            "        $img = $obj.GetData([System.Windows.Forms.DataFormats]::Bitmap)\r\n" +
            "      } elseif ($obj.GetDataPresent('PNG')) {\r\n" +
            "        $raw = $obj.GetData('PNG')\r\n" +
            "        if ($raw -is [System.IO.MemoryStream]) {\r\n" +
            "          $raw.Position = 0\r\n" +
            "          $img = [System.Drawing.Image]::FromStream($raw)\r\n" +
            "        } elseif ($raw -is [byte[]]) {\r\n" +
            "          $ms = New-Object System.IO.MemoryStream(,$raw)\r\n" +
            "          $img = [System.Drawing.Image]::FromStream($ms)\r\n" +
            "        }\r\n" +
            "      }\r\n" +
            "    }\r\n" +
            "  }\r\n" +
            "  if ($img -ne $null) {\r\n" +
            "    $path = Join-Path $env:TEMP ('pickme-clip-' + [guid]::NewGuid().ToString() + '.png')\r\n" +
            "    $img.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)\r\n" +
            "    [Console]::Out.Write($path)\r\n" +
            "    exit 0\r\n" +
            "  }\r\n" +
            "  exit 1\r\n" +
            "} catch {\r\n" +
            "  [Console]::Error.WriteLine($_.Exception.Message)\r\n" +
            "  exit 2\r\n" +
            "}\r\n";

        try {
            java.nio.file.Files.writeString(scriptFile.toPath(), script, java.nio.charset.StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-STA",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-File", scriptFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] bytes = p.getInputStream().readAllBytes();
            int code = p.waitFor();
            if (code != 0) {
                return null;
            }
            String path = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (path.isEmpty()) {
                return null;
            }
            // Keep first non-empty line that looks like a path.
            for (String line : path.split("\\R")) {
                line = line.trim();
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1).trim();
                }
                if (line.isEmpty()) {
                    continue;
                }
                // Strip optional quotes.
                if (line.startsWith("\"") && line.endsWith("\"") && line.length() >= 2) {
                    line = line.substring(1, line.length() - 1);
                }
                Fi file = new Fi(line);
                if (file.exists() && !file.isDirectory()) {
                    Pixmap pixmap = new Pixmap(file);
                    if (line.toLowerCase().contains("pickme-clip-") && line.toLowerCase().endsWith(".png")) {
                        try {
                            file.delete();
                        }
                        catch (Throwable ignored) {
                        }
                    }
                    return pixmap;
                }
            }
            return null;
        }
        finally {
            try {
                scriptFile.delete();
            }
            catch (Throwable ignored) {
            }
        }
    }

    int clampGridSize(int value) {
        int max = coreOutputMode == 0 && coreDisplay instanceof TileableLogicDisplay ? ((TileableLogicDisplay)Main.coreDisplay).maxDisplayDimensions : Integer.MAX_VALUE;
        return Math.max(1, Math.min(value, max));
    }

    String outputModeName() {
        if (coreOutputMode == 1) {
            return "\u0425\u043e\u043b\u0441\u0442\u044b";
        }
        if (coreOutputMode == 2) {
            return "\u0421\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u0449\u0438\u043a\u0438";
        }
        return "\u0414\u0438\u0441\u043f\u043b\u0435\u0438";
    }

    static {
        coreSize = 80;
        coreSpeed = 999;
        coreQuality = 255;
        coreHsv = false;
        coreUseGray = false;
        coreScaling = 0;
        coreGridWidth = 1;
        coreGridHeight = 1;
        coreMixedProcessors = false;
        coreOutputMode = 0;
        currentImage = null;
    }
}

