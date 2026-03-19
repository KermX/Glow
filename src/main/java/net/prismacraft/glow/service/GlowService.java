package net.prismacraft.glow.service;

import net.prismacraft.glow.config.GlowConfig;
import net.prismacraft.glow.config.GlowDefinition;
import net.prismacraft.glow.model.EquippedGlow;
import net.prismacraft.glow.storage.GlowProfileStore;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GlowService {

    private final GlowConfig config;
    private final GlowProfileStore store;

    private final Map<UUID, EquippedGlow> equipped = new ConcurrentHashMap<>();

    public GlowService(GlowConfig config, GlowProfileStore store) {
        this.config = config;
        this.store = store;
    }

    public GlowConfig config() {
        return config;
    }

    public Optional<EquippedGlow> equipped(Player player) {
        return Optional.ofNullable(equipped.get(player.getUniqueId()));
    }

    public boolean isUnlocked(Player player, GlowDefinition def) {
        if (def.permission().isPresent() && !player.hasPermission(def.permission().get())) {
            return false;
        }

        int required = def.requiresUnlockedGlows();
        if (required <= 0) {
            return true;
        }

        if (!def.requiresUnlockedFrom().isEmpty()) {
            long unlockedCount = def.requiresUnlockedFrom().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(id -> !id.equalsIgnoreCase(def.id()))
                    .map(id -> config.definition(id))
                    .flatMap(Optional::stream)
                    .filter(other -> other.permission().map(player::hasPermission).orElse(true))
                    .count();

            return unlockedCount >= required;
        }

        long unlockedCount = config.definitions().stream()
                .filter(other -> !other.id().equalsIgnoreCase(def.id()))
                .filter(other -> other.permission().map(player::hasPermission).orElse(true))
                .count();

        return unlockedCount >= required;
    }

    public boolean loadFor(Player player) {
        Optional<String> storedGlowId = store.getEquippedGlowId(player.getUniqueId());
        if (storedGlowId.isEmpty()) return false;

        boolean ok = equip(player, storedGlowId.get());
        if (!ok) {
            store.clearEquippedGlowId(player.getUniqueId());
            unequip(player);
            return false;
        }
        return true;
    }

    public boolean equip(Player player, String glowId) {
        Optional<GlowDefinition> defOpt = config.definition(glowId);
        if (defOpt.isEmpty()) return false;

        GlowDefinition def = defOpt.get();
        if (!isUnlocked(player, def)) {
            return false;
        }

        equipped.put(player.getUniqueId(), new EquippedGlow(glowId));
        store.setEquippedGlowId(player.getUniqueId(), glowId);
        return true;
    }

    public void unequip(Player player) {
        equipped.remove(player.getUniqueId());
        store.clearEquippedGlowId(player.getUniqueId());
    }

    public void clearAll() {
        equipped.clear();
    }
}