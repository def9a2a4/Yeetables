package anon.def9a2a4.yeetables;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YeetablesPlugin extends JavaPlugin implements Listener, TabCompleter {

    private ConfigManager configManager;
    private ProjectileManager projectileManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.load();

        projectileManager = new ProjectileManager(this, configManager);

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("yeetables").setTabCompleter(this);
        getLogger().info("Yeetables enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Yeetables disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("yeetables")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("yeetables.reload")) {
                    sender.sendMessage("You don't have permission to reload this plugin.");
                    return true;
                }
                reloadConfig();
                configManager.load();
                sender.sendMessage("Yeetables config reloaded!");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
                // Show yeetables (throwables)
                List<YeetableDefinition> enabled = configManager.getEnabledYeetables();
                sender.sendMessage("Yeetables (throwable items) (" + enabled.size() + "):");
                for (YeetableDefinition def : enabled) {
                    sender.sendMessage("- " + def.id());
                }

                // Show custom items (spawnable)
                Map<String, CustomItemDefinition> items = configManager.getCustomItems();
                sender.sendMessage("Custom items (use /yeetables give <item>) (" + items.size() + "):");
                for (String id : items.keySet()) {
                    sender.sendMessage("- " + id);
                }
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                if (!sender.hasPermission("yeetables.give")) {
                    sender.sendMessage("You don't have permission to use this command.");
                    return true;
                }
                String itemId = args[1];
                CustomItemDefinition item = configManager.getCustomItem(itemId);
                if (item == null) {
                    sender.sendMessage("Unknown item: " + itemId);
                    return true;
                }
                player.getInventory().addItem(item.createItemStack());
                String displayName = item.displayName() != null ? item.displayName() : itemId;
                sender.sendMessage("Gave you 1x " + displayName);
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("yeetables")) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument: subcommand
            List<String> subcommands = new ArrayList<>();
            subcommands.add("list");
            if (sender.hasPermission("yeetables.reload")) {
                subcommands.add("reload");
            }
            if (sender.hasPermission("yeetables.give")) {
                subcommands.add("give");
            }
            for (String sub : subcommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Second argument for give: item names
            if (sender.hasPermission("yeetables.give")) {
                for (String itemId : configManager.getCustomItems().keySet()) {
                    if (itemId.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(itemId);
                    }
                }
            }
        }

        return completions;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();

        YeetableDefinition def = configManager.findMatchingYeetable(inHand);
        if (def == null) return;

        if (projectileManager.isOnCooldown(player, def)) return;

        projectileManager.launch(player, def);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball snowball) {
            projectileManager.handleHit(event, snowball);
        } else if (event.getEntity() instanceof Arrow arrow) {
            // Check if this is a grapple arrow
            if (GrappleAbility.isGrappleArrow(arrow)) {
                YeetableDefinition def = configManager.getYeetableById("grappling_hook");
                if (def != null) {
                    GrappleAbility.onArrowHit(event, arrow, def.abilityConfig());
                }
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null) return;

        // Check if this is a grappling hook - prevent normal crossbow firing
        YeetableDefinition def = configManager.findMatchingYeetable(bow);
        if (def != null && "grapple".equals(def.ability())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityUnleash(EntityUnleashEvent event) {
        // Prevent lead from breaking due to distance or player interaction for grapple anchors
        if (GrappleAbility.isGrappleAnchor(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
