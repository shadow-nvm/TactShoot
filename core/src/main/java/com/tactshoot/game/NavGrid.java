package com.tactshoot.game;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Queue;

/** Grid-based BFS pathfinding over a map's walkable area. */
public class NavGrid {

    public static final float CELL = 1f;

    private final int w, h;
    private final float ox, oz;
    private final boolean[] blocked;
    private final int[] cameFrom;
    private final boolean[] visited;
    private final Queue<Integer> queue = new Queue<>();

    public NavGrid(MapDef map) {
        ox = map.minX;
        oz = map.minZ;
        w = (int) Math.ceil((map.maxX - map.minX) / CELL) + 1;
        h = (int) Math.ceil((map.maxZ - map.minZ) / CELL) + 1;
        blocked = new boolean[w * h];
        cameFrom = new int[w * h];
        visited = new boolean[w * h];
        build(map);
    }

    private void build(MapDef map) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float cx = ox + x * CELL + CELL / 2f;
                float cz = oz + y * CELL + CELL / 2f;
                boolean walk = true;
                for (int i = 0; i < map.solids.size; i++) {
                    Box b = map.solids.get(i);
                    if (cx > b.minX && cx < b.maxX && cz > b.minZ && cz < b.maxZ) {
                        // blocks feet (low cover), mid body, or headroom
                        if ((b.minY < 1.0f && b.maxY > 0.12f)      // can't step over
                                || (b.minY < 1.9f && b.maxY > 1.05f)) { // head/torso block
                            walk = false;
                            break;
                        }
                    }
                }
                blocked[y * w + x] = !walk;
            }
        }
    }

    public int cellX(float wx) {
        return T.clamp((int) ((wx - ox) / CELL), 0, w - 1);
    }

    public int cellZ(float wz) {
        return T.clamp((int) ((wz - oz) / CELL), 0, h - 1);
    }

    public float worldX(int cx) {
        return ox + cx * CELL + CELL / 2f;
    }

    public float worldZ(int cz) {
        return oz + cz * CELL + CELL / 2f;
    }

    public boolean isBlocked(int cx, int cz) {
        if (cx < 0 || cz < 0 || cx >= w || cz >= h) return true;
        return blocked[cz * w + cx];
    }

    public boolean blockedAt(float wx, float wz) {
        return isBlocked(cellX(wx), cellZ(wz));
    }

    /** BFS path from (sx,sz) to (tx,tz); returns waypoints in world space or null. */
    public Array<Vector3> findPath(float sx, float sz, float tx, float tz) {
        int sxC = cellX(sx), szC = cellZ(sz);
        int txC = cellX(tx), tzC = cellZ(tz);
        return findPathCells(sxC, szC, txC, tzC);
    }

    public Array<Vector3> findPathCells(int sxC, int szC, int txC, int tzC) {
        java.util.Arrays.fill(visited, false);
        queue.clear();
        int start = szC * w + sxC;
        int target = tzC * w + txC;
        if (start == target) {
            Array<Vector3> single = new Array<>();
            single.add(new Vector3(worldX(txC), 0, worldZ(tzC)));
            return single;
        }
        if (isBlocked(txC, tzC)) {
            // find nearest open neighbor of target
            int[] nb = nearestOpen(txC, tzC);
            if (nb == null) return null;
            txC = nb[0];
            tzC = nb[1];
            target = tzC * w + txC;
        }
        visited[start] = true;
        queue.addLast(start);
        boolean found = false;
        int[] dirs = {1, 0, -1, 0, 0, 1, 0, -1};
        while (queue.size > 0) {
            int cur = queue.removeFirst();
            if (cur == target) {
                found = true;
                break;
            }
            int cx = cur % w;
            int cz = cur / w;
            for (int d = 0; d < 4; d++) {
                int nx = cx + dirs[d * 2];
                int nz = cz + dirs[d * 2 + 1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                int ni = nz * w + nx;
                if (!visited[ni] && !blocked[ni]) {
                    visited[ni] = true;
                    cameFrom[ni] = cur;
                    queue.addLast(ni);
                }
            }
        }
        if (!found) return null;
        // backtrack
        Array<Vector3> raw = new Array<>();
        int cur = target;
        while (cur != start) {
            raw.add(new Vector3(worldX(cur % w), 0, worldZ(cur / w)));
            cur = cameFrom[cur];
        }
        raw.reverse();
        return raw;
    }

    private int[] nearestOpen(int cx, int cz) {
        for (int r = 1; r < 6; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = cx + dx, nz = cz + dy;
                    if (!isBlocked(nx, nz)) return new int[]{nx, nz};
                }
            }
        }
        return null;
    }

    /** A random open cell, preferring ones far from the given point. */
    public Vector3 randomOpen(float farFromX, float farFromZ) {
        float bestDist = -1f;
        Vector3 best = null;
        int attempts = 0;
        while (attempts++ < 40) {
            int cx = T.randInt(0, w - 1);
            int cz = T.randInt(0, h - 1);
            if (blocked[cz * w + cx]) continue;
            float x = worldX(cx), z = worldZ(cz);
            float d = (x - farFromX) * (x - farFromX) + (z - farFromZ) * (z - farFromZ);
            if (d > bestDist) {
                bestDist = d;
                best = new Vector3(x, 0, z);
            }
        }
        if (best == null) {
            for (int cz = 0; cz < h && best == null; cz++) {
                for (int cx = 0; cx < w; cx++) {
                    if (!blocked[cz * w + cx]) {
                        best = new Vector3(worldX(cx), 0, worldZ(cz));
                        break;
                    }
                }
            }
        }
        return best;
    }
}
