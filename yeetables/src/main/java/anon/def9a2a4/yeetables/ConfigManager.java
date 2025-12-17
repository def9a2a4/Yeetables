package anon.def9a2a4.yeetables;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final List<YeetableDefinition> yeetables = new ArrayList<>();
    private final Map<String, CustomItemDefinition> customItems = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        yeetables.clear();
        customItems.clear();

        FileConfiguration cfg = plugin.getConfig();

        // Load custom items first (they may be referenced by yeetables)
        loadCustomItems(cfg);

        // Load yeetables
        loadYeetables(cfg);

        logger.info("Loaded " + customItems.size() + " custom items and " + yeetables.size() + " yeetables");
    }

    private void loadCustomItems(FileConfiguration cfg) {
        ConfigurationSection section = cfg.getConfigurationSection("custom-items");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) continue;

            try {
                CustomItemDefinition item = parseCustomItem(id, itemSection);
                customItems.put(id, item);

                // Register recipe if present
                if (item.recipe() != null) {
                    registerRecipe(id, item);
                }
            } catch (Exception e) {
                logger.warning("Failed to load custom item '" + id + "': " + e.getMessage());
            }
        }
    }

    private CustomItemDefinition parseCustomItem(String id, ConfigurationSection section) {
        Material material = Material.valueOf(section.getString("material", "STONE").toUpperCase());
        String displayName = section.getString("display-name");
        List<String> lore = section.getStringList("lore");

        RecipeDefinition recipe = null;
        ConfigurationSection recipeSection = section.getConfigurationSection("recipe");
        if (recipeSection != null) {
            recipe = parseRecipe(recipeSection);
        }

        return new CustomItemDefinition(id, material, displayName, lore, recipe);
    }

    private RecipeDefinition parseRecipe(ConfigurationSection section) {
        List<String> shape = section.getStringList("shape");
        Map<Character, Material> ingredients = new HashMap<>();

        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredients");
        if (ingredientSection != null) {
            for (String key : ingredientSection.getKeys(false)) {
                if (key.length() == 1) {
                    Material mat = Material.valueOf(ingredientSection.getString(key).toUpperCase());
                    ingredients.put(key.charAt(0), mat);
                }
            }
        }

        return new RecipeDefinition(shape.toArray(new String[0]), ingredients);
    }

    private void registerRecipe(String id, CustomItemDefinition item) {
        ItemStack result = item.createItemStack();
        NamespacedKey key = new NamespacedKey(plugin, "custom_" + id);

        // Remove existing recipe if reloading
        Bukkit.removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(item.recipe().shape());

        for (Map.Entry<Character, Material> entry : item.recipe().ingredients().entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }

        Bukkit.addRecipe(recipe);
    }

    private void loadYeetables(FileConfiguration cfg) {
        List<Map<?, ?>> list = cfg.getMapList("yeetables");

        for (Map<?, ?> entry : list) {
            try {
                YeetableDefinition def = parseYeetable(entry);
                yeetables.add(def);
            } catch (Exception e) {
                logger.warning("Failed to load yeetable: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private YeetableDefinition parseYeetable(Map<?, ?> entry) {
        String id = (String) entry.get("id");
        if (id == null) throw new IllegalArgumentException("Yeetable missing 'id'");

        // Parse item matcher
        Map<?, ?> itemMap = (Map<?, ?>) entry.get("item");
        ItemMatcher itemMatcher = parseItemMatcher(itemMap);

        // Parse properties
        Map<?, ?> propsMap = (Map<?, ?>) entry.get("properties");
        ProjectileProperties properties = parseProperties(propsMap);

        // Parse render config
        Map<?, ?> renderMap = (Map<?, ?>) entry.get("render");
        RenderConfig renderConfig = parseRenderConfig(renderMap);

        // Parse consumption behavior
        Object consumptionObj = entry.get("consumption");
        String consumptionStr = (consumptionObj instanceof String s) ? s : "MAIN_HAND";
        ConsumptionBehavior consumption = ConsumptionBehavior.valueOf(consumptionStr.toUpperCase());

        // Parse impact particles
        Map<?, ?> particlesMap = (Map<?, ?>) entry.get("impact-particles");
        ImpactParticleConfig impactParticles = parseImpactParticles(particlesMap);

        // Parse ability (optional)
        String ability = (String) entry.get("ability");
        ConfigurationSection abilityConfig = null;
        if (entry.get("ability-config") instanceof Map<?, ?> abilityMap) {
            // Convert map to ConfigurationSection for easier access
            abilityConfig = mapToConfigSection(abilityMap);
        }

        return new YeetableDefinition(id, itemMatcher, properties, renderConfig, consumption, impactParticles, ability, abilityConfig);
    }

    private ItemMatcher parseItemMatcher(Map<?, ?> map) {
        if (map == null) throw new IllegalArgumentException("Yeetable missing 'item' section");

        Material material = Material.valueOf(((String) map.get("material")).toUpperCase());
        String displayName = (String) map.get("display-name");

        List<String> lore = new ArrayList<>();
        Object loreObj = map.get("lore");
        if (loreObj instanceof List<?> loreList) {
            for (Object o : loreList) {
                if (o instanceof String s) lore.add(s);
            }
        }

        return new ItemMatcher(material, displayName, lore);
    }

    private ProjectileProperties parseProperties(Map<?, ?> map) {
        if (map == null) map = Map.of();

        double speed = getDouble(map, "speed", 1.0);
        double accuracyOffset = getDouble(map, "accuracy-offset", 0.0);
        long cooldown = getLong(map, "cooldown", 500);
        double gravityMultiplier = getDouble(map, "gravity-multiplier", 1.0);
        double damage = getDouble(map, "damage", 0.0);
        double knockbackStrength = getDouble(map, "knockback-strength", 0.0);
        double knockbackVertical = getDouble(map, "knockback-vertical", 0.0);

        Material dropOnBreak = null;
        Object dropObj = map.get("drop-on-break");
        if (dropObj instanceof String dropStr && !dropStr.equalsIgnoreCase("null") && !dropStr.equalsIgnoreCase("none")) {
            try {
                dropOnBreak = Material.valueOf(dropStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return new ProjectileProperties(speed, accuracyOffset, cooldown, gravityMultiplier, damage, knockbackStrength, knockbackVertical, dropOnBreak);
    }

    @SuppressWarnings("unchecked")
    private RenderConfig parseRenderConfig(Map<?, ?> map) {
        if (map == null) return new SimpleRender(Material.STONE);

        Object typeObj = map.get("type");
        String type = (typeObj instanceof String s) ? s : "simple";

        if ("block-display".equals(type)) {
            float yawOffset = getFloat(map, "yaw-offset", 0f);
            float yawMultiplier = getFloat(map, "yaw-multiplier", 1f);
            float pitchOffset = getFloat(map, "pitch-offset", 0f);
            float pitchMultiplier = getFloat(map, "pitch-multiplier", 1f);

            List<ModelPart> parts = new ArrayList<>();
            Object blocksObj = map.get("blocks");
            if (blocksObj instanceof List<?> blocksList) {
                for (Object blockObj : blocksList) {
                    if (blockObj instanceof Map<?, ?> blockMap) {
                        String blockName = (String) blockMap.get("block");
                        List<?> transformList = (List<?>) blockMap.get("transformation");

                        if (blockName != null && transformList != null && transformList.size() == 16) {
                            Material blockMaterial = Material.valueOf(blockName.toUpperCase());
                            float[] matrix = new float[16];
                            for (int i = 0; i < 16; i++) {
                                Object val = transformList.get(i);
                                matrix[i] = (val instanceof Number n) ? n.floatValue() : 0f;
                            }
                            parts.add(new ModelPart(blockMaterial, matrix));
                        }
                    }
                }
            }

            return new BlockDisplayRender(parts, yawOffset, pitchOffset, yawMultiplier, pitchMultiplier);
        } else {
            // Simple render
            Object matObj = map.get("material");
            Material material = Material.valueOf(((matObj instanceof String s) ? s : "STONE").toUpperCase());
            return new SimpleRender(material);
        }
    }

    private ImpactParticleConfig parseImpactParticles(Map<?, ?> map) {
        if (map == null) return new ImpactParticleConfig(15, 0.25, 0.1);

        int count = getInt(map, "count", 15);
        double spread = getDouble(map, "spread", 0.25);
        double velocity = getDouble(map, "velocity", 0.1);

        return new ImpactParticleConfig(count, spread, velocity);
    }

    private ConfigurationSection mapToConfigSection(Map<?, ?> map) {
        // Create a temporary config section from a map
        org.bukkit.configuration.MemoryConfiguration temp = new org.bukkit.configuration.MemoryConfiguration();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            temp.set(e.getKey().toString(), e.getValue());
        }
        return temp;
    }

    // Helper methods for safe number extraction
    private double getDouble(Map<?, ?> map, String key, double def) {
        Object val = map.get(key);
        return (val instanceof Number n) ? n.doubleValue() : def;
    }

    private float getFloat(Map<?, ?> map, String key, float def) {
        Object val = map.get(key);
        return (val instanceof Number n) ? n.floatValue() : def;
    }

    private int getInt(Map<?, ?> map, String key, int def) {
        Object val = map.get(key);
        return (val instanceof Number n) ? n.intValue() : def;
    }

    private long getLong(Map<?, ?> map, String key, long def) {
        Object val = map.get(key);
        return (val instanceof Number n) ? n.longValue() : def;
    }

    // Public accessors

    public List<YeetableDefinition> getYeetables() {
        return yeetables;
    }

    public YeetableDefinition getYeetableById(String id) {
        for (YeetableDefinition def : yeetables) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    public YeetableDefinition findMatchingYeetable(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        // Find best match (most specific first)
        YeetableDefinition bestMatch = null;
        int bestSpecificity = -1;

        for (YeetableDefinition def : yeetables) {
            if (def.itemMatcher().matches(item)) {
                int specificity = def.itemMatcher().specificity();
                if (specificity > bestSpecificity) {
                    bestSpecificity = specificity;
                    bestMatch = def;
                }
            }
        }

        return bestMatch;
    }

    public CustomItemDefinition getCustomItem(String id) {
        return customItems.get(id);
    }
}

// ============================================================================
// Data Records and Types
// ============================================================================

record YeetableDefinition(
    String id,
    ItemMatcher itemMatcher,
    ProjectileProperties properties,
    RenderConfig renderConfig,
    ConsumptionBehavior consumption,
    ImpactParticleConfig impactParticles,
    String ability,
    ConfigurationSection abilityConfig
) {}

record CustomItemDefinition(
    String id,
    Material material,
    String displayName,
    List<String> lore,
    RecipeDefinition recipe
) {
    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(material);
        if (displayName != null || (lore != null && !lore.isEmpty())) {
            ItemMeta meta = stack.getItemMeta();
            if (displayName != null) meta.setDisplayName(displayName);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

record RecipeDefinition(
    String[] shape,
    Map<Character, Material> ingredients
) {}

record ItemMatcher(
    Material material,
    String displayName,
    List<String> lore
) {
    public boolean matches(ItemStack item) {
        if (item == null || item.getType() != material) return false;

        // If no name/lore required, just material match
        if (displayName == null && (lore == null || lore.isEmpty())) {
            return true;
        }

        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        // Check display name
        if (displayName != null) {
            if (!meta.hasDisplayName() || !displayName.equals(meta.getDisplayName())) {
                return false;
            }
        }

        // Check lore contains required lines
        if (lore != null && !lore.isEmpty()) {
            if (!meta.hasLore()) return false;
            List<String> itemLore = meta.getLore();
            for (String required : lore) {
                if (!itemLore.contains(required)) return false;
            }
        }

        return true;
    }

    public int specificity() {
        int score = 0;
        if (displayName != null) score += 10;
        if (lore != null && !lore.isEmpty()) score += 5;
        return score;
    }
}

record ProjectileProperties(
    double speed,
    double accuracyOffset,
    long cooldown,
    double gravityMultiplier,
    double damage,
    double knockbackStrength,
    double knockbackVertical,
    Material dropOnBreak
) {}

record ImpactParticleConfig(
    int count,
    double spread,
    double velocity
) {}

enum ConsumptionBehavior {
    MAIN_HAND,
    OFFHAND,
    NONE
}

// Sealed interface for render configuration
sealed interface RenderConfig permits SimpleRender, BlockDisplayRender {}

record SimpleRender(Material material) implements RenderConfig {}

record BlockDisplayRender(
    List<ModelPart> parts,
    float yawOffset,
    float pitchOffset,
    float yawMultiplier,
    float pitchMultiplier
) implements RenderConfig {}

record ModelPart(
    Material material,
    float[] transformation
) {}
