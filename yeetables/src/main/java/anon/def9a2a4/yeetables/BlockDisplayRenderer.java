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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rotation mode for display entity renderers.
 */
enum RotationMode {
    POINT_FORWARD,  // Face velocity direction (default)
    SPIN_RANDOM,    // Spin along a random axis
    NONE            // No rotation
}

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

    // For SPIN_RANDOM mode
    private Vector spinAxis;
    private float spinAngle = 0f;
    private static final float SPIN_SPEED = 0.3f; // radians per tick

    private BlockDisplay parent;
    private BukkitRunnable task;
    private int life = 20 * 10; // 10 seconds max safety

    public BlockDisplayRenderer(JavaPlugin plugin, Snowball carrier, double gravityMultiplier, BlockDisplayRender config) {
        this.plugin = plugin;
        this.carrier = carrier;
        this.id = NEXT_ID++;
        this.gravityMultiplier = gravityMultiplier;
        this.config = config;

        // Initialize random spin axis if needed
        if (config.rotationMode() == RotationMode.SPIN_RANDOM) {
            Random rand = new Random();
            spinAxis = new Vector(
                rand.nextDouble() - 0.5,
                rand.nextDouble() - 0.5,
                rand.nextDouble() - 0.5
            ).normalize();
        }
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
        Matrix4f R;

        switch (config.rotationMode()) {
            case SPIN_RANDOM -> {
                // Rotate around random axis
                spinAngle += SPIN_SPEED;
                R = new Matrix4f().rotate(spinAngle,
                    (float) spinAxis.getX(),
                    (float) spinAxis.getY(),
                    (float) spinAxis.getZ());
            }
            case NONE -> {
                // No rotation - identity matrix
                R = new Matrix4f();
            }
            default -> { // POINT_FORWARD
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
                R = new Matrix4f()
                    .rotateY(yawRad)
                    .rotateX(pitchRad);
            }
        }

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

/**
 * Renders a projectile using an ItemDisplay entity.
 * Supports custom items with full metadata (e.g., player heads with textures).
 */
class ItemDisplayRenderer implements ProjectileRenderer {
    private static int NEXT_ID = 1000; // Offset from BlockDisplayRenderer IDs

    private final JavaPlugin plugin;
    private final Snowball carrier;
    private final int id;
    private final double gravityMultiplier;
    private final ItemDisplayRender config;

    // For SPIN_RANDOM mode
    private Vector spinAxis;
    private float spinAngle = 0f;
    private static final float SPIN_SPEED = 0.3f;

    private org.bukkit.entity.ItemDisplay display;
    private BukkitRunnable task;
    private int life = 20 * 10;
    private Matrix4f baseTransform;
    private Vector lastDir = new Vector(0, 0, 1);

    public ItemDisplayRenderer(JavaPlugin plugin, Snowball carrier, double gravityMultiplier, ItemDisplayRender config) {
        this.plugin = plugin;
        this.carrier = carrier;
        this.id = NEXT_ID++;
        this.gravityMultiplier = gravityMultiplier;
        this.config = config;

        if (config.rotationMode() == RotationMode.SPIN_RANDOM) {
            Random rand = new Random();
            spinAxis = new Vector(
                rand.nextDouble() - 0.5,
                rand.nextDouble() - 0.5,
                rand.nextDouble() - 0.5
            ).normalize();
        }
    }

    @Override
    public int getId() {
        return id;
    }

    private static Matrix4f matrixFromRowMajor(final float[] a) {
        return new Matrix4f(
            a[0],  a[4],  a[8],  a[12],
            a[1],  a[5],  a[9],  a[13],
            a[2],  a[6],  a[10], a[14],
            a[3],  a[7],  a[11], a[15]
        );
    }

    private void applyRotation(Vector dir) {
        Matrix4f R;

        switch (config.rotationMode()) {
            case SPIN_RANDOM -> {
                spinAngle += SPIN_SPEED;
                R = new Matrix4f().rotate(spinAngle,
                    (float) spinAxis.getX(),
                    (float) spinAxis.getY(),
                    (float) spinAxis.getZ());
            }
            case NONE -> {
                R = new Matrix4f();
            }
            default -> { // POINT_FORWARD
                Vector v = (dir == null || dir.lengthSquared() < 1e-10) ? lastDir : dir.clone();
                if (v.lengthSquared() < 1e-10) v = new Vector(0, 0, 1);
                Vector n = v.normalize();
                lastDir = n;

                double nx = n.getX(), ny = n.getY(), nz = n.getZ();
                float yawDeg = (float) Math.toDegrees(Math.atan2(-nx, nz));
                float pitchDeg = (float) Math.toDegrees(-Math.atan2(ny, Math.sqrt(nx * nx + nz * nz)));

                R = new Matrix4f()
                    .rotateY((float) Math.toRadians(yawDeg))
                    .rotateX((float) Math.toRadians(pitchDeg));
            }
        }

        Matrix4f world = new Matrix4f(R).mul(baseTransform);
        display.setTransformationMatrix(world);
    }

    @Override
    public void spawn() {
        RendererRegistry.register(this);
        World w = carrier.getWorld();
        Location loc = carrier.getLocation();
        baseTransform = matrixFromRowMajor(config.transformation());

        display = w.spawn(loc, org.bukkit.entity.ItemDisplay.class, d -> {
            d.setItemStack(config.item());
            d.setInterpolationDuration(1);
            d.setTeleportDuration(1);
            d.setViewRange(64f);
            d.setPersistent(false);
            d.setGravity(false);
            d.setTransformationMatrix(baseTransform);
        });

        new BukkitRunnable() {
            @Override
            public void run() {
                carrier.addPassenger(display);

                Vector initDir = carrier.getVelocity();
                if (initDir == null || initDir.lengthSquared() < 1e-10) {
                    initDir = carrier.getLocation().getDirection();
                }
                applyRotation(initDir);

                task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (life-- <= 0 || carrier.isDead() || !carrier.isValid()) {
                            remove();
                            cancel();
                            return;
                        }

                        Vector vel = carrier.getVelocity();

                        double normalGravity = -0.04;
                        double adjustment = normalGravity * (gravityMultiplier - 1.0);
                        vel.setY(vel.getY() + adjustment);
                        carrier.setVelocity(vel);

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
        if (display != null) {
            org.bukkit.entity.Entity vehicle = display.getVehicle();
            if (vehicle != null) {
                vehicle.removePassenger(display);
            }
            display.remove();
            display = null;
        }
    }
}
