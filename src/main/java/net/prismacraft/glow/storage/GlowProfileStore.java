package net.prismacraft.glow.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GlowProfileStore {

    private final JavaPlugin plugin;
    private final File file;

    // uuid -> glowId
    private final Map<UUID, String> equippedByUuid = new ConcurrentHashMap<>();

    public GlowProfileStore(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(plugin.getDataFolder(), "profiles.yml");
    }

    public void load() {
        equippedByUuid.clear();

        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        var sec = yml.getConfigurationSection("profiles");
        if (sec == null) return;

        for (String uuidStr : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String glowId = sec.getString(uuidStr + ".equipped", null);
                if (glowId != null && !glowId.isBlank()) {
                    equippedByUuid.put(uuid, glowId);
                }
            } catch (IllegalArgumentException ignored) {
                // ignore invalid UUID keys
            }
        }
    }

    public void save() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration yml = new YamlConfiguration();

        for (var e : equippedByUuid.entrySet()) {
            yml.set("profiles." + e.getKey() + ".equipped", e.getValue());
        }

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save profiles.yml: " + e.getMessage());
        }
    }

    public Optional<String> getEquippedGlowId(UUID uuid) {
        return Optional.ofNullable(equippedByUuid.get(uuid));
    }

    public void setEquippedGlowId(UUID uuid, String glowId) {
        if (glowId == null || glowId.isBlank()) {
            equippedByUuid.remove(uuid);
        } else {
            equippedByUuid.put(uuid, glowId);
        }
    }

    public void clearEquippedGlowId(UUID uuid) {
        equippedByUuid.remove(uuid);
    }
}