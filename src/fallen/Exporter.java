/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  arc.Core
 *  arc.graphics.Color
 *  arc.graphics.Pixmap
 *  arc.struct.ObjectMap
 *  arc.struct.ObjectMap$Entry
 *  arc.struct.Seq
 *  arc.struct.StringMap
 *  arc.util.Log
 *  mindustry.Vars
 *  mindustry.content.Blocks
 *  mindustry.content.Liquids
 *  mindustry.game.Schematic
 *  mindustry.game.Schematic$Stile
 *  mindustry.game.Team
 *  mindustry.type.Item
 *  mindustry.world.Block
 *  mindustry.world.Tile
 *  mindustry.world.blocks.distribution.Sorter
 *  mindustry.world.blocks.logic.CanvasBlock
 *  mindustry.world.blocks.logic.LogicBlock
 *  mindustry.world.blocks.logic.LogicBlock$LogicBuild
 *  mindustry.world.blocks.logic.LogicBlock$LogicLink
 *  mindustry.world.blocks.logic.TileableLogicDisplay
 */
package fallen;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.Log;
import fallen.Main;
import fallen.Processor;
import fallen.RectInt;
import java.util.Comparator;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.game.Schematic;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.logic.CanvasBlock;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.TileableLogicDisplay;

public class Exporter {
    public static void export(Pixmap input) {
        if (input == null) {
            return;
        }
        if (Main.coreOutputMode == 1) {
            Exporter.exportCanvasBlocks(input);
            return;
        }
        if (Main.coreOutputMode == 2) {
            Exporter.exportSorterBlocks(input);
            return;
        }
        if (Main.coreDisplay instanceof TileableLogicDisplay) {
            Exporter.exportTileable(input, (TileableLogicDisplay)Main.coreDisplay);
            return;
        }
        int displayPixels = Main.coreDisplay.displaySize;
        final int gridWidth = Math.max(1, Main.coreGridWidth);
        final int gridHeight = Math.max(1, Main.coreGridHeight);
        int canvasWidth = displayPixels * gridWidth;
        int canvasHeight = displayPixels * gridHeight;
        Pixmap pixmap = new Pixmap(canvasWidth, canvasHeight);
        try {
            int index;
            int gx;
            int gy;
            pixmap.fill(Color.rgba8888((float)0.0f, (float)0.0f, (float)0.0f, (float)1.0f));
            int srcW = input.width;
            int srcH = input.height;
            if (Main.coreScaling == 0) {
                pixmap.draw(input, 0, 0, srcW, srcH, 0, 0, canvasWidth, canvasHeight);
            } else if (Main.coreScaling == 1) {
                float srcRatio = (float)srcW / (float)srcH;
                float canvasRatio = (float)canvasWidth / (float)canvasHeight;
                int srcX = 0;
                int srcY = 0;
                int srcPartW = srcW;
                int srcPartH = srcH;
                if (srcRatio > canvasRatio) {
                    srcPartW = (int)((float)srcH * canvasRatio);
                    srcX = (srcW - srcPartW) / 2;
                } else {
                    srcPartH = (int)((float)srcW / canvasRatio);
                    srcY = (srcH - srcPartH) / 2;
                }
                pixmap.draw(input, srcX, srcY, srcPartW, srcPartH, 0, 0, canvasWidth, canvasHeight);
            } else {
                float ratio = Math.min((float)canvasWidth / (float)srcW, (float)canvasHeight / (float)srcH);
                int drawW = (int)((float)srcW * ratio);
                int drawH = (int)((float)srcH * ratio);
                int drawX = (canvasWidth - drawW) / 2;
                int drawY = (canvasHeight - drawH) / 2;
                pixmap.draw(input, 0, 0, srcW, srcH, drawX, drawY, drawW, drawH);
            }
            boolean mixedProcessors = Main.coreMixedProcessors;
            LogicBlock microProcessor = (LogicBlock)Blocks.microProcessor;
            LogicBlock logicProcessor = (LogicBlock)Blocks.logicProcessor;
            LogicBlock processor = mixedProcessors ? logicProcessor : (Main.coreProcessor == null ? microProcessor : Main.coreProcessor);
            ObjectMap<String, Seq<RectInt>> optimized = Processor.process(pixmap);
            CodeWriter[] writers = new CodeWriter[gridWidth * gridHeight];
            boolean[][] claimedPixels = new boolean[gridWidth * gridHeight][displayPixels * displayPixels];
            for (int i = 0; i < writers.length; ++i) {
                writers[i] = new CodeWriter(i, Exporter.processorLineLimit(), Exporter.flushInterval(processor));
            }
            for (ObjectMap.Entry<String, Seq<RectInt>> entry : optimized.entries()) {
                String colorCmd = entry.key;
                for (RectInt rect : entry.value) {
                    Exporter.addRectToDisplays(writers, claimedPixels, colorCmd, rect, displayPixels, gridWidth, gridHeight, canvasWidth, canvasHeight);
                }
            }
            Seq<CodeBlock> codeBlocks = new Seq<>();
            for (CodeWriter writer : writers) {
                codeBlocks.addAll(writer.finish());
            }
            int procSize = processor.size;
            int displayBlockSize = Main.coreDisplay.size;
            int cellGap = 0;
            int cellPitch = displayBlockSize + cellGap;
            int layoutWidth = (gridWidth - 1) * cellPitch + displayBlockSize;
            int layoutHeight = (gridHeight - 1) * cellPitch + displayBlockSize;
            int processorSlotsTarget = Math.max(codeBlocks.size, codeBlocks.size * 2);
            int minDim = (int)Math.ceil(Math.sqrt(processorSlotsTarget * procSize * procSize + layoutWidth * layoutHeight));
            int dim = Math.max(minDim, Math.max(layoutWidth, layoutHeight));
            int layoutMinX = (dim - layoutWidth) / 2;
            int layoutMinY = (dim - layoutHeight) / 2;
            int layoutMaxX = layoutMinX + layoutWidth - 1;
            int layoutMaxY = layoutMinY + layoutHeight - 1;
            int[] displayX = new int[gridWidth * gridHeight];
            int[] displayY = new int[gridWidth * gridHeight];
            DisplayArea[] displayAreas = new DisplayArea[gridWidth * gridHeight];
            Seq<Schematic.Stile> tiles = new Seq<>();
            for (int gy2 = 0; gy2 < gridHeight; ++gy2) {
                for (int gx2 = 0; gx2 < gridWidth; ++gx2) {
                    int index2 = gy2 * gridWidth + gx2;
                    int physicalGy = gridHeight - 1 - gy2;
                    int minX = layoutMinX + gx2 * cellPitch;
                    int minY = layoutMinY + physicalGy * cellPitch;
                    displayAreas[index2] = new DisplayArea(minX, minY, minX + displayBlockSize - 1, minY + displayBlockSize - 1);
                }
            }
            boolean needsCryoSource = !mixedProcessors && processor == Blocks.hyperProcessor;
            int microRangeTiles = Exporter.processorRangeTiles(microProcessor, displayBlockSize);
            int logicRangeTiles = Exporter.processorRangeTiles(logicProcessor, displayBlockSize);
            int singleRangeTiles = Exporter.processorRangeTiles(processor, displayBlockSize);
            Seq<ProcessorPos> processorPositions = null;
            Seq<ProcessorPos> microProcessorPositions = null;
            // Grow layout until every code block can get an in-range processor.
            // Mixed: micros only in micro-range band; logic uses longer range / outer ring.
            int growGuard = 0;
            while (growGuard++ < 512) {
                int growCenterX = (layoutMinX + layoutMaxX) / 2;
                int growCenterY = (layoutMinY + layoutMaxY) / 2;
                if (mixedProcessors) {
                    microProcessorPositions = Exporter.processorPositions(dim, displayAreas, microProcessor.size, false, microRangeTiles);
                    processorPositions = Exporter.processorPositions(dim, displayAreas, logicProcessor.size, false, logicRangeTiles);
                    if (Exporter.countMixedPlaceable(codeBlocks, microProcessorPositions, processorPositions, displayAreas, microRangeTiles, logicRangeTiles, growCenterX, growCenterY) >= codeBlocks.size) {
                        break;
                    }
                } else {
                    microProcessorPositions = null;
                    processorPositions = Exporter.processorPositions(dim, displayAreas, procSize, needsCryoSource, singleRangeTiles);
                    if (processorPositions.size >= codeBlocks.size) {
                        break;
                    }
                }
                layoutMinX = (++dim - layoutWidth) / 2;
                layoutMinY = (dim - layoutHeight) / 2;
                layoutMaxX = layoutMinX + layoutWidth - 1;
                layoutMaxY = layoutMinY + layoutHeight - 1;
                for (gy = 0; gy < gridHeight; ++gy) {
                    for (gx = 0; gx < gridWidth; ++gx) {
                        index = gy * gridWidth + gx;
                        int physicalGy = gridHeight - 1 - gy;
                        int minX = layoutMinX + gx * cellPitch;
                        int minY = layoutMinY + physicalGy * cellPitch;
                        displayAreas[index] = new DisplayArea(minX, minY, minX + displayBlockSize - 1, minY + displayBlockSize - 1);
                    }
                }
            }
            for (gy = 0; gy < gridHeight; ++gy) {
                for (gx = 0; gx < gridWidth; ++gx) {
                    index = gy * gridWidth + gx;
                    DisplayArea area = displayAreas[index];
                    int x = Exporter.tileAnchor(area.minX, displayBlockSize);
                    int y = Exporter.tileAnchor(area.minY, displayBlockSize);
                    displayX[index] = x;
                    displayY[index] = y;
                    tiles.add(new Schematic.Stile((Block)Main.coreDisplay, x, y, null, (byte)0));
                }
            }
            int placementCenterX = (layoutMinX + layoutMaxX) / 2;
            int placementCenterY = (layoutMinY + layoutMaxY) / 2;
            int finalWidth = layoutMaxX;
            int finalHeight = layoutMaxY;
            Seq<ProcessorPos> usedPositions = new Seq<>();
            Seq<SourcePos> usedSources = new Seq<>();
            Exporter.sortCodeBlocksByCenter(codeBlocks);
            for (CodeBlock code : codeBlocks) {
                Placement placement;
                int display = code.display;
                DisplayArea linkArea = displayAreas[display];
                LogicBlock blockProcessor = processor;
                if (mixedProcessors) {
                    // Prefer micro when it can reach; otherwise logic (longer range / outer ring).
                    placement = Exporter.takeNearestPlacementAround(microProcessorPositions, usedPositions, usedSources, displayAreas, linkArea, placementCenterX, placementCenterY, microProcessor.size, microRangeTiles, false);
                    if (placement != null) {
                        blockProcessor = microProcessor;
                    } else {
                        placement = Exporter.takeNearestPlacementAround(processorPositions, usedPositions, usedSources, displayAreas, linkArea, placementCenterX, placementCenterY, logicProcessor.size, logicRangeTiles, false);
                        blockProcessor = logicProcessor;
                    }
                } else {
                    placement = Exporter.takeNearestPlacementAround(processorPositions, usedPositions, usedSources, displayAreas, linkArea, placementCenterX, placementCenterY, procSize, singleRangeTiles, needsCryoSource);
                }
                if (placement == null) continue;
                ProcessorPos pos = placement.processor;
                // Do not create Tile/Building — triggers nested ObjectSet iterators (updateProximity).
                byte[] config = Exporter.logicConfig(code.code, pos.x, pos.y, displayX[display], displayY[display]);
                tiles.add(new Schematic.Stile((Block)blockProcessor, pos.x, pos.y, config, (byte)0));
                if (placement.source != null) {
                    SourcePos source = placement.source;
                    tiles.add(new Schematic.Stile(Blocks.liquidSource, source.x, source.y, Liquids.cryofluid, (byte)0));
                    finalWidth = Math.max(finalWidth, source.x);
                    finalHeight = Math.max(finalHeight, source.y);
                }
                finalWidth = Math.max(finalWidth, pos.maxX);
                finalHeight = Math.max(finalHeight, pos.maxY);
            }
            final int schemDim = dim;
            Schematic schem = new Schematic(tiles, new StringMap(){
                {
                    this.put("name", "_PickMe " + gridWidth + "x" + gridHeight + " " + schemDim);
                }
            }, finalWidth + 1, finalHeight + 1);
            Vars.schematics.add(schem);
            Core.app.post(() -> {
                Vars.ui.schematics.hide();
                Vars.control.input.useSchematic(schem);
            });
        }
        catch (Exception e) {
            Log.err((String)"Export error", (Throwable)e);
            throw e;
        }
        finally {
            pixmap.dispose();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static void exportCanvasBlocks(Pixmap input) {
        CanvasBlock canvas = (CanvasBlock)Blocks.largeCanvas;
        int blockPixels = canvas.canvasSize;
        int blockSize = canvas.size;
        final int gridWidth = Math.max(1, Main.coreGridWidth);
        final int gridHeight = Math.max(1, Main.coreGridHeight);
        int canvasWidth = blockPixels * gridWidth;
        int canvasHeight = blockPixels * gridHeight;
        Pixmap pixmap = Exporter.preparePixmap(input, canvasWidth, canvasHeight);
        try {
            Seq<Schematic.Stile> tiles = new Seq<>();
            for (int gy = 0; gy < gridHeight; ++gy) {
                for (int gx = 0; gx < gridWidth; ++gx) {
                    byte[] data = new byte[(int)Math.ceil((float)(blockPixels * blockPixels * canvas.bitsPerPixel) / 8.0f)];
                    for (int py = 0; py < blockPixels; ++py) {
                        for (int px = 0; px < blockPixels; ++px) {
                            int color = pixmap.get(gx * blockPixels + px, gy * blockPixels + py);
                            int index = Exporter.nearestCanvasColor(canvas, color);
                            Exporter.setPackedCanvasPixel(data, (py * blockPixels + px) * canvas.bitsPerPixel, index, canvas.bitsPerPixel);
                        }
                    }
                    int physicalY = gridHeight - 1 - gy;
                    tiles.add(new Schematic.Stile((Block)canvas, gx * blockSize, physicalY * blockSize, data, (byte)0));
                }
            }
            Schematic schem = new Schematic(tiles, new StringMap(){
                {
                    this.put("name", "_PickMe canvas " + gridWidth + "x" + gridHeight);
                }
            }, gridWidth * blockSize, gridHeight * blockSize);
            Exporter.useSchematic(schem);
        }
        finally {
            pixmap.dispose();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static void exportSorterBlocks(Pixmap input) {
        Sorter sorter = Main.coreSorter == null ? (Sorter)Blocks.sorter : Main.coreSorter;
        final int width = Math.max(1, Main.coreGridWidth);
        final int height = Math.max(1, Main.coreGridHeight);
        Pixmap pixmap = Exporter.preparePixmap(input, width, height);
        try {
            Seq items = Vars.content.items();
            Seq<Schematic.Stile> tiles = new Seq<>();
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    Item item = Exporter.nearestItem((Seq<Item>)items, pixmap.get(x, y));
                    int physicalY = height - 1 - y;
                    tiles.add(new Schematic.Stile((Block)sorter, x, physicalY, item, (byte)0));
                }
            }
            Schematic schem = new Schematic(tiles, new StringMap(){
                {
                    this.put("name", "_PickMe sorter " + width + "x" + height);
                }
            }, width, height);
            Exporter.useSchematic(schem);
        }
        finally {
            pixmap.dispose();
        }
    }

    static Pixmap preparePixmap(Pixmap input, int canvasWidth, int canvasHeight) {
        Pixmap pixmap = new Pixmap(canvasWidth, canvasHeight);
        pixmap.fill(Color.rgba8888((float)0.0f, (float)0.0f, (float)0.0f, (float)1.0f));
        int srcW = input.width;
        int srcH = input.height;
        if (Main.coreScaling == 0) {
            pixmap.draw(input, 0, 0, srcW, srcH, 0, 0, canvasWidth, canvasHeight);
        } else if (Main.coreScaling == 1) {
            float srcRatio = (float)srcW / (float)srcH;
            float canvasRatio = (float)canvasWidth / (float)canvasHeight;
            int srcX = 0;
            int srcY = 0;
            int srcPartW = srcW;
            int srcPartH = srcH;
            if (srcRatio > canvasRatio) {
                srcPartW = (int)((float)srcH * canvasRatio);
                srcX = (srcW - srcPartW) / 2;
            } else {
                srcPartH = (int)((float)srcW / canvasRatio);
                srcY = (srcH - srcPartH) / 2;
            }
            pixmap.draw(input, srcX, srcY, srcPartW, srcPartH, 0, 0, canvasWidth, canvasHeight);
        } else {
            float ratio = Math.min((float)canvasWidth / (float)srcW, (float)canvasHeight / (float)srcH);
            int drawW = (int)((float)srcW * ratio);
            int drawH = (int)((float)srcH * ratio);
            int drawX = (canvasWidth - drawW) / 2;
            int drawY = (canvasHeight - drawH) / 2;
            pixmap.draw(input, 0, 0, srcW, srcH, drawX, drawY, drawW, drawH);
        }
        return pixmap;
    }

    static int nearestCanvasColor(CanvasBlock canvas, int color) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < canvas.palette.length; ++i) {
            int distance = Exporter.colorDistance(color, canvas.palette[i]);
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = i;
        }
        return best;
    }

