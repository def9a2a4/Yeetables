package anon.def9a2a4.yeetables;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Chicken;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AbilityRegistry {
    private static final Map<String, Ability> ABILITIES = new HashMap<>();

    static {
        ABILITIES.put("bounce", new BounceAbility());
        ABILITIES.put("explode", new ExplodeAbility());
        ABILITIES.put("fireball", new FireballAbility());
        ABILITIES.put("ignite", new IgniteAbility());
        ABILITIES.put("potion", new PotionAbility());
        ABILITIES.put("grapple", new GrappleAbility());
        ABILITIES.put("swap", new SwapAbility());
    }

    public static Ability get(String name) {
        return name == null ? null : ABILITIES.get(name.toLowerCase());
    }

    public static void register(String name, Ability ability) {
        ABILITIES.put(name.toLowerCase(), ability);
    }
}

/**
 * Interface for special projectile behaviors.
 */
interface Ability {
    /**
     * Called when the projectile hits something.
     *
     * @param event The hit event
     * @param snowball The projectile
     * @param definition The yeetable definition
     * @param abilityConfig The ability-specific config section (may be null)
     * @param manager The projectile manager (for spawning bounced projectiles)
     * @return true if the projectile should be destroyed, false to keep it alive (e.g., for bouncing)
     */
    boolean onHit(ProjectileHitEvent event, Snowball snowball, YeetableDefinition definition,
                  ConfigurationSection abilityConfig, ProjectileManager manager);
}

/**
 * Bounce ability - projectile bounces off blocks.
 */
class BounceAbility implements Ability {
    @Override
    public boolean onHit(ProjectileHitEvent event, Snowball snowball, YeetableDefinition definition,
                         ConfigurationSection abilityConfig, ProjectileManager manager) {

        // Entity hit - don't bounce, let normal hit handling occur
        if (event.getHitEntity() instanceof LivingEntity) {
            return true;
        }

        // Check remaining bounces
        int remaining = manager.getRemainingBounces(snowball);
        if (remaining <= 0) {
            return true; // No bounces left, destroy
        }

        BlockFace face = event.getHitBlockFace();
        if (face == null) {
            return true;
        }

        // Calculate bounce velocity
        Vector v = snowball.getVelocity().clone();
        if (v.lengthSquared() < 1e-6) {
            v = snowball.getLocation().getDirection();
        }

        double damp = 0.8; // Lose some speed each bounce
        switch (face) {
            case NORTH, SOUTH -> v.setZ(-v.getZ());
            case EAST, WEST -> v.setX(-v.getX());
            case UP, DOWN -> v.setY(-v.getY());
            default -> v.multiply(-1);
        }
        v.multiply(damp);

        // Spawn new projectile with reduced bounce count
        final Vector finalVelocity = v.clone();
        final int newBounces = remaining - 1;

        JavaPlugin plugin = manager.getPlugin();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!(snowball.getShooter() instanceof Player shooter)) return;

                Location spawnLoc = snowball.getLocation().add(finalVelocity.clone().normalize().multiply(0.1));
                manager.spawnBouncedProjectile(shooter, spawnLoc, finalVelocity, definition, newBounces);
            }
        }.runTask(plugin);

        return true; // Original projectile is destroyed, new one spawned
    }
}

/**
 * Explode ability - creates an explosion on impact.
 */
class ExplodeAbility implements Ability {
    @Override
    public boolean onHit(ProjectileHitEvent event, Snowball snowball, YeetableDefinition definition,
                         ConfigurationSection abilityConfig, ProjectileManager manager) {

        Location explosionLoc = snowball.getLocation();

        // Get explosion parameters from config
        float power = 2.0f;
        boolean setFire = false;
        boolean breakBlocks = false;

        if (abilityConfig != null) {
            power = (float) abilityConfig.getDouble("power", 2.0);
            setFire = abilityConfig.getBoolean("set-fire", false);
            breakBlocks = abilityConfig.getBoolean("break-blocks", false);
        }

        // Create explosion
        explosionLoc.getWorld().createExplosion(
            explosionLoc,
            power,
            setFire,
            breakBlocks,
            snowball.getShooter() instanceof Player p ? p : null
        );

        return true; // Projectile destroyed after explosion
    }
}

