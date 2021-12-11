// Modified savage factions dynmap marker code

package net.starlegacy.feature.nations;

import net.starlegacy.database.schema.nations.Settlement;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerSet;
import org.dynmap.utils.TileFlags;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.starlegacy.util.CoordinatesKt.chunkKeyX;
import static net.starlegacy.util.CoordinatesKt.chunkKeyZ;

public class SettlementMapper {
    private final MarkerSet markerSet;
    private final Settlement settlement;

    public SettlementMapper(MarkerSet markerSet, Settlement settlement) {
        this.markerSet = markerSet;
        this.settlement = settlement;
    }


    enum direction {XPLUS, ZPLUS, XMINUS, ZMINUS}

    double blocksize = 16;

    /**
     * Find all contiguous blocks, set in target and clear in source
     */
    private int floodFillTarget(TileFlags src, TileFlags dest, int x, int y) {
        int cnt = 0;
        ArrayDeque<int[]> stack = new ArrayDeque<int[]>();
        stack.push(new int[]{x, y});

        while (!stack.isEmpty()) {
            int[] nxt = stack.pop();
            x = nxt[0];
            y = nxt[1];
            if (src.getFlag(x, y)) { /* Set in src */
                src.setFlag(x, y, false);   /* Clear source */
                dest.setFlag(x, y, true);   /* Set in destination */
                cnt++;
                if (src.getFlag(x + 1, y))
                    stack.push(new int[]{x + 1, y});
                if (src.getFlag(x - 1, y))
                    stack.push(new int[]{x - 1, y});
                if (src.getFlag(x, y + 1))
                    stack.push(new int[]{x, y + 1});
                if (src.getFlag(x, y - 1))
                    stack.push(new int[]{x, y - 1});
            }
        }
        return cnt;
    }

    private static class FactionBlock {
        String world;
        int x, z;
    }

    private static class FactionBlocks {
        Map<String, LinkedList<FactionBlock>> blocks = new HashMap<String, LinkedList<FactionBlock>>();
    }