    static Item nearestItem(Seq<Item> items, int color) {
        Item best = (Item)items.first();
        int bestDistance = Integer.MAX_VALUE;
        for (Item item : items) {
            int distance = Exporter.colorDistance(color, item.color.rgba8888());
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = item;
        }
        return best;
    }

    static int colorDistance(int a, int b) {
        int ar = a >>> 24 & 0xFF;
        int ag = a >>> 16 & 0xFF;
        int ab = a >>> 8 & 0xFF;
        int br = b >>> 24 & 0xFF;
        int bg = b >>> 16 & 0xFF;
        int bb = b >>> 8 & 0xFF;
        int dr = ar - br;
        int dg = ag - bg;
        int db = ab - bb;
        return dr * dr + dg * dg + db * db;
    }

    static void setPackedCanvasPixel(byte[] data, int bitOffset, int value, int bits) {
        for (int i = 0; i < bits; ++i) {
            int bit = value >>> i & 1;
            int offset = bitOffset + i;
            int byteIndex = offset / 8;
            int bitIndex = offset % 8;
            data[byteIndex] = bit == 1 ? (byte)(data[byteIndex] | 1 << bitIndex) : (byte)(data[byteIndex] & ~(1 << bitIndex));
        }
    }

    static void useSchematic(Schematic schem) {
        Vars.schematics.add(schem);
        Core.app.post(() -> {
            Vars.ui.schematics.hide();
            Vars.control.input.useSchematic(schem);
        });
    }

