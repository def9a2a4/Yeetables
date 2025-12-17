package anon.def9a2a4.yeetables;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ProjectileManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    private final NamespacedKey keyYeetableId;
    private final NamespacedKey keyBounces;
    private final NamespacedKey keyRendererId;

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Random random = new Random();

    public ProjectileManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        this.keyYeetableId = new NamespacedKey(plugin, "yeetable_id");
        this.keyBounces = new NamespacedKey(plugin, "bounces");
        this.keyRendererId = new NamespacedKey(plugin, "renderer_id");
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    // ========================================================================
    // Cooldown Management
    // ========================================================================

    public boolean isOnCooldown(Player player, YeetableDefinition def) {
        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return false;

        Long lastThrow = playerCooldowns.get(def.id());
        if (lastThrow == null) return false;

        return (now - lastThrow) < def.properties().cooldown();
    }

    public void setCooldown(Player player, YeetableDefinition def) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                 .put(def.id(), System.currentTimeMillis());
    }

    // ========================================================================
    // Launching
    // ========================================================================

    public void launch(Player player, YeetableDefinition def) {
        // Check if this is an arrow-based projectile (grapple)
        if ("arrow".equals(def.projectileType())) {
            launchArrow(player, def);
            return;
        }

        ProjectileProperties props = def.properties();

        // Apply accuracy offset
        Vector dir = applyAccuracyOffset(player.getLocation().getDirection(), props.accuracyOffset());

        // Launch snowball
        Snowball snowball = player.launchProjectile(Snowball.class, dir.multiply(props.speed()));

        // Tag with yeetable ID
        PersistentDataContainer pdc = snowball.getPersistentDataContainer();
        pdc.set(keyYeetableId, PersistentDataType.STRING, def.id());

        // Set initial bounces if ability is bounce
        if ("bounce".equals(def.ability()) && def.abilityConfig() != null) {
            int numBounces = def.abilityConfig().getInt("num-bounces", 3);
            pdc.set(keyBounces, PersistentDataType.INTEGER, numBounces);
        }

        // Setup rendering
        RenderConfig renderConfig = def.renderConfig();
        if (renderConfig instanceof SimpleRender simple) {
            snowball.setItem(new ItemStack(simple.material()));
        } else if (renderConfig instanceof BlockDisplayRender blockDisplay) {
            BlockDisplayRenderer renderer = new BlockDisplayRenderer(
                plugin, snowball, props.gravityMultiplier(), blockDisplay
            );
            renderer.spawn();
            pdc.set(keyRendererId, PersistentDataType.INTEGER, renderer.getId());
            if (configManager.shouldHideDisplayProjectiles()) {
                snowball.setItem(new ItemStack(Material.AIR));
            }
        } else if (renderConfig instanceof ItemDisplayRender itemDisplay) {
            ItemDisplayRenderer renderer = new ItemDisplayRenderer(
                plugin, snowball, props.gravityMultiplier(), itemDisplay
            );
            renderer.spawn();
            pdc.set(keyRendererId, PersistentDataType.INTEGER, renderer.getId());
            if (configManager.shouldHideDisplayProjectiles()) {
                snowball.setItem(new ItemStack(Material.AIR));
            }
        }

        // Play launch sound if configured
        playLaunchSound(player.getLocation(), def.soundConfig());

        // Consume item
        consumeItem(player, def.consumption());

        // Set cooldown
        setCooldown(player, def);
    }

    private void launchArrow(Player player, YeetableDefinition def) {
        // Don't fire if player already has an active grapple
        if ("grapple".equals(def.ability()) && GrappleAbility.hasActiveGrapple(player)) {
            return;
        }

        ProjectileProperties props = def.properties();

        Vector dir = applyAccuracyOffset(player.getLocation().getDirection(), props.accuracyOffset());

        Arrow arrow = player.launchProjectile(Arrow.class, dir.multiply(props.speed()));
        arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);

        // Tag with yeetable ID
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.set(keyYeetableId, PersistentDataType.STRING, def.id());

        // Start grapple tracking
        if ("grapple".equals(def.ability())) {
            GrappleAbility.onLaunch(player, arrow, def.abilityConfig(), plugin);
        }

        // Play launch sound if configured
        playLaunchSound(player.getLocation(), def.soundConfig());

        // Consume item
        consumeItem(player, def.consumption());

        // Set cooldown
        setCooldown(player, def);
    }

    public void spawnBouncedProjectile(Player shooter, Location location, Vector velocity,
                                        YeetableDefinition def, int remainingBounces) {
        Snowball snowball = location.getWorld().spawn(location, Snowball.class, s -> {
            s.setVelocity(velocity);
            s.setShooter(shooter);

            PersistentDataContainer pdc = s.getPersistentDataContainer();
            pdc.set(keyYeetableId, PersistentDataType.STRING, def.id());
            pdc.set(keyBounces, PersistentDataType.INTEGER, remainingBounces);

            // Set item appearance
            if (def.renderConfig() instanceof SimpleRender simple) {
                s.setItem(new ItemStack(simple.material()));
            }
        });
    }

    private Vector applyAccuracyOffset(Vector direction, double offset) {
        if (offset <= 0.0) return direction;
        double x = direction.getX() + (random.nextDouble() - 0.5) * offset;
        double y = direction.getY() + (random.nextDouble() - 0.5) * offset;
        double z = direction.getZ() + (random.nextDouble() - 0.5) * offset;
        return new Vector(x, y, z).normalize();
    }

    private void consumeItem(Player player, ConsumptionBehavior behavior) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (behavior == ConsumptionBehavior.NONE) return;

        ItemStack stack;
        if (behavior == ConsumptionBehavior.OFFHAND) {
            stack = player.getInventory().getItemInOffHand();
            if (stack.getType().isAir()) return;
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItemInOffHand(stack);
        } else {
            stack = player.getInventory().getItemInMainHand();
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItemInMainHand(stack);
        }
    }

    private void playLaunchSound(Location loc, SoundConfig soundConfig) {
        if (soundConfig != null && soundConfig.launch() != null) {
            loc.getWorld().playSound(loc, soundConfig.launch(), soundConfig.volume(), soundConfig.pitch());
        } else {
            // Default throw sound for all projectiles
            loc.getWorld().playSound(loc, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.0f);
        }
    }

    private void playImpactSound(Location loc, SoundConfig soundConfig) {
        if (soundConfig == null || soundConfig.impact() == null) return;
        loc.getWorld().playSound(loc, soundConfig.impact(), soundConfig.volume(), soundConfig.pitch());
    }

    // ========================================================================
    // Hit Handling
    // ========================================================================

    public void handleHit(ProjectileHitEvent event, Snowball snowball) {
        PersistentDataContainer pdc = snowball.getPersistentDataContainer();
        String yeetableId = pdc.get(keyYeetableId, PersistentDataType.STRING);
        if (yeetableId == null) return;

        YeetableDefinition def = configManager.getYeetableById(yeetableId);
        if (def == null) {
            plugin.getLogger().warning("Unknown yeetable ID: " + yeetableId);
            return;
        }

        // Clean up renderer if present
        Integer rendererId = pdc.get(keyRendererId, PersistentDataType.INTEGER);
        if (rendererId != null) {
            RendererRegistry.remove(rendererId);
        }

        // Check for ability
        Ability ability = AbilityRegistry.get(def.ability());
        boolean destroy = true;

        if (ability != null) {
            // Spawn particles before ability (for bounce feedback)
            Location particleLoc = getImpactLocation(event, snowball);
            spawnImpactParticles(particleLoc, def);

            destroy = ability.onHit(event, snowball, def, def.abilityConfig(), this);
        }

        if (destroy) {
            // Apply standard hit effects
            applyHitEffects(event, snowball, def);
        }
    }

    private void applyHitEffects(ProjectileHitEvent event, Snowball snowball, YeetableDefinition def) {
        ProjectileProperties props = def.properties();
        Location impactLoc = getImpactLocation(event, snowball);

        // Spawn particles (if not already spawned by ability)
        if (def.ability() == null) {
            spawnImpactParticles(impactLoc, def);
        }

        // Play impact sound if configured
        playImpactSound(impactLoc, def.soundConfig());

        // Entity hit effects
        if (event.getHitEntity() instanceof LivingEntity le && snowball.getShooter() instanceof Player shooter) {
            // Damage
            if (props.damage() > 0) {
                le.damage(props.damage(), shooter);
            }

            // Knockback
            if (props.knockbackStrength() > 0 || props.knockbackVertical() > 0) {
                Vector dir = snowball.getVelocity().clone();
                if (dir.lengthSquared() < 1e-6) {
                    dir = le.getLocation().toVector().subtract(snowball.getLocation().toVector());
                }
                dir.normalize();

                Vector kb = dir.multiply(props.knockbackStrength());
                kb.setY(Math.max(kb.getY(), props.knockbackVertical()));
                le.setVelocity(le.getVelocity().add(kb));
            }
        }

        // Drop item on break
        if (props.dropOnBreak() != null) {
            snowball.getWorld().dropItemNaturally(snowball.getLocation(), new ItemStack(props.dropOnBreak()));
        }
    }

    private Location getImpactLocation(ProjectileHitEvent event, Snowball snowball) {
        if (event.getHitEntity() != null) {
            return event.getHitEntity().getLocation();
        } else if (event.getHitBlock() != null) {
            return event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        }
        return snowball.getLocation();
    }

    private void spawnImpactParticles(Location loc, YeetableDefinition def) {
        ImpactParticleConfig particles = def.impactParticles();
        if (particles.count() <= 0) return;

        // Determine particle material from render config
        Material particleMaterial = Material.STONE;
        if (def.renderConfig() instanceof SimpleRender simple) {
            particleMaterial = simple.material();
        } else if (def.renderConfig() instanceof BlockDisplayRender blockDisplay) {
            if (!blockDisplay.parts().isEmpty()) {
                particleMaterial = blockDisplay.parts().get(0).material();
            }
        }

        ItemStack itemStack = new ItemStack(particleMaterial);
        loc.getWorld().spawnParticle(
            Particle.ITEM,
            loc,
            particles.count(),
            particles.spread(), particles.spread(), particles.spread(),
            particles.velocity(),
            itemStack
        );
    }

    // ========================================================================
    // Bounce Support
    // ========================================================================

    public int getRemainingBounces(Snowball snowball) {
        Integer bounces = snowball.getPersistentDataContainer().get(keyBounces, PersistentDataType.INTEGER);
        return bounces != null ? bounces : 0;
    }
}
