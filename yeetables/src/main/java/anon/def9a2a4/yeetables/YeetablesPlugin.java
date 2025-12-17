package anon.def9a2a4.yeetables;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class YeetablesPlugin extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private ProjectileManager projectileManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.load();

        projectileManager = new ProjectileManager(this, configManager);

        Bukkit.getPluginManager().registerEvents(this, this);
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
        }
        return false;
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
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        projectileManager.handleHit(event, snowball);
    }
}
