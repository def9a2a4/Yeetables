package anon.def9a2a4.yeetables;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class HelpProvider {

    // Color scheme
    private static final ChatColor PRIMARY = ChatColor.GOLD;
    private static final ChatColor SECONDARY = ChatColor.YELLOW;
    private static final ChatColor HIGHLIGHT = ChatColor.AQUA;
    private static final ChatColor TEXT = ChatColor.GRAY;
    private static final ChatColor PERMISSION = ChatColor.RED;

    private final ConfigManager configManager;

    public HelpProvider(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void showHelp(CommandSender sender) {
        sender.sendMessage(header("Commands"));

        sender.sendMessage(HIGHLIGHT + "/yeetables help");
        sender.sendMessage(TEXT + "  Show this help message");

        sender.sendMessage(HIGHLIGHT + "/yeetables list");
        sender.sendMessage(TEXT + "  List all yeetables and custom items");

        sender.sendMessage(HIGHLIGHT + "/yeetables give <item>");
        sender.sendMessage(TEXT + "  Give yourself a custom item");
        sender.sendMessage(PERMISSION + "  Requires: " + TEXT + "yeetables.give");

        sender.sendMessage(HIGHLIGHT + "/yeetables reload");
        sender.sendMessage(TEXT + "  Reload plugin configuration");
        sender.sendMessage(PERMISSION + "  Requires: " + TEXT + "yeetables.reload");

        // Show available custom items
        Map<String, CustomItemDefinition> items = configManager.getCustomItems();
        if (!items.isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(SECONDARY + "Available items for /give:");
            for (String id : items.keySet()) {
                sender.sendMessage(TEXT + "  - " + HIGHLIGHT + id);
            }
        }
    }

    public void showGiveUsage(CommandSender sender) {
        sender.sendMessage(SECONDARY + "Usage: " + HIGHLIGHT + "/yeetables give <item>");
        sender.sendMessage("");
        Map<String, CustomItemDefinition> items = configManager.getCustomItems();
        if (items.isEmpty()) {
            sender.sendMessage(TEXT + "No custom items available.");
        } else {
            sender.sendMessage(SECONDARY + "Available items:");
            for (String id : items.keySet()) {
                sender.sendMessage(TEXT + "  - " + HIGHLIGHT + id);
            }
        }
    }

    private String header(String title) {
        return PRIMARY + "========== " + SECONDARY + title + PRIMARY + " ==========";
    }
}
