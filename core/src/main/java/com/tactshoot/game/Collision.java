package com.tactshoot.game;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Raycasting and capsule-vs-world physics against axis-aligned boxes.
 *
 * <p>The old solver pushed the player out of the <em>last</em> overlapping box
 * and re-used the whole-body overlap test for every axis, so tall stacks
 * teleported the player downward and walking along a wall could jitter. This
 * solver sweeps horizontally per axis, supports real stair stepping, picks the
 * highest support when landing and handles ceilings.</p>
 */
public final class Collision {

    public static final float STEP_UP = 0.42f;
    private static final float EPS = 0.0015f;

    public static class Hit {
        public boolean hit;
        public float dist;
        public final Vector3 point = new Vector3();
        public final Vector3 normal = new Vector3();

        public void set(boolean hit, float dist, Vector3 point, Vector3 normal) {
            this.hit = hit;
            this.dist = dist;
            this.point.set(point);
            this.normal.set(normal);
        }

        public void clear() {
            hit = false;
            dist = Float.MAX_VALUE;
        }
    }

    private static final Hit sharedHit = new Hit();
    private static final Vector3 tmpP = new Vector3();
    private static final Vector3 tmpN = new Vector3();

    private Collision() {
    }

    /** Ray vs AABB slab test. Returns entry distance or -1. */
    public static float rayVsBox(Vector3 o, Vector3 d, Box b, Vector3 normalOut) {
        float tmin = 0f, tmax = Float.MAX_VALUE;
        int axis = -1;
        int sign = 0;
        // X
        if (Math.abs(d.x) < 1e-8f) {
            if (o.x < b.minX || o.x > b.maxX) return -1f;
        } else {
            float t1 = (b.minX - o.x) / d.x;
            float t2 = (b.maxX - o.x) / d.x;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) { tmin = t1; axis = 0; sign = d.x < 0 ? 1 : -1; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1f;
        }
        // Y
        if (Math.abs(d.y) < 1e-8f) {
            if (o.y < b.minY || o.y > b.maxY) return -1f;
        } else {
            float t1 = (b.minY - o.y) / d.y;
            float t2 = (b.maxY - o.y) / d.y;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) { tmin = t1; axis = 1; sign = d.y < 0 ? 1 : -1; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1f;
        }
        // Z
        if (Math.abs(d.z) < 1e-8f) {
            if (o.z < b.minZ || o.z > b.maxZ) return -1f;
        } else {
            float t1 = (b.minZ - o.z) / d.z;
            float t2 = (b.maxZ - o.z) / d.z;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) { tmin = t1; axis = 2; sign = d.z < 0 ? 1 : -1; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1f;
        }
        if (normalOut != null) {
            normalOut.setZero();
            if (axis == 0) normalOut.x = sign;
            else if (axis == 1) normalOut.y = sign;
            else normalOut.z = sign;
        }
        return tmin;
    }

    /** Closest hit of a ray against all world boxes. */
    public static Hit rayWorld(Vector3 o, Vector3 d, float maxDist, Array<Box> boxes) {
        sharedHit.clear();
        for (int i = 0; i < boxes.size; i++) {
            Box b = boxes.get(i);
            float t = rayVsBox(o, d, b, tmpN);
            if (t >= 0f && t < sharedHit.dist) {
                tmpP.set(d).scl(t).add(o);
                sharedHit.set(true, t, tmpP, tmpN);
            }
        }
        if (sharedHit.hit && sharedHit.dist > maxDist) {
            sharedHit.clear();
        }
        return sharedHit;
    }

    public static boolean lineOfSight(Vector3 a, Vector3 b, Array<Box> boxes) {
        Vector3 dir = T.tmp1.set(b).sub(a);
        float dist = dir.len();
        if (dist < 1e-5f) return true;
        dir.scl(1f / dist);
        for (int i = 0; i < boxes.size; i++) {
            float t = rayVsBox(a, dir, boxes.get(i), null);
            if (t >= 0f && t < dist - 0.05f) return false;
        }
        return true;
    }

    /** Ray vs entity AABB (standing capsule approximation). Returns entry distance or -1. */
    public static float rayVsPlayer(Vector3 o, Vector3 d, PlayerEntity e) {
        Box b = entityBox(e, new Box());
        return rayVsBox(o, d, b, null);
    }

    public static Box entityBox(PlayerEntity e, Box out) {
        float r = NetConfig.PLAYER_RADIUS;
        float h = e.height();
        out.set(e.pos.x - r, e.pos.y, e.pos.z - r, e.pos.x + r, e.pos.y + h, e.pos.z + r, 0);
        return out;
    }