    static void exportTileable(Pixmap input, TileableLogicDisplay display) {
        final int tilesWide = Math.max(1, Math.min(Main.coreGridWidth, display.maxDisplayDimensions));
        final int tilesHigh = Math.max(1, Math.min(Main.coreGridHeight, display.maxDisplayDimensions));
        int frame = display.frameSize;
        int canvasWidth = tilesWide * display.displaySize - frame * 2;
        int canvasHeight = tilesHigh * display.displaySize - frame * 2;
        Pixmap pixmap = new Pixmap(canvasWidth, canvasHeight);
        try {
            pixmap.fill(Color.rgba8888((float)0.0f, (float)0.0f, (float)0.0f, (float)1.0f));
            int srcW = input.width;
            int srcH = input.height;
            if (Main.coreScaling == 0) {
                pixmap.draw(input, 0, 0, srcW, srcH, 0, 0, canvasWidth, canvasHeight);
            } else if (Main.coreScaling == 1) {
                float srcRatio = (float)srcW / (float)srcH;
                float canvasRatio = (float)canvasWidth / (float)canvasHeight;
                int srcX = 0;
                int srcY = 0;
                int srcPartW = srcW;
                int srcPartH = srcH;
                if (srcRatio > canvasRatio) {
                    srcPartW = (int)((float)srcH * canvasRatio);
                    srcX = (srcW - srcPartW) / 2;
                } else {
                    srcPartH = (int)((float)srcW / canvasRatio);
                    srcY = (srcH - srcPartH) / 2;
                }
                pixmap.draw(input, srcX, srcY, srcPartW, srcPartH, 0, 0, canvasWidth, canvasHeight);
            } else {
                float ratio = Math.min((float)canvasWidth / (float)srcW, (float)canvasHeight / (float)srcH);
                int drawW = (int)((float)srcW * ratio);
                int drawH = (int)((float)srcH * ratio);
                int drawX = (canvasWidth - drawW) / 2;
                int drawY = (canvasHeight - drawH) / 2;
                pixmap.draw(input, 0, 0, srcW, srcH, drawX, drawY, drawW, drawH);
            }
            boolean mixedProcessors = Main.coreMixedProcessors;
            LogicBlock microProcessor = (LogicBlock)Blocks.microProcessor;
            LogicBlock logicProcessor = (LogicBlock)Blocks.logicProcessor;
            LogicBlock processor = mixedProcessors ? logicProcessor : (Main.coreProcessor == null ? microProcessor : Main.coreProcessor);
            CodeWriter writer = new CodeWriter(0, Exporter.processorLineLimit(), Exporter.flushInterval(processor));
            boolean[] claimedPixels = new boolean[canvasWidth * canvasHeight];
            ObjectMap<String, Seq<RectInt>> optimized = Processor.process(pixmap);
            for (ObjectMap.Entry<String, Seq<RectInt>> entry : optimized.entries()) {
                String colorCmd = entry.key;
                for (RectInt rect : entry.value) {
                    Exporter.addRectToTileable(writer, claimedPixels, colorCmd, rect, canvasWidth, canvasHeight);
                }
            }
            Seq<CodeBlock> codeBlocks = writer.finish();
            int procSize = processor.size;
            int layoutWidth = tilesWide;
            int layoutHeight = tilesHigh;
            int processorSlotsTarget = Math.max(codeBlocks.size, codeBlocks.size * 2);
            int minDim = (int)Math.ceil(Math.sqrt(processorSlotsTarget * procSize * procSize + layoutWidth * layoutHeight));
            int dim = Math.max(minDim, Math.max(layoutWidth, layoutHeight));
            int layoutMinX = (dim - layoutWidth) / 2;
            int layoutMinY = (dim - layoutHeight) / 2;
            int layoutMaxX = layoutMinX + layoutWidth - 1;
            int layoutMaxY = layoutMinY + layoutHeight - 1;
            DisplayArea[] displayAreas = new DisplayArea[]{new DisplayArea(layoutMinX, layoutMinY, layoutMaxX, layoutMaxY)};
            boolean needsCryoSource = !mixedProcessors && processor == Blocks.hyperProcessor;
            int microRangeTiles = Exporter.processorRangeTiles(microProcessor, 1);
            int logicRangeTiles = Exporter.processorRangeTiles(logicProcessor, 1);
            int singleRangeTiles = Exporter.processorRangeTiles(processor, 1);
            Seq<ProcessorPos> processorPositions = null;
            Seq<ProcessorPos> microProcessorPositions = null;
            int growGuardTileable = 0;
            while (growGuardTileable++ < 512) {
                int growCenterX = (layoutMinX + layoutMaxX) / 2;
                int growCenterY = (layoutMinY + layoutMaxY) / 2;
                if (mixedProcessors) {
                    microProcessorPositions = Exporter.processorPositions(dim, displayAreas, microProcessor.size, false, microRangeTiles);
                    processorPositions = Exporter.processorPositions(dim, displayAreas, logicProcessor.size, false, logicRangeTiles);
                    if (Exporter.countMixedPlaceable(codeBlocks, microProcessorPositions, processorPositions, displayAreas, microRangeTiles, logicRangeTiles, growCenterX, growCenterY) >= codeBlocks.size) {
                        break;
                    }
                } else {
                    microProcessorPositions = null;
                    processorPositions = Exporter.processorPositions(dim, displayAreas, procSize, needsCryoSource, singleRangeTiles);
                    if (processorPositions.size >= codeBlocks.size) {
                        break;
                    }
                }
                layoutMinX = (++dim - layoutWidth) / 2;
                layoutMinY = (dim - layoutHeight) / 2;
                layoutMaxX = layoutMinX + layoutWidth - 1;
                layoutMaxY = layoutMinY + layoutHeight - 1;
                displayAreas[0] = new DisplayArea(layoutMinX, layoutMinY, layoutMaxX, layoutMaxY);
            }
            Seq<Schematic.Stile> tiles = new Seq<>();
            for (int y = 0; y < tilesHigh; ++y) {
                for (int x = 0; x < tilesWide; ++x) {
                    tiles.add(new Schematic.Stile((Block)display, layoutMinX + x, layoutMinY + y, null, (byte)0));
                }
            }
            DisplayArea displayArea = displayAreas[0];
            int placementCenterX = (layoutMinX + layoutMaxX) / 2;
            int placementCenterY = (layoutMinY + layoutMaxY) / 2;
            int finalWidth = layoutMaxX;
            int finalHeight = layoutMaxY;
            Seq<ProcessorPos> usedPositions = new Seq<>();
            Seq<SourcePos> usedSources = new Seq<>();
            Exporter.sortCodeBlocksByCenter(codeBlocks);
            for (CodeBlock code : codeBlocks) {
                Placement placement;
                LogicBlock blockProcessor = processor;
                if (mixedProcessors) {
                    placement = Exporter.takeNearestPlacementAround(microProcessorPositions, usedPositions, usedSources, displayAreas, displayArea, placementCenterX, placementCenterY, microProcessor.size, microRangeTiles, false);
                    if (placement != null) {
                        blockProcessor = microProcessor;
                    } else {
                        placement = Exporter.takeNearestPlacementAround(processorPositions, usedPositions, usedSources, displayAreas, displayArea, placementCenterX, placementCenterY, logicProcessor.size, logicRangeTiles, false);
                        blockProcessor = logicProcessor;
                    }
                } else {
                    placement = Exporter.takeNearestPlacementAround(processorPositions, usedPositions, usedSources, displayAreas, displayArea, placementCenterX, placementCenterY, procSize, singleRangeTiles, needsCryoSource);
                }
                if (placement == null) continue;
                ProcessorPos pos = placement.processor;
                // Config without Building/Tile (avoids nested iterator crash in updateProximity).
                byte[] config = Exporter.logicConfig(code.code, pos.x, pos.y, placement.linkX, placement.linkY);
                tiles.add(new Schematic.Stile((Block)blockProcessor, pos.x, pos.y, config, (byte)0));
                if (placement.source != null) {
                    SourcePos source = placement.source;
                    tiles.add(new Schematic.Stile(Blocks.liquidSource, source.x, source.y, Liquids.cryofluid, (byte)0));
                    finalWidth = Math.max(finalWidth, source.x);
                    finalHeight = Math.max(finalHeight, source.y);
                }
                finalWidth = Math.max(finalWidth, pos.maxX);
                finalHeight = Math.max(finalHeight, pos.maxY);
            }
            final int schemDim = dim;
            Schematic schem = new Schematic(tiles, new StringMap(){
                {
                    this.put("name", "_PickMe tileable " + tilesWide + "x" + tilesHigh + " " + schemDim);
                }
            }, finalWidth + 1, finalHeight + 1);
            Vars.schematics.add(schem);
            Core.app.post(() -> {
                Vars.ui.schematics.hide();
                Vars.control.input.useSchematic(schem);
            });
        }
        catch (Exception e) {
            Log.err((String)"Tileable export error", (Throwable)e);
            throw e;
        }
        finally {
            pixmap.dispose();
        }
    }