/**
 * Fireball ability - spawns a small fireball projectile on impact.
 */
class FireballAbility implements Ability {
    @Override
    public boolean onHit(ProjectileHitEvent event, Snowball snowball, YeetableDefinition definition,
                         ConfigurationSection abilityConfig, ProjectileManager manager) {

        Location loc = snowball.getLocation();
        Vector direction = snowball.getVelocity().normalize();

        // Spawn small fireball like a dispenser would
        SmallFireball fireball = loc.getWorld().spawn(loc, SmallFireball.class, fb -> {
            fb.setDirection(direction);
            fb.setIsIncendiary(true);
            if (snowball.getShooter() instanceof Player p) {
                fb.setShooter(p);
            }
        });

        return true;
    }
}

/**
 * Ignite ability - bounces off blocks and sets entities on fire.
 */
class IgniteAbility implements Ability {
    @Override
    public boolean onHit(ProjectileHitEvent event, Snowball snowball, YeetableDefinition definition,
                         ConfigurationSection abilityConfig, ProjectileManager manager) {

        int fireTicks = 100; // 5 seconds default
        if (abilityConfig != null) {
            fireTicks = abilityConfig.getInt("fire-ticks", 100);
        }

        // Entity hit - set on fire
        if (event.getHitEntity() instanceof LivingEntity le) {
            le.setFireTicks(fireTicks);
            return true;
        }

        // Block hit - bounce like slimeball
        int remaining = manager.getRemainingBounces(snowball);
        if (remaining <= 0) {
            return true;
        }

        BlockFace face = event.getHitBlockFace();
        if (face == null) {
            return true;
        }

        Vector v = snowball.getVelocity().clone();
        if (v.lengthSquared() < 1e-6) {
            v = snowball.getLocation().getDirection();
        }

        double damp = 0.8;
        switch (face) {
            case NORTH, SOUTH -> v.setZ(-v.getZ());
            case EAST, WEST -> v.setX(-v.getX());
            case UP, DOWN -> v.setY(-v.getY());
            default -> v.multiply(-1);
        }
        v.multiply(damp);

        final Vector finalVelocity = v.clone();
        final int newBounces = remaining - 1;

        JavaPlugin plugin = manager.getPlugin();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!(snowball.getShooter() instanceof Player shooter)) return;

                Location spawnLoc = snowball.getLocation().add(finalVelocity.clone().normalize().multiply(0.1));
                manager.spawnBouncedProjectile(shooter, spawnLoc, finalVelocity, definition, newBounces);
            }
        }.runTask(plugin);

        return true;
    }
}

/**
 * Potion ability - applies a configurable potion effect to hit entities.
 */
class PotionAbility implements Ability {
    @Override
    public boolean onHit(ProjectileHitEvent event, Snowball snowball, YeetableDefinition definition,
                         ConfigurationSection abilityConfig, ProjectileManager manager) {

        if (!(event.getHitEntity() instanceof LivingEntity le)) {
            return true; // Only affects entities
        }

        // Get potion effect parameters from config
        String effectName = "SLOWNESS";
        int duration = 100; // 5 seconds
        int amplifier = 0;  // Level 1

        if (abilityConfig != null) {
            effectName = abilityConfig.getString("effect", "SLOWNESS").toUpperCase();
            duration = abilityConfig.getInt("duration", 100);
            amplifier = abilityConfig.getInt("amplifier", 0);
        }

        PotionEffectType effectType = PotionEffectType.getByName(effectName);
        if (effectType == null) {
            // Try registry lookup for newer versions
            effectType = org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(effectName.toLowerCase()));
        }

        if (effectType != null) {
            le.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
        }