    public static boolean boxesOverlap(Box a, Box b) {
        return a.minX < b.maxX && a.maxX > b.minX
                && a.minY < b.maxY && a.maxY > b.minY
                && a.minZ < b.maxZ && a.maxZ > b.minZ;
    }

    public static boolean entityOverlaps(PlayerEntity e, Box b) {
        Box eb = entityBox(e, new Box());
        return boxesOverlap(eb, b);
    }

    private static boolean overlapsAt(float x, float y, float z, float radius, float height,
                                      Array<Box> boxes) {
        float minX = x - radius, maxX = x + radius;
        float minY = y, maxY = y + height;
        float minZ = z - radius, maxZ = z + radius;
        for (int i = 0; i < boxes.size; i++) {
            Box b = boxes.get(i);
            if (minX < b.maxX && maxX > b.minX
                    && minY < b.maxY && maxY > b.minY
                    && minZ < b.maxZ && maxZ > b.minZ) {
                return true;
            }
        }
        return false;
    }

    /** True when the entity's feet are horizontally over the box. */
    private static boolean overBoxXZ(PlayerEntity e, Box b) {
        float r = NetConfig.PLAYER_RADIUS;
        return e.pos.x + r > b.minX && e.pos.x - r < b.maxX
                && e.pos.z + r > b.minZ && e.pos.z - r < b.maxZ;
    }

    /**
     * Integrates one frame of movement with sub-stepping so fast movement
     * cannot tunnel through thin cover.
     */
    public static void moveEntity(PlayerEntity e, float dt, Array<Box> boxes) {
        float speed = (float) Math.sqrt(e.vel.x * e.vel.x + e.vel.z * e.vel.z)
                + Math.abs(e.vel.y) * 0.5f;
        int substeps = T.clamp((int) Math.ceil(speed * dt / 0.22f), 1, 4);
        float sdt = dt / substeps;
        for (int i = 0; i < substeps; i++) {
            if (e.vel.x != 0f) moveAxisX(e, e.vel.x * sdt, boxes);
            if (e.vel.z != 0f) moveAxisZ(e, e.vel.z * sdt, boxes);
            moveAxisY(e, e.vel.y * sdt, boxes);
        }
    }

    private static void moveAxisX(PlayerEntity e, float dx, Array<Box> boxes) {
        float r = NetConfig.PLAYER_RADIUS;
        float nx = e.pos.x + dx;
        if (!overlapsAt(nx, e.pos.y, e.pos.z, r, e.height(), boxes)) {
            e.pos.x = nx;
            return;
        }
        if (e.onGround && tryStep(e, nx, e.pos.z, boxes)) {
            e.pos.x = nx;
            return;
        }
        float clamped = nx;
        for (int i = 0; i < boxes.size; i++) {
            Box b = boxes.get(i);
            // Only boxes the swept capsule actually overlaps resolve this axis.
            if (nx + r <= b.minX || nx - r >= b.maxX) continue;
            if (e.pos.z + r <= b.minZ || e.pos.z - r >= b.maxZ) continue;
            if (e.pos.y + e.height() <= b.minY || e.pos.y >= b.maxY) continue;
            if (dx > 0f) clamped = Math.min(clamped, b.minX - r - EPS);
            else clamped = Math.max(clamped, b.maxX + r + EPS);
        }
        e.pos.x = clamped;
        e.vel.x = 0f;
    }

    private static void moveAxisZ(PlayerEntity e, float dz, Array<Box> boxes) {
        float r = NetConfig.PLAYER_RADIUS;
        float nz = e.pos.z + dz;
        if (!overlapsAt(e.pos.x, e.pos.y, nz, r, e.height(), boxes)) {
            e.pos.z = nz;
            return;
        }
        if (e.onGround && tryStep(e, e.pos.x, nz, boxes)) {
            e.pos.z = nz;
            return;
        }
        float clamped = nz;
        for (int i = 0; i < boxes.size; i++) {
            Box b = boxes.get(i);
            if (e.pos.x + r <= b.minX || e.pos.x - r >= b.maxX) continue;
            if (nz + r <= b.minZ || nz - r >= b.maxZ) continue;
            if (e.pos.y + e.height() <= b.minY || e.pos.y >= b.maxY) continue;
            if (dz > 0f) clamped = Math.min(clamped, b.minZ - r - EPS);
            else clamped = Math.max(clamped, b.maxZ + r + EPS);
        }
        e.pos.z = clamped;
        e.vel.z = 0f;
    }

