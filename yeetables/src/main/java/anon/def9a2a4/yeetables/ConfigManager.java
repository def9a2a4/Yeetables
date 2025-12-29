package anon.def9a2a4.yeetables;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final List<YeetableDefinition> yeetables = new ArrayList<>();
    private final Map<String, CustomItemDefinition> customItems = new HashMap<>();

    private YamlConfiguration itemsConfig;
    private YamlConfiguration yeetablesConfig;

    // Global config values loaded once on reload
    private boolean hideDisplayProjectiles;
    private List<EntityExemption> swapExemptions = new ArrayList<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        yeetables.clear();
        customItems.clear();

        // Load main config (global settings)
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        // Load global config values
        hideDisplayProjectiles = plugin.getConfig().getBoolean("hide-display-projectiles", true);
        swapExemptions = parseSwapExemptions(plugin.getConfig().getMapList("swap-exempt-entities"));

        // Load items.yml
        itemsConfig = loadYamlFile("items.yml");

        // Load yeetables.yml
        yeetablesConfig = loadYamlFile("yeetables.yml");

        // Load custom items first (they may be referenced by yeetables)
        loadCustomItems();

        // Load yeetables
        loadYeetables();

        logger.info("Loaded " + customItems.size() + " custom items and " + yeetables.size() + " yeetables");
    }

    private YamlConfiguration loadYamlFile(String filename) {
        File file = new File(plugin.getDataFolder(), filename);

        // Save default if doesn't exist
        if (!file.exists()) {
            plugin.saveResource(filename, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Load defaults from jar
        InputStream defaultStream = plugin.getResource(filename);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            config.setDefaults(defaultConfig);
        }

        return config;
    }

    private void loadCustomItems() {
        ConfigurationSection section = itemsConfig.getConfigurationSection("items");
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
                e.printStackTrace();
            }
        }
    }

    private CustomItemDefinition parseCustomItem(String id, ConfigurationSection section) {
        Material material = Material.valueOf(section.getString("material", "STONE").toUpperCase());
        String displayName = section.getString("display-name");
        List<String> lore = section.getStringList("lore");
        String texture = section.getString("texture"); // Base64 texture for player heads
        boolean charged = section.getBoolean("charged", false); // For crossbows

        RecipeDefinition recipe = null;
        ConfigurationSection recipeSection = section.getConfigurationSection("recipe");
        if (recipeSection != null) {
            recipe = parseRecipe(recipeSection);
        }

        return new CustomItemDefinition(id, material, displayName, lore, texture, recipe, charged);
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

    private void loadYeetables() {
        List<Map<?, ?>> list = yeetablesConfig.getMapList("yeetables");

        for (Map<?, ?> entry : list) {
            try {
                YeetableDefinition def = parseYeetable(entry);
                yeetables.add(def);
            } catch (Exception e) {
                logger.warning("Failed to load yeetable: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private YeetableDefinition parseYeetable(Map<?, ?> entry) {
        String id = (String) entry.get("id");
        if (id == null) throw new IllegalArgumentException("Yeetable missing 'id'");

        // Parse enabled (defaults to true if not specified)
        boolean enabled = entry.get("enabled") == null || Boolean.TRUE.equals(entry.get("enabled"));

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
            abilityConfig = mapToConfigSection(abilityMap);
        }

        // Parse projectile type (optional, defaults to snowball)
        String projectileType = (String) entry.get("projectile-type");

        // Parse sounds (optional)
        Map<?, ?> soundsMap = (Map<?, ?>) entry.get("sounds");
        SoundConfig soundConfig = parseSoundConfig(soundsMap);

        return new YeetableDefinition(id, enabled, itemMatcher, properties, renderConfig, consumption, impactParticles, ability, abilityConfig, projectileType, soundConfig);
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

        // Parse rotation mode (shared by block-display and item-display)
        RotationMode rotationMode = RotationMode.POINT_FORWARD;
        Object rotModeObj = map.get("rotation-mode");
        if (rotModeObj instanceof String rotModeStr) {
            try {
                rotationMode = RotationMode.valueOf(rotModeStr.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException ignored) {}
        }

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

            return new BlockDisplayRender(parts, yawOffset, pitchOffset, yawMultiplier, pitchMultiplier, rotationMode);
        } else if ("item-display".equals(type)) {
            // Parse transformation matrix
            float[] matrix = new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}; // identity default
            Object transformObj = map.get("transformation");
            if (transformObj instanceof List<?> transformList && transformList.size() == 16) {
                for (int i = 0; i < 16; i++) {
                    Object val = transformList.get(i);
                    matrix[i] = (val instanceof Number n) ? n.floatValue() : 0f;
                }
            }

            // Get item from item-id reference or inline material
            ItemStack item;
            String itemId = (String) map.get("item-id");
            if (itemId != null) {
                CustomItemDefinition customItem = customItems.get(itemId);
                if (customItem != null) {
                    item = customItem.createItemStack();
                } else {
                    logger.warning("Unknown item-id '" + itemId + "' in item-display render");
                    item = new ItemStack(Material.STONE);
                }
            } else {
                Object matObj = map.get("material");
                Material material = Material.valueOf(((matObj instanceof String s) ? s : "STONE").toUpperCase());
                item = new ItemStack(material);
            }

            return new ItemDisplayRender(item, matrix, rotationMode);
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

    private SoundConfig parseSoundConfig(Map<?, ?> map) {
        if (map == null) return null;

        Sound launch = null;
        Sound impact = null;

        Object launchObj = map.get("launch");
        if (launchObj instanceof String launchStr) {
            try {
                launch = Sound.valueOf(launchStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown launch sound: " + launchStr);
            }
        }

        Object impactObj = map.get("impact");
        if (impactObj instanceof String impactStr) {
            try {
                impact = Sound.valueOf(impactStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown impact sound: " + impactStr);
            }
        }

        // If neither sound is configured, return null
        if (launch == null && impact == null) return null;

        float volume = getFloat(map, "volume", 1.0f);
        float pitch = getFloat(map, "pitch", 1.0f);

        return new SoundConfig(launch, impact, volume, pitch);
    }

    private ConfigurationSection mapToConfigSection(Map<?, ?> map) {
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

        YeetableDefinition bestMatch = null;
        int bestSpecificity = -1;

        for (YeetableDefinition def : yeetables) {
            if (!def.enabled()) continue;
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

    public List<YeetableDefinition> getEnabledYeetables() {
        return yeetables.stream().filter(YeetableDefinition::enabled).toList();
    }

    public Map<String, CustomItemDefinition> getCustomItems() {
        return customItems;
    }

    public boolean shouldHideDisplayProjectiles() {
        return hideDisplayProjectiles;
    }

    public List<EntityExemption> getSwapExemptions() {
        return swapExemptions;
    }

    /**
     * Check if an entity is exempt from swap based on global exemption rules.
     */
    public boolean isSwapExempt(Entity entity) {
        for (EntityExemption exemption : swapExemptions) {
            if (exemption.isExempt(entity)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<EntityExemption> parseSwapExemptions(List<Map<?, ?>> list) {
        List<EntityExemption> exemptions = new ArrayList<>();
        if (list == null) {
            return exemptions;
        }

        for (Map<?, ?> map : list) {
            try {
                // Parse entity type
                EntityType entityType = null;
                Object typeObj = map.get("type");
                if (typeObj instanceof String typeStr && !"*".equals(typeStr)) {
                    entityType = EntityType.valueOf(typeStr.toUpperCase());
                }

                // Parse if-has-any-tag
                boolean ifHasAnyTag = false;
                Object anyTagObj = map.get("if-has-any-tag");
                if (anyTagObj instanceof Boolean b) {
                    ifHasAnyTag = b;
                }

                // Parse required tags
                Set<String> requiredTags = new HashSet<>();
                Object tagsObj = map.get("if-has-tags");
                if (tagsObj instanceof List<?> tagsList) {
                    for (Object tag : tagsList) {
                        if (tag instanceof String s) {
                            requiredTags.add(s);
                        }
                    }
                }

                // Parse tag match mode
                boolean requireAllTags = false;
                Object modeObj = map.get("tag-match-mode");
                if (modeObj instanceof String modeStr) {
                    requireAllTags = "ALL".equalsIgnoreCase(modeStr);
                }

                exemptions.add(new EntityExemption(entityType, ifHasAnyTag, requiredTags, requireAllTags));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid entity type in swap-exempt-entities: " + map.get("type"));
            }
        }

        return exemptions;
    }
}

// ============================================================================
// Data Records and Types
// ============================================================================

record YeetableDefinition(
    String id,
    boolean enabled,
    ItemMatcher itemMatcher,
    ProjectileProperties properties,
    RenderConfig renderConfig,
    ConsumptionBehavior consumption,
    ImpactParticleConfig impactParticles,
    String ability,
    ConfigurationSection abilityConfig,
    String projectileType,
    SoundConfig soundConfig
) {}

record CustomItemDefinition(
    String id,
    Material material,
    String displayName,
    List<String> lore,
    String texture,
    RecipeDefinition recipe,
    boolean charged
) {
    public ItemStack createItemStack() {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        if (displayName != null) meta.setDisplayName(displayName);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);

        // Handle player head textures
        if (material == Material.PLAYER_HEAD && texture != null && meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            skullMeta.setPlayerProfile(profile);
        }

        // Handle charged crossbows
        if (material == Material.CROSSBOW && charged && meta instanceof CrossbowMeta crossbowMeta) {
            crossbowMeta.addChargedProjectile(new ItemStack(Material.ARROW));
        }

        stack.setItemMeta(meta);
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

record SoundConfig(
    Sound launch,
    Sound impact,
    float volume,
    float pitch
) {}

enum ConsumptionBehavior {
    MAIN_HAND,
    OFFHAND,
    NONE
}

// Sealed interface for render configuration
sealed interface RenderConfig permits SimpleRender, BlockDisplayRender, ItemDisplayRender {}

record SimpleRender(Material material) implements RenderConfig {}

record BlockDisplayRender(
    List<ModelPart> parts,
    float yawOffset,
    float pitchOffset,
    float yawMultiplier,
    float pitchMultiplier,
    RotationMode rotationMode
) implements RenderConfig {}

record ItemDisplayRender(
    ItemStack item,
    float[] transformation,
    RotationMode rotationMode
) implements RenderConfig {}

record ModelPart(
    Material material,
    float[] transformation
) {}

/**
 * Represents an entity exemption rule for swap ability.
 * @param entityType The entity type to match, or null for wildcard (any entity)
 * @param ifHasAnyTag If true, entity is exempt only if it has at least one scoreboard tag
 * @param requiredTags Specific tags to check (empty = no specific tag requirement)
 * @param requireAllTags If true, entity must have ALL tags; if false, ANY tag matches
 */
record EntityExemption(
    EntityType entityType,
    boolean ifHasAnyTag,
    Set<String> requiredTags,
    boolean requireAllTags
) {
    public boolean isExempt(Entity entity) {
        // Check entity type (null = wildcard)
        if (entityType != null && entity.getType() != entityType) {
            return false;
        }

        // If if-has-any-tag is set, check if entity has any scoreboard tag
        if (ifHasAnyTag) {
            return !entity.getScoreboardTags().isEmpty();
        }

        // If no specific tags required, type match alone is sufficient
        if (requiredTags.isEmpty()) {
            return entityType != null; // Wildcard with no conditions matches nothing
        }

        // Check specific tag requirements
        Set<String> entityTags = entity.getScoreboardTags();
        if (requireAllTags) {
            return entityTags.containsAll(requiredTags);
        } else {
            for (String tag : requiredTags) {
                if (entityTags.contains(tag)) {
                    return true;
                }
            }
            return false;
        }
    }
}
