package anon.def9a2a4.yeetables;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    private static final String GITHUB_URL = "https://github.com/def9a2a4/Yeetables";
    private static final String DISCORD_URL = "https://def9a2a4.github.io/discord.html";

    private final ConfigManager configManager;
    private final String version;

    public HelpProvider(ConfigManager configManager, String version) {
        this.configManager = configManager;
        this.version = version;
    }

    public void showHelp(CommandSender sender) {
        sender.sendMessage(header("Yeetables v" + version));
        sender.sendMessage(TEXT + "Throwable items with configurable physics, abilities, and rendering.");

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

        sender.sendMessage("");
        sender.sendMessage(link("GitHub", GITHUB_URL));
        sender.sendMessage(link("Discord", DISCORD_URL));
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

    private static Component link(String label, String url) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url)));
    }

    private String header(String title) {
        return PRIMARY + "========== " + SECONDARY + title + PRIMARY + " ==========";
    }
}