        return true;
    }
}

/**
 * Swap ability - swaps positions of thrower and hit entity.
 */
class SwapAbility implements Ability {
    @Override
    public boolean onHit(ProjectileHitEvent event, Snowball snowball, YeetableDefinition definition,
                         ConfigurationSection abilityConfig, ProjectileManager manager) {

        // Only swap on entity hit
        if (!(event.getHitEntity() instanceof LivingEntity hitEntity)) {
            return true; // Block hit - do nothing, destroy projectile
        }

        if (!(snowball.getShooter() instanceof Player shooter)) {
            return true;
        }

        // Get locations before swap
        Location shooterLoc = shooter.getLocation();
        Location entityLoc = hitEntity.getLocation();

        // Swap positions (preserve yaw/pitch for each entity)
        shooter.teleport(entityLoc.clone().setDirection(shooterLoc.getDirection()));
        hitEntity.teleport(shooterLoc.clone().setDirection(entityLoc.getDirection()));

        // Play impact sound at both locations (configured via sounds.impact in yeetables.yml)
        SoundConfig soundConfig = definition.soundConfig();
        if (soundConfig != null && soundConfig.impact() != null) {
            shooterLoc.getWorld().playSound(shooterLoc, soundConfig.impact(), soundConfig.volume(), soundConfig.pitch());
            entityLoc.getWorld().playSound(entityLoc, soundConfig.impact(), soundConfig.volume(), soundConfig.pitch());
        }

        // Cancel event to prevent vanilla snowball knockback, then remove projectile manually
        event.setCancelled(true);
        snowball.remove();

        return true;
    }
}

/**
 * Grapple ability - launches a hook (arrow) with a lead visual that pulls the player toward the impact point.
 * Uses an invisible chicken leashed to the player that teleports to follow the arrow.
 */
class GrappleAbility implements Ability {
    private static final Map<UUID, GrappleState> activeGrapples = new HashMap<>();
    private static final NamespacedKey GRAPPLE_ANCHOR_KEY = new NamespacedKey("yeetables", "grapple_anchor");

    private record GrappleState(Arrow arrow, Chicken leashAnchor, BukkitTask task, int itemSlot) {}

    @Override
    public boolean onHit(ProjectileHitEvent event, Snowball snowball, YeetableDefinition definition,
                         ConfigurationSection abilityConfig, ProjectileManager manager) {
        // Not used - grapple uses arrows, handled via onArrowHit
        return true;
    }

    /**
     * Called when a grapple arrow hits something.
     */
    public static void onArrowHit(ProjectileHitEvent event, Arrow arrow,
                                   ConfigurationSection abilityConfig) {
        if (!(arrow.getShooter() instanceof Player player)) {
            return;
        }

        GrappleState state = activeGrapples.get(player.getUniqueId());
        if (state == null || state.arrow() != arrow) {
            return; // Not our arrow
        }

        // If arrow hit the player who launched it, cancel and let it keep flying
        if (event.getHitEntity() != null && event.getHitEntity().equals(player)) {
            event.setCancelled(true);
            return;
        }

        // Get impact location before cleanup
        Location hitLoc;
        if (event.getHitEntity() != null) {
            hitLoc = event.getHitEntity().getLocation();
        } else if (event.getHitBlock() != null) {
            hitLoc = event.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
        } else {
            cleanup(player.getUniqueId(), player);
            return;
        }

        // Calculate pull velocity (minimal vertical boost)
        double pullStrength = abilityConfig != null
            ? abilityConfig.getDouble("pull-strength", 1.2) : 1.2;

        Vector toHook = hitLoc.toVector().subtract(player.getLocation().toVector());
        double distance = toHook.length();

        Vector pullVelocity = toHook.normalize()
            .multiply(Math.min(pullStrength * Math.sqrt(distance), 3.0));
        // Only slight vertical adjustment to clear obstacles
        pullVelocity.setY(Math.max(pullVelocity.getY(), 0.1));

        player.setVelocity(pullVelocity);

        // Cleanup after applying velocity
        cleanup(player.getUniqueId(), player);
        arrow.remove();
    }

