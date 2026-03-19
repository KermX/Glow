package net.prismacraft.glow.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class GlowConfig {

    private final Map<String, GlowDefinition> definitionsById;

    public GlowConfig(Map<String, GlowDefinition> definitionsById) {
        this.definitionsById = Map.copyOf(definitionsById);
    }

    public Collection<GlowDefinition> definitions() {
        return definitionsById.values();
    }

    public Optional<GlowDefinition> definition(String id) {
        return Optional.ofNullable(definitionsById.get(id));
    }

    public static GlowConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection glows = cfg.getConfigurationSection("glows");
        if (glows == null) {
            return new GlowConfig(Map.of());
        }

        Map<String, GlowDefinition> out = new LinkedHashMap<>();

        for (String id : glows.getKeys(false)) {
            ConfigurationSection sec = glows.getConfigurationSection(id);
            if (sec == null) continue;

            List<String> colors = sec.getStringList("colors");
            if (colors == null || colors.isEmpty()) {
                continue;
            }

            int cycleTicks = Math.max(1, sec.getInt("cycle-ticks", 20));
            String permission = sec.getString("permission", null);
            String displayName = sec.getString("name", null);

            int requiresUnlockedGlows = Math.max(0, sec.getInt("requires-unlocked-glows", 0));
            List<String> requiresUnlockedFrom = sec.getStringList("requires-unlocked-from");

            GlowDefinition.Icon icon = readIcon(sec);

            GlowDefinition def = new GlowDefinition(
                    id,
                    colors,
                    cycleTicks,
                    permission,
                    requiresUnlockedGlows,
                    requiresUnlockedFrom,
                    icon,
                    displayName
            );

            out.put(id, def);
        }

        return new GlowConfig(out);
    }

    private static GlowDefinition.Icon readIcon(ConfigurationSection glowSection) {
        Object rawIcon = glowSection.get("icon");
        if (rawIcon == null) return null;

        if (rawIcon instanceof String material) {
            return new GlowDefinition.Icon(material, null);
        }

        ConfigurationSection iconSec = glowSection.getConfigurationSection("icon");
        if (iconSec == null) return null;

        String material = iconSec.getString("material", null);
        if (material == null || material.isBlank()) return null;

        String name = iconSec.getString("name", null);
        return new GlowDefinition.Icon(material, name);
    }
}