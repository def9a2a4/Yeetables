package anon.def9a2a4.yeetables;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Snowball;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface for projectile visual rendering.
 */
interface ProjectileRenderer {
    void spawn();
    void remove();
    int getId();
}

/**
 * No-op renderer for simple item-based projectiles.
 */
class SimpleItemRenderer implements ProjectileRenderer {
    public static final SimpleItemRenderer INSTANCE = new SimpleItemRenderer();

    private SimpleItemRenderer() {}

    @Override public void spawn() {}
    @Override public void remove() {}
    @Override public int getId() { return -1; }
}

/**
 * Registry for tracking active renderers.
 */
class RendererRegistry {
    private static final Map<Integer, ProjectileRenderer> ACTIVE = new ConcurrentHashMap<>();

    public static void register(ProjectileRenderer renderer) {
        ACTIVE.put(renderer.getId(), renderer);
    }

    public static void remove(int id) {
        ProjectileRenderer r = ACTIVE.remove(id);
        if (r != null) r.remove();
    }

    public static ProjectileRenderer get(int id) {
        return ACTIVE.get(id);
    }
}

/**
 * Renders a projectile using BlockDisplay entities.
 * Spawns display entities that follow and rotate with the snowball carrier.
 */
public class BlockDisplayRenderer implements ProjectileRenderer {
    private static int NEXT_ID = 1;

    private static class PartInstance {
        final BlockDisplay entity;
        final Matrix4f base;

        PartInstance(BlockDisplay entity, Matrix4f base) {
            this.entity = entity;
            this.base = base;
        }
    }

    private final JavaPlugin plugin;
    private final Snowball carrier;
    private final int id;
    private final double gravityMultiplier;
    private final BlockDisplayRender config;
    private final List<PartInstance> parts = new ArrayList<>();
    private Vector lastDir = new Vector(0, 0, 1);

    private BlockDisplay parent;
    private BukkitRunnable task;
    private int life = 20 * 10; // 10 seconds max safety

    public BlockDisplayRenderer(JavaPlugin plugin, Snowball carrier, double gravityMultiplier, BlockDisplayRender config) {
        this.plugin = plugin;
        this.carrier = carrier;
        this.id = NEXT_ID++;
        this.gravityMultiplier = gravityMultiplier;
        this.config = config;
    }

    @Override
    public int getId() {
        return id;
    }

    private static Matrix4f matrixFromRowMajor(final float[] a) {
        // NBT/config stores row-major: [r00 r01 r02 r03, r10 r11 r12 r13, ...]
        // JOML constructor takes column-major, so we transpose
        return new Matrix4f(
            a[0],  a[4],  a[8],  a[12],
            a[1],  a[5],  a[9],  a[13],
            a[2],  a[6],  a[10], a[14],
            a[3],  a[7],  a[11], a[15]
        );
    }

    private void applyRotation(Vector dir) {
        Vector v = (dir == null || dir.lengthSquared() < 1e-10) ? lastDir : dir.clone();
        if (v.lengthSquared() < 1e-10) v = new Vector(0, 0, 1);
        Vector n = v.normalize();
        lastDir = n;

        double nx = n.getX(), ny = n.getY(), nz = n.getZ();

        // Calculate yaw and pitch from direction vector
        float yawDeg = (float) Math.toDegrees(Math.atan2(-nx, nz));
        float pitchDeg = (float) Math.toDegrees(-Math.atan2(ny, Math.sqrt(nx * nx + nz * nz)));

        // Apply multipliers and offsets
        float yawRad = (float) Math.toRadians(config.yawMultiplier() * yawDeg + config.yawOffset());
        float pitchRad = (float) Math.toRadians(config.pitchMultiplier() * pitchDeg + config.pitchOffset());

        // Build rotation matrix: yaw around Y, then pitch around X
        Matrix4f R = new Matrix4f()
            .rotateY(yawRad)
            .rotateX(pitchRad);

        // Apply rotation to each part
        for (PartInstance pi : parts) {
            Matrix4f world = new Matrix4f(R).mul(pi.base);
            pi.entity.setTransformationMatrix(world);
        }
    }

    @Override
    public void spawn() {
        RendererRegistry.register(this);
        World w = carrier.getWorld();
        Location base = carrier.getLocation();

        // Spawn invisible parent display for position/rotation control
        parent = w.spawn(base, BlockDisplay.class, d -> {
            d.setBlock(Bukkit.createBlockData(Material.AIR));
            d.setInterpolationDuration(1);
            d.setTeleportDuration(1);
            d.setViewRange(64f);
            d.setPersistent(false);
            d.setGravity(false);
        });

        // Spawn each model part as a child
        for (ModelPart part : config.parts()) {
            BlockData blockData = Bukkit.createBlockData(part.material());
            Matrix4f baseM = matrixFromRowMajor(part.transformation());

            BlockDisplay child = w.spawn(base, BlockDisplay.class, c -> {
                c.setBlock(blockData);
                c.setInterpolationDuration(1);
                c.setTeleportDuration(1);
                c.setViewRange(64f);
                c.setPersistent(false);
                c.setGravity(false);
                c.setTransformationMatrix(baseM);
            });

            parts.add(new PartInstance(child, new Matrix4f(baseM)));
        }

        // Wait 1 tick for entities to spawn, then assemble and start ticking
        new BukkitRunnable() {
            @Override
            public void run() {
                // Mount children to parent
                for (PartInstance pi : parts) {
                    parent.addPassenger(pi.entity);
                }
                // Mount parent to snowball
                carrier.addPassenger(parent);

                // Set initial facing
                Vector initDir = carrier.getVelocity();
                if (initDir == null || initDir.lengthSquared() < 1e-10) {
                    initDir = carrier.getLocation().getDirection();
                }
                applyRotation(initDir);

                // Per-tick update
                task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (life-- <= 0 || carrier.isDead() || !carrier.isValid()) {
                            remove();
                            cancel();
                            return;
                        }

                        Vector vel = carrier.getVelocity();

                        // Adjust gravity
                        double normalGravity = -0.04;
                        double adjustment = normalGravity * (gravityMultiplier - 1.0);
                        vel.setY(vel.getY() + adjustment);
                        carrier.setVelocity(vel);

                        // Update rotation
                        applyRotation(vel);
                    }
                };
                task.runTaskTimer(plugin, 0L, 1L);
            }
        }.runTaskLater(plugin, 1L);
    }

    @Override
    public void remove() {
        RendererRegistry.remove(id);
        removeNow();
    }

    private void removeNow() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (parent != null) {
            org.bukkit.entity.Entity vehicle = parent.getVehicle();
            if (vehicle != null) {
                vehicle.removePassenger(parent);
            }
            for (org.bukkit.entity.Entity passenger : parent.getPassengers()) {
                passenger.remove();
            }
            parent.remove();
            parent = null;
        }
    }
}