    /**
     * Attempts to climb a step no taller than {@link #STEP_UP}. The entity is
     * raised onto the highest blocking top found between the current and target
     * position, then checked for headroom.
     */
    private static boolean tryStep(PlayerEntity e, float targetX, float targetZ, Array<Box> boxes) {
        float r = NetConfig.PLAYER_RADIUS;
        float stepTop = -Float.MAX_VALUE;
        for (int i = 0; i < boxes.size; i++) {
            Box b = boxes.get(i);
            boolean overTarget = targetX + r > b.minX && targetX - r < b.maxX
                    && targetZ + r > b.minZ && targetZ - r < b.maxZ;
            if (!overTarget) continue;
            if (b.maxY <= e.pos.y + EPS || b.maxY > e.pos.y + STEP_UP + 0.05f) continue;
            stepTop = Math.max(stepTop, b.maxY);
        }
        if (stepTop == -Float.MAX_VALUE) return false;
        float targetY = stepTop + EPS;
        if (overlapsAt(targetX, targetY, targetZ, r, e.height(), boxes)) return false;
        e.pos.y = targetY;
        return true;
    }

    private static void moveAxisY(PlayerEntity e, float dy, Array<Box> boxes) {
        float oldY = e.pos.y;
        float ny = oldY + dy;
        float r = NetConfig.PLAYER_RADIUS;
        float h = e.height();

        if (!overlapsAt(e.pos.x, ny, e.pos.z, r, h, boxes)) {
            e.pos.y = ny;
            e.onGround = false;
            // Safety floor so a missing ground mesh can never drop the player forever.
            if (e.pos.y <= 0f) {
                e.pos.y = 0f;
                e.vel.y = 0f;
                e.onGround = true;
            }
            return;
        }

        if (dy <= 0f) {
            // Land on the highest support we could have crossed from above.
            float bestTop = 0f;
            for (int i = 0; i < boxes.size; i++) {
                Box b = boxes.get(i);
                if (!overBoxXZ(e, b)) continue;
                if (b.maxY <= oldY + 0.02f && b.maxY > bestTop) bestTop = b.maxY;
            }
            e.pos.y = bestTop;
            e.vel.y = 0f;
            e.onGround = true;
        } else {
            // Head bump: stop under the lowest ceiling. Only boxes that lie
            // above the head count -- otherwise the ground box (minY < 0)
            // would be chosen and teleport the player downward.
            float lowest = Float.MAX_VALUE;
            for (int i = 0; i < boxes.size; i++) {
                Box b = boxes.get(i);
                if (!overBoxXZ(e, b)) continue;
                if (b.minY < oldY + h - 0.20f) continue;
                if (b.minY < lowest) lowest = b.minY;
            }
            if (lowest != Float.MAX_VALUE) {
                e.pos.y = lowest - h - EPS;
                e.vel.y = 0f;
            } else {
                e.pos.y = ny;
            }
            e.onGround = false;
        }
    }

    /** True if the entity can stand at its current position with the given height. */
    public static boolean canStand(PlayerEntity e, float height, Array<Box> boxes) {
        float r = NetConfig.PLAYER_RADIUS;
        Box eb = new Box();
        eb.set(e.pos.x - r, e.pos.y, e.pos.z - r, e.pos.x + r, e.pos.y + height, e.pos.z + r, 0);
        for (int i = 0; i < boxes.size; i++) {
            if (boxesOverlap(eb, boxes.get(i))) return false;
        }
        return true;
    }

    /**
     * Gently pushes overlapping combatants apart on the horizontal plane.
     * Bodies are treated as circles so players cannot occupy the same cell.
     */
    public static void separatePlayers(Array<PlayerEntity> players) {
        float minDist = NetConfig.PLAYER_RADIUS * 2f;
        for (int i = 0; i < players.size; i++) {
            PlayerEntity a = players.get(i);
            if (!a.alive) continue;
            for (int j = i + 1; j < players.size; j++) {
                PlayerEntity b = players.get(j);
                if (!b.alive) continue;
                float dx = b.pos.x - a.pos.x;
                float dz = b.pos.z - a.pos.z;
                float d2 = dx * dx + dz * dz;
                if (d2 >= minDist * minDist || d2 < 1e-6f) continue;
                float d = (float) Math.sqrt(d2);
                float push = (minDist - d) * 0.5f;
                float ux = dx / d, uz = dz / d;
                if (a != null && !a.isRemote) {
                    a.pos.x -= ux * push;
                    a.pos.z -= uz * push;
                }
                if (b != null && !b.isRemote) {
                    b.pos.x += ux * push;
                    b.pos.z += uz * push;
                }
            }
        }
    }
}
