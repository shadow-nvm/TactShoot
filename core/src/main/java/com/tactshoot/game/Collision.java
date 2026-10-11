package com.tactshoot.game;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/** Raycasting and capsule-vs-world physics against axis-aligned boxes. */
public final class Collision {

    public static final float STEP_UP = 0.45f;

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

    /**
     * Integrate entity movement against the world with axis-separated resolution
     * and automatic step-up for stairs/low obstacles.
     */
    public static void moveEntity(PlayerEntity e, float dt, Array<Box> boxes) {
        float r = NetConfig.PLAYER_RADIUS;

        // ---- horizontal ----
        float dx = e.vel.x * dt;
        float dz = e.vel.z * dt;
        if (dx != 0f || dz != 0f) {
            // X axis
            e.pos.x += dx;
            boolean hitX = false;
            for (int i = 0; i < boxes.size; i++) {
                Box b = boxes.get(i);
                if (entityOverlaps(e, b)) {
                    hitX = true;
                    break;
                }
            }
            if (hitX) {
                e.pos.x -= dx; // back off
                if (e.onGround && tryStep(e, dx, 0f, boxes)) {
                    // stepped up
                } else {
                    for (int i = 0; i < boxes.size; i++) {
                        Box b = boxes.get(i);
                        if (entityOverlaps(e, b)) {
                            if (dx > 0) e.pos.x = b.minX - r - 0.001f;
                            else e.pos.x = b.maxX + r + 0.001f;
                        }
                    }
                    e.vel.x = 0f;
                }
            }
            // Z axis
            e.pos.z += dz;
            boolean hitZ = false;
            for (int i = 0; i < boxes.size; i++) {
                Box b = boxes.get(i);
                if (entityOverlaps(e, b)) {
                    hitZ = true;
                    break;
                }
            }
            if (hitZ) {
                e.pos.z -= dz;
                if (e.onGround && tryStep(e, 0f, dz, boxes)) {
                    // stepped up
                } else {
                    for (int i = 0; i < boxes.size; i++) {
                        Box b = boxes.get(i);
                        if (entityOverlaps(e, b)) {
                            if (dz > 0) e.pos.z = b.minZ - r - 0.001f;
                            else e.pos.z = b.maxZ + r + 0.001f;
                        }
                    }
                    e.vel.z = 0f;
                }
            }
        }

        // ---- vertical ----
        e.pos.y += e.vel.y * dt;
        boolean grounded = false;
        for (int i = 0; i < boxes.size; i++) {
            Box b = boxes.get(i);
            if (entityOverlaps(e, b)) {
                if (e.vel.y <= 0f) {
                    e.pos.y = b.maxY;
                    e.vel.y = 0f;
                    grounded = true;
                } else {
                    e.pos.y = b.minY - e.height();
                    e.vel.y = 0f;
                }
            }
        }
        if (e.pos.y < 0f) { // safety floor
            e.pos.y = 0f;
            e.vel.y = 0f;
            grounded = true;
        }
        e.onGround = grounded;
    }

    /** Attempt to lift the entity by STEP_UP to clear a low obstacle. */
    private static boolean tryStep(PlayerEntity e, float dx, float dz, Array<Box> boxes) {
        e.pos.y += STEP_UP;
        e.pos.x += dx;
        e.pos.z += dz;
        boolean ok = true;
        for (int i = 0; i < boxes.size; i++) {
            if (entityOverlaps(e, boxes.get(i))) {
                ok = false;
                break;
            }
        }
        if (!ok) {
            e.pos.x -= dx;
            e.pos.z -= dz;
            e.pos.y -= STEP_UP;
        }
        return ok;
    }

    /** True if the entity can stand at its current position with the given height. */
    public static boolean canStand(PlayerEntity e, float height, Array<Box> boxes) {
        float prev = e.height();
        // temporarily test with target height
        Box eb = new Box();
        float r = NetConfig.PLAYER_RADIUS;
        eb.set(e.pos.x - r, e.pos.y, e.pos.z - r, e.pos.x + r, e.pos.y + height, e.pos.z + r, 0);
        for (int i = 0; i < boxes.size; i++) {
            if (boxesOverlap(eb, boxes.get(i))) return false;
        }
        return true;
    }
}
