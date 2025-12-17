package anon.def9a2a4.yeetables;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class AbilityRegistry {
    private static final Map<String, Ability> ABILITIES = new HashMap<>();

    static {
        ABILITIES.put("bounce", new BounceAbility());
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