    static void addRectToTileable(CodeWriter writer, boolean[] claimedPixels, String colorCmd, RectInt rect, int canvasWidth, int canvasHeight) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int x2 = Math.min(rect.x + rect.width, canvasWidth);
        int y2 = Math.min(rect.y + rect.height, canvasHeight);
        if (x >= x2 || y >= y2) {
            return;
        }
        int width = x2 - x;
        int height = y2 - y;
        int drawY = canvasHeight - y - height;
        Exporter.addOwnedRectCanvas(writer, claimedPixels, colorCmd, x, drawY, width, height, canvasWidth, x, y, canvasWidth, canvasHeight);
    }

    static void addOwnedRectCanvas(CodeWriter writer, boolean[] claimed, String colorCmd, int x, int y, int width, int height, int stride, int globalX, int globalTopY, int canvasWidth, int canvasHeight) {
        int col;
        int row;
        boolean overlapsClaimed = false;
        block0: for (row = 0; row < height && !overlapsClaimed; ++row) {
            for (col = 0; col < width; ++col) {
                if (!claimed[x + col + (y + row) * stride]) continue;
                overlapsClaimed = true;
                continue block0;
            }
        }
        if (!overlapsClaimed) {
            for (row = 0; row < height; ++row) {
                for (col = 0; col < width; ++col) {
                    claimed[x + col + (y + row) * stride] = true;
                }
            }
            int centerX = globalX * 2 + width - canvasWidth;
            int centerY = globalTopY * 2 + height - canvasHeight;
            writer.addRect(colorCmd, x, y, width, height, centerX * centerX + centerY * centerY);
            return;
        }
        for (row = 0; row < height; ++row) {
            int runStart = -1;
            for (int col2 = 0; col2 < width; ++col2) {
                int px = x + col2;
                int py = y + row;
                int index = px + py * stride;
                if (!claimed[index]) {
                    if (runStart < 0) {
                        runStart = col2;
                    }
                    claimed[index] = true;
                    continue;
                }
                if (runStart < 0) continue;
                Exporter.addOwnedRun(writer, colorCmd, x + runStart, y + row, col2 - runStart, globalX + runStart, globalTopY + height - 1 - row, canvasWidth, canvasHeight);
                runStart = -1;
            }
            if (runStart < 0) continue;
            Exporter.addOwnedRun(writer, colorCmd, x + runStart, y + row, width - runStart, globalX + runStart, globalTopY + height - 1 - row, canvasWidth, canvasHeight);
        }
    }

    static void addRectToDisplays(CodeWriter[] writers, boolean[][] claimedPixels, String colorCmd, RectInt rect, int displayPixels, int gridWidth, int gridHeight, int canvasWidth, int canvasHeight) {
        int startGX = Math.max(0, rect.x / displayPixels);
        int endGX = Math.min(gridWidth - 1, (rect.x + rect.width - 1) / displayPixels);
        int startGY = Math.max(0, rect.y / displayPixels);
        int endGY = Math.min(gridHeight - 1, (rect.y + rect.height - 1) / displayPixels);
        for (int gy = startGY; gy <= endGY; ++gy) {
            for (int gx = startGX; gx <= endGX; ++gx) {
                int cellX = gx * displayPixels;
                int cellY = gy * displayPixels;
                int x1 = Math.max(rect.x, cellX);
                int y1 = Math.max(rect.y, cellY);
                int x2 = Math.min(rect.x + rect.width, cellX + displayPixels);
                int y2 = Math.min(rect.y + rect.height, cellY + displayPixels);
                if (x1 >= x2 || y1 >= y2) continue;
                int localX = x1 - cellX;
                int localTopY = y1 - cellY;
                int width = x2 - x1;
                int height = y2 - y1;
                int drawY = displayPixels - localTopY - height;
                int displayIndex = gy * gridWidth + gx;
                Exporter.addOwnedRect(writers[displayIndex], claimedPixels[displayIndex], colorCmd, localX, drawY, width, height, displayPixels, x1, y1, canvasWidth, canvasHeight);
            }
        }
    }

    static void addOwnedRect(CodeWriter writer, boolean[] claimed, String colorCmd, int x, int y, int width, int height, int displayPixels, int globalX, int globalTopY, int canvasWidth, int canvasHeight) {
        int col;
        int row;
        boolean overlapsClaimed = false;
        block0: for (row = 0; row < height && !overlapsClaimed; ++row) {
            for (col = 0; col < width; ++col) {
                if (!claimed[x + col + (y + row) * displayPixels]) continue;
                overlapsClaimed = true;
                continue block0;
            }
        }
        if (!overlapsClaimed) {
            for (row = 0; row < height; ++row) {
                for (col = 0; col < width; ++col) {
                    claimed[x + col + (y + row) * displayPixels] = true;
                }
            }
            int centerX = globalX * 2 + width - canvasWidth;
            int centerY = globalTopY * 2 + height - canvasHeight;
            writer.addRect(colorCmd, x, y, width, height, centerX * centerX + centerY * centerY);
            return;
        }
        for (row = 0; row < height; ++row) {
            int runStart = -1;
            for (int col2 = 0; col2 < width; ++col2) {
                int px = x + col2;
                int py = y + row;
                int index = px + py * displayPixels;
                if (!claimed[index]) {
                    if (runStart < 0) {
                        runStart = col2;
                    }
                    claimed[index] = true;
                    continue;
                }
                if (runStart < 0) continue;
                Exporter.addOwnedRun(writer, colorCmd, x + runStart, y + row, col2 - runStart, globalX + runStart, globalTopY + height - 1 - row, canvasWidth, canvasHeight);
                runStart = -1;
            }
            if (runStart < 0) continue;
            Exporter.addOwnedRun(writer, colorCmd, x + runStart, y + row, width - runStart, globalX + runStart, globalTopY + height - 1 - row, canvasWidth, canvasHeight);
        }
    }

    static void addOwnedRun(CodeWriter writer, String colorCmd, int x, int y, int width, int globalX, int globalY, int canvasWidth, int canvasHeight) {
        int centerX = globalX * 2 + width - canvasWidth;
        int centerY = globalY * 2 + 1 - canvasHeight;
        writer.addRect(colorCmd, x, y, width, 1, centerX * centerX + centerY * centerY);
    }

    static int[] displayOrder(int[] displayX, int[] displayY) {
        Seq<Integer> order = new Seq<>();
        int centerX = 0;
        int centerY = 0;
        for (int i2 = 0; i2 < displayX.length; ++i2) {
            order.add(i2);
            centerX += displayX[i2] * 2;
            centerY += displayY[i2] * 2;
        }
        int doubledCenterX = centerX / Math.max(1, displayX.length);
        int doubledCenterY = centerY / Math.max(1, displayY.length);
        order.sort(Comparator.comparingInt((Integer i) -> {
            int dx = displayX[i] * 2 - doubledCenterX;
            int dy = displayY[i] * 2 - doubledCenterY;
            return dx * dx + dy * dy;
        }).thenComparingInt((Integer i) -> displayY[i]).thenComparingInt((Integer i) -> displayX[i]));
        int[] result = new int[order.size];
        for (int i3 = 0; i3 < order.size; ++i3) {
            result[i3] = (Integer)order.get(i3);
        }
        return result;
    }

    /** Prefer code that draws near the image center (lower priority = closer). Stable by generation order. */
    static void sortCodeBlocksByCenter(Seq<CodeBlock> blocks) {
        blocks.sort(Comparator.comparingInt((CodeBlock b) -> b.priority).thenComparingInt(b -> b.order));
    }

    /**
     * Build processor schematic config without allocating a real Building/Tile.
     * Links are stored relative to the processor (same as {@code LogicBuild.config()}).
     */
    static byte[] logicConfig(String code, int procX, int procY, int linkTileX, int linkTileY) {
        Seq<LogicBlock.LogicLink> links = new Seq<>(1);
        links.add(new LogicBlock.LogicLink(linkTileX - procX, linkTileY - procY, "display1", true));
        return LogicBlock.compress(code, links);
    }

    static int processorLineLimit() {
        return Math.max(4, Math.min(Main.coreSpeed, 999));
    }

    static int flushInterval(LogicBlock processor) {
        return 100;
    }

    static int processorRangeTiles(LogicBlock processor) {
        return Exporter.processorRangeTiles(processor, 1);
    }

    /**
     * Tile radius matching {@code LogicBuild.validLink}: world distance
     * {@code range + other.block.size * tilesize / 2}. Slightly conservative floor.
     */
    static int processorRangeTiles(LogicBlock processor, int linkedBlockSize) {
        float tiles = processor.range / 8.0f + linkedBlockSize / 2.0f;
        return Math.max(1, (int)Math.floor(tiles) - 1);
    }

    /** Nearest tile of a display rectangle to a processor anchor (for range + LogicLink). */
    static void nearestDisplayTile(int procX, int procY, DisplayArea area, int[] out) {
        out[0] = Math.max(area.minX, Math.min(area.maxX, procX));
        out[1] = Math.max(area.minY, Math.min(area.maxY, procY));
    }

    static SourcePos adjacentSourcePosition(ProcessorPos pos, Seq<ProcessorPos> processors, Seq<SourcePos> sources, DisplayArea[] displayAreas) {
        for (int x = pos.minX; x <= pos.maxX; ++x) {
            SourcePos bottom = Exporter.trySourcePosition(x, pos.minY - 1, pos, processors, sources, displayAreas);
            if (bottom != null) {
                return bottom;
            }
            SourcePos top = Exporter.trySourcePosition(x, pos.maxY + 1, pos, processors, sources, displayAreas);
            if (top == null) continue;
            return top;
        }
        for (int y = pos.minY; y <= pos.maxY; ++y) {
            SourcePos left = Exporter.trySourcePosition(pos.minX - 1, y, pos, processors, sources, displayAreas);
            if (left != null) {
                return left;
            }
            SourcePos right = Exporter.trySourcePosition(pos.maxX + 1, y, pos, processors, sources, displayAreas);
            if (right == null) continue;
            return right;
        }
        return null;
    }

    static SourcePos preferredSourcePosition(ProcessorPos pos, Seq<ProcessorPos> processors, Seq<SourcePos> sources, DisplayArea[] displayAreas) {
        if (pos.preferredSource == null) {
            return null;
        }
        return Exporter.trySourcePosition(pos.preferredSource.x, pos.preferredSource.y, pos, processors, sources, displayAreas);
    }

    static SourcePos trySourcePosition(int x, int y, ProcessorPos current, Seq<ProcessorPos> processors, Seq<SourcePos> sources, DisplayArea[] displayAreas) {
        if (x < 0 || y < 0) {
            return null;
        }
        for (DisplayArea area : displayAreas) {
            if (x < area.minX || x > area.maxX || y < area.minY || y > area.maxY) continue;
            return null;
        }
        if (x >= current.minX && x <= current.maxX && y >= current.minY && y <= current.maxY) {
            return null;
        }
        for (ProcessorPos pos : processors) {
            if (x < pos.minX || x > pos.maxX || y < pos.minY || y > pos.maxY) continue;
            return null;
        }
        for (SourcePos source : sources) {
            if (source.x != x || source.y != y) continue;
            return null;
        }
        return new SourcePos(x, y);
    }

    static Seq<ProcessorPos> processorPositions(int dim, DisplayArea[] displayAreas, int procSize, boolean reserveSource) {
        return Exporter.processorPositions(dim, displayAreas, procSize, reserveSource, -1);
    }

    /**
     * @param maxRangeTiles if &gt; 0, only keep candidates whose footprint is within this many tiles
     *                      of a display (so micros do not fill the whole schematic and starve logic slots).
     */
    static Seq<ProcessorPos> processorPositions(int dim, DisplayArea[] displayAreas, int procSize, boolean reserveSource, int maxRangeTiles) {
        if (reserveSource) {
            return Exporter.hyperProcessorPositions(dim, displayAreas, procSize);
        }
        int maxDist2 = maxRangeTiles > 0 ? maxRangeTiles * maxRangeTiles : Integer.MAX_VALUE;
        Seq<ProcessorPos> candidates = new Seq<>();
        for (int offsetX = 0; offsetX < procSize; ++offsetX) {
            for (int offsetY = 0; offsetY < procSize; ++offsetY) {
                for (int minX = offsetX; minX <= dim - procSize; minX += procSize) {
                    for (int minY = offsetY; minY <= dim - procSize; minY += procSize) {
                        int dist2 = Exporter.nearestDisplayDistance(minX, minY, procSize, displayAreas);
                        if (dist2 > maxDist2) continue;
                        ProcessorPos pos = new ProcessorPos(minX, minY, procSize, dist2);
                        if (Exporter.overlapsAnyDisplay(pos, displayAreas, procSize)) continue;
                        candidates.add(pos);
                    }
                }
            }
        }
        // Prefer near-display slots, then near layout center — never bias bottom-left (old minY/minX).
        int packCenterX = dim / 2;
        int packCenterY = dim / 2;
        candidates.sort(Comparator
            .comparingInt((ProcessorPos p) -> p.distance)
            .thenComparingInt(p -> {
                int dx = p.x - packCenterX;
                int dy = p.y - packCenterY;
                return dx * dx + dy * dy;
            })
            .thenComparingInt(p -> p.minX)
            .thenComparingInt(p -> p.minY));
        Seq<ProcessorPos> packed = new Seq<>();
        for (ProcessorPos candidate : candidates) {
            boolean blocked = false;
            for (ProcessorPos placed : packed) {
                if (!Exporter.overlaps(candidate.minX, candidate.minY, procSize, placed.minX, placed.maxX, placed.minY, placed.maxY)) continue;
                blocked = true;
                break;
            }
            if (blocked) continue;
            packed.add(candidate);
        }
        return packed;
    }

    /**
     * Dry-run mixed placement (micro first, then logic) without mutating the candidate lists permanently.
     * Returns how many code blocks would get a processor.
     */
    static int countMixedPlaceable(Seq<CodeBlock> codeBlocks, Seq<ProcessorPos> microPositions, Seq<ProcessorPos> logicPositions, DisplayArea[] displayAreas, int microRange, int logicRange, int centerX, int centerY) {
        Seq<ProcessorPos> used = new Seq<>();
        Seq<SourcePos> sources = new Seq<>();
        int placed = 0;
        // Work on a center-sorted copy so capacity matches real export order.
        Seq<CodeBlock> ordered = codeBlocks.copy();
        Exporter.sortCodeBlocksByCenter(ordered);
        for (CodeBlock code : ordered) {
            DisplayArea linkArea = displayAreas[Math.min(code.display, displayAreas.length - 1)];
            Placement p = Exporter.takeNearestPlacementAround(microPositions, used, sources, displayAreas, linkArea, centerX, centerY, 1, microRange, false);
            if (p == null) {
                p = Exporter.takeNearestPlacementAround(logicPositions, used, sources, displayAreas, linkArea, centerX, centerY, 2, logicRange, false);
            }
            if (p != null) {
                ++placed;
            }
        }
        return placed;
    }

    static Seq<ProcessorPos> hyperProcessorPositions(int dim, DisplayArea[] displayAreas, int procSize) {
        Seq<ProcessorPos> candidates = new Seq<>();
        Exporter.addHyperProcessorRows(candidates, dim, displayAreas, procSize, true);
        Exporter.addHyperProcessorRows(candidates, dim, displayAreas, procSize, false);
        Exporter.addHyperProcessorColumns(candidates, dim, displayAreas, procSize, true);
        Exporter.addHyperProcessorColumns(candidates, dim, displayAreas, procSize, false);
        int packCenterX = dim / 2;
        int packCenterY = dim / 2;
        candidates.sort(Comparator
            .comparingInt((ProcessorPos p) -> p.distance)
            .thenComparingInt(p -> {
                int dx = p.x - packCenterX;
                int dy = p.y - packCenterY;
                return dx * dx + dy * dy;
            })
            .thenComparingInt(p -> p.minX)
            .thenComparingInt(p -> p.minY));
        return candidates;
    }

    static void addHyperProcessorRows(Seq<ProcessorPos> candidates, int dim, DisplayArea[] displayAreas, int procSize, boolean sourceRight) {
        int pitchX = procSize + 1;
        int pitchY = procSize;
        for (int offsetX = 0; offsetX < pitchX; ++offsetX) {
            for (int offsetY = 0; offsetY < pitchY; ++offsetY) {
                for (int minX = offsetX; minX <= dim - procSize; minX += pitchX) {
                    for (int minY = offsetY; minY <= dim - procSize; minY += pitchY) {
                        int sourceX = sourceRight ? minX + procSize : minX - 1;
                        int sourceY = minY + procSize / 2;
                        Exporter.addHyperCandidate(candidates, dim, displayAreas, procSize, minX, minY, sourceX, sourceY);
                    }
                }
            }
        }
    }

    static void addHyperProcessorColumns(Seq<ProcessorPos> candidates, int dim, DisplayArea[] displayAreas, int procSize, boolean sourceTop) {
        int pitchX = procSize;
        int pitchY = procSize + 1;
        for (int offsetX = 0; offsetX < pitchX; ++offsetX) {
            for (int offsetY = 0; offsetY < pitchY; ++offsetY) {
                for (int minX = offsetX; minX <= dim - procSize; minX += pitchX) {
                    for (int minY = offsetY; minY <= dim - procSize; minY += pitchY) {
                        int sourceX = minX + procSize / 2;
                        int sourceY = sourceTop ? minY + procSize : minY - 1;
                        Exporter.addHyperCandidate(candidates, dim, displayAreas, procSize, minX, minY, sourceX, sourceY);
                    }
                }
            }
        }
    }

    static void addHyperCandidate(Seq<ProcessorPos> candidates, int dim, DisplayArea[] displayAreas, int procSize, int minX, int minY, int sourceX, int sourceY) {
        if (sourceX < 0 || sourceY < 0 || sourceX > dim || sourceY > dim) {
            return;
        }
        ProcessorPos pos = new ProcessorPos(minX, minY, procSize, Exporter.nearestDisplayDistance(minX, minY, procSize, displayAreas), new SourcePos(sourceX, sourceY));
        if (Exporter.overlapsAnyDisplay(pos, displayAreas, procSize) || Exporter.pointOverlapsAnyDisplay(sourceX, sourceY, displayAreas)) {
            return;
        }
        candidates.add(pos);
    }

    static boolean overlaps(int x, int y, int size, int minX, int maxX, int minY, int maxY) {
        int maxBlockX = x + size - 1;
        int maxBlockY = y + size - 1;
        return maxBlockX >= minX && x <= maxX && maxBlockY >= minY && y <= maxY;
    }

    static int distanceToRect(int x, int y, int size, int minX, int maxX, int minY, int maxY) {
        int maxBlockX = x + size - 1;
        int maxBlockY = y + size - 1;
        int dx = maxBlockX < minX ? minX - maxBlockX : (x > maxX ? x - maxX : 0);
        int dy = maxBlockY < minY ? minY - maxBlockY : (y > maxY ? y - maxY : 0);
        return dx * dx + dy * dy;
    }

    static int nearestDisplayDistance(int x, int y, int size, DisplayArea[] displayAreas) {
        int best = Integer.MAX_VALUE;
        for (DisplayArea area : displayAreas) {
            best = Math.min(best, Exporter.distanceToRect(x, y, size, area.minX, area.maxX, area.minY, area.maxY));
        }
        return best;
    }

    static boolean overlapsAnyDisplay(ProcessorPos pos, DisplayArea[] displayAreas, int procSize) {
        for (DisplayArea area : displayAreas) {
            if (!Exporter.overlaps(pos.minX, pos.minY, procSize, area.minX, area.maxX, area.minY, area.maxY)) continue;
            return true;
        }
        return false;
    }

    static boolean pointOverlapsAnyDisplay(int x, int y, DisplayArea[] displayAreas) {
        for (DisplayArea area : displayAreas) {
            if (x < area.minX || x > area.maxX || y < area.minY || y > area.maxY) continue;
            return true;
        }
        return false;
    }

    static int center(int start, int size) {
        return start * 2 + size - 1;
    }

    static int tileAnchor(int min, int size) {
        return min + (size - 1) / 2;
    }

    static Placement takeNearestPlacement(Seq<ProcessorPos> positions, Seq<ProcessorPos> used, Seq<SourcePos> sources, DisplayArea[] displayAreas, int targetX, int targetY, int procSize, int maxRange, boolean requireSource) {
        return Exporter.takeNearestPlacement(positions, used, sources, displayAreas, targetX, targetY, procSize, maxRange, requireSource, true);
    }

    static Placement takeNearestPlacement(Seq<ProcessorPos> positions, Seq<ProcessorPos> used, Seq<SourcePos> sources, DisplayArea[] displayAreas, int targetX, int targetY, int procSize, int maxRange, boolean requireSource, boolean allowAnyRange) {
        Placement best = Exporter.takeNearestPlacementInternal(positions, used, sources, displayAreas, targetX, targetY, procSize, maxRange, requireSource, true);
        if (best == null && allowAnyRange) {
            best = Exporter.takeNearestPlacementInternal(positions, used, sources, displayAreas, targetX, targetY, procSize, maxRange, requireSource, false);
        }
        if (best != null) {
            used.add(best.processor);
            if (best.source != null) {
                sources.add(best.source);
            }
        }
        return best;
    }

    static Placement takeNearestPlacementScored(Seq<ProcessorPos> positions, Seq<ProcessorPos> used, Seq<SourcePos> sources, DisplayArea[] displayAreas, int linkX, int linkY, int scoreX, int scoreY, int procSize, int maxRange, boolean requireSource) {
        DisplayArea fake = new DisplayArea(linkX, linkY, linkX, linkY);
        return Exporter.takeNearestPlacementAround(positions, used, sources, displayAreas, fake, scoreX, scoreY, procSize, maxRange, requireSource);
    }

    /**
     * Place the next processor in a ring around the display:
     * <ul>
     *   <li>range is checked to the <b>nearest tile</b> of {@code linkArea} (not a fixed bottom-left corner);</li>
     *   <li>prefer slots closer to the display, then closer to layout center — avoids bottom-left clumping.</li>
     * </ul>
     */
    static Placement takeNearestPlacementAround(Seq<ProcessorPos> positions, Seq<ProcessorPos> used, Seq<SourcePos> sources, DisplayArea[] displayAreas, DisplayArea linkArea, int scoreX, int scoreY, int procSize, int maxRange, boolean requireSource) {
        ProcessorPos best = null;
        SourcePos bestSource = null;
        int bestLinkX = 0;
        int bestLinkY = 0;
        long bestScore = Long.MAX_VALUE;
        int maxDistance = maxRange * maxRange;
        int[] linkTile = new int[2];
        for (ProcessorPos pos : positions) {
            boolean blocked = false;
            for (ProcessorPos placed : used) {
                if (!Exporter.overlaps(pos.minX, pos.minY, procSize, placed.minX, placed.maxX, placed.minY, placed.maxY)) continue;
                blocked = true;
                break;
            }
            if (blocked) continue;
            for (SourcePos source : sources) {
                if (source.x < pos.minX || source.x > pos.maxX || source.y < pos.minY || source.y > pos.maxY) continue;
                blocked = true;
                break;
            }
            if (blocked) continue;
            SourcePos source = null;
            if (requireSource) {
                source = Exporter.preferredSourcePosition(pos, used, sources, displayAreas);
                if (source == null && pos.preferredSource == null) {
                    source = Exporter.adjacentSourcePosition(pos, used, sources, displayAreas);
                }
                if (source == null) continue;
            }
            Exporter.nearestDisplayTile(pos.x, pos.y, linkArea, linkTile);
            int linkDx = pos.x - linkTile[0];
            int linkDy = pos.y - linkTile[1];
            int linkDist2 = linkDx * linkDx + linkDy * linkDy;
            if (linkDist2 > maxDistance) continue;
            int scoreDx = pos.x - scoreX;
            int scoreDy = pos.y - scoreY;
            int centerDist2 = scoreDx * scoreDx + scoreDy * scoreDy;
            // Tight ring first, then prefer center of the art — never favor bottom-left via minY/minX.
            long score = (long)linkDist2 * 1_000_000L + centerDist2;
            if (score >= bestScore) continue;
            bestScore = score;
            best = pos;
            bestSource = source;
            bestLinkX = linkTile[0];
            bestLinkY = linkTile[1];
        }
        if (best != null) {
            used.add(best);
            if (bestSource != null) {
                sources.add(bestSource);
            }
            return new Placement(best, bestSource, bestLinkX, bestLinkY);
        }
        return null;
    }

    static Placement takeNearestPlacementInternal(Seq<ProcessorPos> positions, Seq<ProcessorPos> used, Seq<SourcePos> sources, DisplayArea[] displayAreas, int targetX, int targetY, int procSize, int maxRange, boolean requireSource, boolean inRangeOnly) {
        ProcessorPos best = null;
        SourcePos bestSource = null;
        int bestDistance = Integer.MAX_VALUE;
        int maxDistance = maxRange * maxRange;
        for (ProcessorPos pos : positions) {
            boolean blocked = false;
            for (ProcessorPos placed : used) {
                if (!Exporter.overlaps(pos.minX, pos.minY, procSize, placed.minX, placed.maxX, placed.minY, placed.maxY)) continue;
                blocked = true;
                break;
            }
            if (blocked) continue;
            for (SourcePos source : sources) {
                if (source.x < pos.minX || source.x > pos.maxX || source.y < pos.minY || source.y > pos.maxY) continue;
                blocked = true;
                break;
            }
            if (blocked) continue;
            SourcePos source = null;
            if (requireSource) {
                source = Exporter.preferredSourcePosition(pos, used, sources, displayAreas);
                if (source == null && pos.preferredSource == null) {
                    source = Exporter.adjacentSourcePosition(pos, used, sources, displayAreas);
                }
                if (source == null) continue;
            }
            int dx = pos.x - targetX;
            int dy = pos.y - targetY;
            int distance = dx * dx + dy * dy;
            if (inRangeOnly && distance > maxDistance || distance >= bestDistance) continue;
            bestDistance = distance;
            best = pos;
            bestSource = source;
        }
        return best == null ? null : new Placement(best, bestSource);
    }

    public static Pixmap scaleLinear(Pixmap src, int newW, int newH) {
        Pixmap dst = new Pixmap(newW, newH);
        float xRatio = (float)src.width / (float)newW;
        float yRatio = (float)src.height / (float)newH;
        for (int dy = 0; dy < newH; ++dy) {
            for (int dx = 0; dx < newW; ++dx) {
                float sx = (float)dx * xRatio;
                float sy = (float)dy * yRatio;
                int x0 = Math.max(0, Math.min((int)Math.floor(sx), src.width - 2));
                int x1 = Math.max(0, Math.min(x0 + 1, src.width - 1));
                int y0 = Math.max(0, Math.min((int)Math.floor(sy), src.height - 2));
                int y1 = Math.max(0, Math.min(y0 + 1, src.height - 1));
                float tx = sx - (float)x0;
                float ty = sy - (float)y0;
                Color c00 = new Color(src.get(x0, y0));
                Color c01 = new Color(src.get(x0, y1));
                Color c10 = new Color(src.get(x1, y0));
                Color c11 = new Color(src.get(x1, y1));
                Color result = new Color();
                result.r = c00.r * (1.0f - tx) * (1.0f - ty) + c10.r * tx * (1.0f - ty) + c01.r * (1.0f - tx) * ty + c11.r * tx * ty;
                result.g = c00.g * (1.0f - tx) * (1.0f - ty) + c10.g * tx * (1.0f - ty) + c01.g * (1.0f - tx) * ty + c11.g * tx * ty;
                result.b = c00.b * (1.0f - tx) * (1.0f - ty) + c10.b * tx * (1.0f - ty) + c01.b * (1.0f - tx) * ty + c11.b * tx * ty;
                result.a = 1.0f;
                dst.set(dx, dy, result.rgba8888());
            }
        }
        return dst;
    }

    static class CodeWriter {
        final int display;
        final int maxLines;
        final int flushInterval;
        final Seq<CodeBlock> blocks = new Seq();
        Seq<String> current = new Seq();
        int blockLines = 0;
        int drawCalls = 0;
        int blockPriority = Integer.MAX_VALUE;
        int blockOrder = 0;
        String lastColor = null;

        CodeWriter(int display, int maxLines, int flushInterval) {
            this.display = display;
            this.maxLines = maxLines;
            this.flushInterval = flushInterval;
        }

        void addRect(String colorCmd, int x, int y, int width, int height, int distanceFromCenter) {
            int extraLines;
            int n = extraLines = this.lastColor == null || !this.lastColor.equals(colorCmd) ? 2 : 1;
            if (this.drawCalls == 0) {
                ++extraLines;
            }
            if (this.drawCalls + 1 >= this.flushInterval) {
                ++extraLines;
            }
            this.ensureSpace(extraLines);
            this.blockPriority = Math.min(this.blockPriority, distanceFromCenter);
            if (this.lastColor == null || !this.lastColor.equals(colorCmd)) {
                this.current.add(colorCmd);
                ++this.blockLines;
                this.lastColor = colorCmd;
            }
            this.current.add(("draw rect " + x + " " + y + " " + width + " " + height));
            ++this.blockLines;
            ++this.drawCalls;
            if (this.drawCalls >= this.flushInterval) {
                this.flushPending();
            }
        }

        Seq<CodeBlock> finish() {
            this.flushPending();
            this.pushBlock();
            return this.blocks;
        }

        void ensureSpace(int extraLines) {
            int flushLines;
            int n = flushLines = this.drawCalls == 0 ? 0 : 1;
            if (this.blockLines + flushLines + extraLines <= this.maxLines) {
                return;
            }
            this.flushPending();
            this.pushBlock();
        }

        void flushPending() {
            if (this.drawCalls == 0) {
                return;
            }
            this.current.add("drawflush display1");
            ++this.blockLines;
            this.drawCalls = 0;
            this.lastColor = null;
        }

        void pushBlock() {
            if (this.current.size == 0) {
                return;
            }
            this.blocks.add(new CodeBlock(this.current.toString("\n"), this.display, this.blockPriority, this.blockOrder++));
            this.current = new Seq();
            this.blockLines = 0;
            this.blockPriority = Integer.MAX_VALUE;
            this.lastColor = null;
        }
    }

    static class DisplayArea {
        int minX;
        int minY;
        int maxX;
        int maxY;

        DisplayArea(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    static class CodeBlock {
        String code;
        int display;
        int priority;
        int order;

        CodeBlock(String code, int display, int priority, int order) {
            this.code = code;
            this.display = display;
            this.priority = priority;
            this.order = order;
        }
    }

    static class Placement {
        ProcessorPos processor;
        SourcePos source;
        int linkX;
        int linkY;

        Placement(ProcessorPos processor, SourcePos source) {
            this(processor, source, processor == null ? 0 : processor.x, processor == null ? 0 : processor.y);
        }

        Placement(ProcessorPos processor, SourcePos source, int linkX, int linkY) {
            this.processor = processor;
            this.source = source;
            this.linkX = linkX;
            this.linkY = linkY;
        }
    }

    static class ProcessorPos {
        int x;
        int y;
        int minX;
        int minY;
        int maxX;
        int maxY;
        int distance;
        SourcePos preferredSource;

        ProcessorPos(int minX, int minY, int size, int distance) {
            this(minX, minY, size, distance, null);
        }

        ProcessorPos(int minX, int minY, int size, int distance, SourcePos preferredSource) {
            this.x = Exporter.tileAnchor(minX, size);
            this.y = Exporter.tileAnchor(minY, size);
            this.minX = minX;
            this.minY = minY;
            this.maxX = minX + size - 1;
            this.maxY = minY + size - 1;
            this.distance = distance;
            this.preferredSource = preferredSource;
        }
    }

    static class SourcePos {
        int x;
        int y;

        SourcePos(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}