    /**
     * Called when grapple arrow is launched - spawns lead anchor and starts tracking.
     */
    public static void onLaunch(Player player, Arrow arrow,
                                 ConfigurationSection abilityConfig,
                                 JavaPlugin plugin) {
        // Cancel any existing grapple
        cleanup(player.getUniqueId(), player);

        int despawnTicks = abilityConfig != null
            ? abilityConfig.getInt("despawn-ticks", 100) : 100;

        // Store item slot for recharging later
        int itemSlot = player.getInventory().getHeldItemSlot();

        // Uncharge the crossbow while grapple is active
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getItemMeta() instanceof CrossbowMeta meta) {
            meta.setChargedProjectiles(List.of());
            item.setItemMeta(meta);
        }

        // Spawn invisible chicken as lead anchor - leash AFTER spawning to prevent immediate drop
        Chicken anchor = player.getWorld().spawn(arrow.getLocation(), Chicken.class, c -> {
            c.setInvisible(true);
            c.setSilent(true);
            c.setInvulnerable(true);
            c.setAI(false);
            c.setGravity(false);
            c.setBaby();
            c.setAgeLock(true);
            c.setLootTable(null);
            // Tag as grapple anchor for unleash event detection
            c.getPersistentDataContainer().set(GRAPPLE_ANCHOR_KEY, PersistentDataType.BYTE, (byte) 1);
        });

        // Set leash holder after spawn completes
        anchor.setLeashHolder(player);

        // Teleport chicken to follow arrow each tick
        BukkitTask task = new BukkitRunnable() {
            int ticksAlive = 0;

            @Override
            public void run() {
                if (arrow.isDead() || !arrow.isValid() || !anchor.isValid()) {
                    cleanup(player.getUniqueId(), player);
                    cancel();
                    return;
                }

                if (ticksAlive >= despawnTicks) {
                    cleanup(player.getUniqueId(), player);
                    arrow.remove();
                    cancel();
                    return;
                }

                // Teleport anchor to arrow position (offset down half a block for better lead visual)
                anchor.teleport(arrow.getLocation().subtract(0, 0.5, 0));
                ticksAlive++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeGrapples.put(player.getUniqueId(), new GrappleState(arrow, anchor, task, itemSlot));
    }

    /**
     * Check if an arrow belongs to an active grapple.
     */
    public static boolean isGrappleArrow(Arrow arrow) {
        if (!(arrow.getShooter() instanceof Player player)) {
            return false;
        }
        GrappleState state = activeGrapples.get(player.getUniqueId());
        return state != null && state.arrow() == arrow;
    }

    /**
     * Check if an entity is a grapple anchor chicken.
     */
    public static boolean isGrappleAnchor(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof Chicken)) {
            return false;
        }
        return entity.getPersistentDataContainer().has(GRAPPLE_ANCHOR_KEY, PersistentDataType.BYTE);
    }

    /**
     * Check if a player has an active grapple projectile.
     */
    public static boolean hasActiveGrapple(Player player) {
        return activeGrapples.containsKey(player.getUniqueId());
    }

    private static void cleanup(UUID playerId, Player player) {
        GrappleState state = activeGrapples.remove(playerId);
        if (state != null) {
            state.task().cancel();

            // Remove chicken without dropping lead - set leash holder to null first
            if (state.leashAnchor().isValid()) {
                state.leashAnchor().setLeashHolder(null);
                state.leashAnchor().remove();
            }

            // Recharge the crossbow
            if (player != null && player.isOnline()) {
                ItemStack item = player.getInventory().getItem(state.itemSlot());
                if (item != null && item.getItemMeta() instanceof CrossbowMeta meta) {
                    meta.addChargedProjectile(new ItemStack(Material.ARROW));
                    item.setItemMeta(meta);
                }
            }
        }
    }
}