    /* Handle specific faction on specific world */
    public void run(Consumer<AreaMarker> formatMarker) {
        String world = settlement.getWorldName();

        List<FactionBlock> chunks = settlement.getChunks().stream()
                .map(key -> {
                    FactionBlock factionBlock = new FactionBlock();
                    factionBlock.world = settlement.getWorldName();
                    factionBlock.x = chunkKeyX(key);
                    factionBlock.z = chunkKeyZ(key);
                    return factionBlock;
                })
                .collect(Collectors.toList());

        if (chunks.isEmpty()) {
            return;
        }

        // Index of polygon for given faction
        int markerIndex = 0;

        // Loop through chunks: set flags on chunk map
        TileFlags allChunkFlags = new TileFlags();
        LinkedList<FactionBlock> allChunks = new LinkedList<>();
        for (FactionBlock chunk : chunks) {
            allChunkFlags.setFlag((int) chunk.x, (int) chunk.z, true); // Set flag for chunk
            allChunks.addLast(chunk);
        }

        // Loop through until we don't find more areas
        while (allChunks != null) {
            TileFlags ourChunkFlags = null;
            LinkedList<FactionBlock> ourChunks = null;
            LinkedList<FactionBlock> newChunks = null;

            int minimumX = Integer.MAX_VALUE;
            int minimumZ = Integer.MAX_VALUE;
            for (FactionBlock chunk : allChunks) {
                int chunkX = chunk.x;
                int chunkZ = chunk.z;

                // If we need to start shape, and this block is not part of one yet
                if (ourChunkFlags == null && allChunkFlags.getFlag(chunkX, chunkZ)) {
                    ourChunkFlags = new TileFlags(); // Create map for shape
                    ourChunks = new LinkedList<>();
                    floodFillTarget(allChunkFlags, ourChunkFlags, chunkX, chunkZ); // Copy shape
                    ourChunks.add(chunk); // Add it to our chunk list
                    minimumX = chunkX;
                    minimumZ = chunkZ;
                }
                // If shape found, and we're in it, add to our node list
                else if (ourChunkFlags != null && ourChunkFlags.getFlag(chunkX, chunkZ)) {
                    ourChunks.add(chunk);
                    if (chunkX < minimumX) {
                        minimumX = chunkX;
                        minimumZ = chunkZ;
                    } else if (chunkX == minimumX && chunkZ < minimumZ) {
                        minimumZ = chunkZ;
                    }
                }
                // Else, keep it in the list for the next polygon
                else {
                    if (newChunks == null) {
                        newChunks = new LinkedList<>();
                    }
                    newChunks.add(chunk);
                }
            }

            // Replace list (null if no more to process)
            allChunks = newChunks;

            if (ourChunkFlags == null) {
                continue;
            }

            // Trace outline of blocks - start from minx, minz going to x+
            int initialX = minimumX;
            int initialZ = minimumZ;
            int currentX = minimumX;
            int currentZ = minimumZ;
            direction dir = direction.XPLUS;
            ArrayList<int[]> linelist = new ArrayList<>();
            linelist.add(new int[]{initialX, initialZ}); // Add start point
            while ((currentX != initialX) || (currentZ != initialZ) || (dir != direction.ZMINUS)) {
                switch (dir) {
                    case XPLUS: // Segment in X+ direction
                        if (!ourChunkFlags.getFlag(currentX + 1, currentZ)) { // Right turn?
                            linelist.add(new int[]{currentX + 1, currentZ}); // Finish line
                            dir = direction.ZPLUS; // Change direction
                        } else if (!ourChunkFlags.getFlag(currentX + 1, currentZ - 1)) { // Straight?
                            currentX++;
                        } else { // Left turn
                            linelist.add(new int[]{currentX + 1, currentZ}); // Finish line
                            dir = direction.ZMINUS;
                            currentX++;
                            currentZ--;
                        }
                        break;
                    case ZPLUS: // Segment in Z+ direction
                        if (!ourChunkFlags.getFlag(currentX, currentZ + 1)) { // Right turn?
                            linelist.add(new int[]{currentX + 1, currentZ + 1}); // Finish line
                            dir = direction.XMINUS; // Change direction
                        } else if (!ourChunkFlags.getFlag(currentX + 1, currentZ + 1)) { // Straight?
                            currentZ++;
                        } else { // Left turn
                            linelist.add(new int[]{currentX + 1, currentZ + 1}); // Finish line
                            dir = direction.XPLUS;
                            currentX++;
                            currentZ++;
                        }
                        break;
                    case XMINUS: // Segment in X- direction
                        if (!ourChunkFlags.getFlag(currentX - 1, currentZ)) { // Right turn?
                            linelist.add(new int[]{currentX, currentZ + 1}); // Finish line
                            dir = direction.ZMINUS; // Change direction
                        } else if (!ourChunkFlags.getFlag(currentX - 1, currentZ + 1)) { // Straight?
                            currentX--;
                        } else { // Left turn
                            linelist.add(new int[]{currentX, currentZ + 1}); // Finish line
                            dir = direction.ZPLUS;
                            currentX--;
                            currentZ++;
                        }
                        break;
                    case ZMINUS: // Segment in Z- direction
                        if (!ourChunkFlags.getFlag(currentX, currentZ - 1)) { // Right turn?
                            linelist.add(new int[]{currentX, currentZ}); // Finish line
                            dir = direction.XPLUS; // Change direction
                        } else if (!ourChunkFlags.getFlag(currentX - 1, currentZ - 1)) { // Straight?
                            currentZ--;
                        } else { // Left turn
                            linelist.add(new int[]{currentX, currentZ}); // Finish line
                            dir = direction.XMINUS;
                            currentX--;
                            currentZ--;
                        }
                        break;
                }
            }

            int sz = linelist.size();
            double[] x = new double[sz];
            double[] z = new double[sz];
            for (int i = 0; i < sz; i++) {
                int[] line = linelist.get(i);
                x[i] = (double) line[0] * blocksize;
                z[i] = (double) line[1] * blocksize;
            }

            // Build information for specific area
            String markerId = settlement.get_id().toString() + "." + markerIndex;

            AreaMarker m = markerSet.findAreaMarker(markerId);

            if (m == null) {
                m = markerSet.createAreaMarker(markerId, settlement.getName(), false, world, x, z, false);

                if (m == null) {
                    throw new RuntimeException("error adding area marker " + markerId);
                }
            } else {
                m.setCornerLocations(x, z); /* Replace corner locations */
            }


            formatMarker.accept(m);

            markerIndex++;
        }
    }
}
