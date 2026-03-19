package net.prismacraft.glow.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.prismacraft.glow.config.GlowDefinition;
import net.prismacraft.glow.service.GlowService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class GlowPlaceholderExpansion extends PlaceholderExpansion {

    private final GlowService glowService;

    public GlowPlaceholderExpansion(GlowService glowService) {
        this.glowService = glowService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "glow";
    }

    @Override
    public @NotNull String getAuthor() {
        return "KermX";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) return "";

        Player online = (Player) player;

        if (params.equalsIgnoreCase("owned")) {
            long owned = glowService.config().definitions().stream()
                    .filter(def -> glowService.isUnlocked(online, def))
                    .count();
            return Long.toString(owned);
        }

        if (params.equalsIgnoreCase("glowcolor") || params.equalsIgnoreCase("color")) {
            return currentGlowLegacyColorCode(online);
        }

        // %glow_unlock_<glowId>_required%
        // %glow_unlock_<glowId>_current%
        if (params.regionMatches(true, 0, "unlock_", 0, "unlock_".length())) {
            String rest = params.substring("unlock_".length());
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore <= 0 || lastUnderscore >= rest.length() - 1) {
                return null;
            }

            String glowId = rest.substring(0, lastUnderscore);
            String type = rest.substring(lastUnderscore + 1);

            Optional<GlowDefinition> defOpt = glowService.config().definition(glowId);
            if (defOpt.isEmpty()) return "0";

            GlowDefinition def = defOpt.get();

            if (type.equalsIgnoreCase("required") || type.equalsIgnoreCase("req")) {
                return Integer.toString(Math.max(0, def.requiresUnlockedGlows()));
            }

            if (type.equalsIgnoreCase("current") || type.equalsIgnoreCase("cur")) {
                return Long.toString(currentUnlockedCountForRequirement(online, def));
            }

            return null;
        }

        return null;
    }

    private long currentUnlockedCountForRequirement(Player player, GlowDefinition def) {
        int required = def.requiresUnlockedGlows();
        if (required <= 0) {
            return 0L;
        }

        if (!def.requiresUnlockedFrom().isEmpty()) {
            return def.requiresUnlockedFrom().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(id -> !id.equalsIgnoreCase(def.id()))
                    .map(id -> glowService.config().definition(id))
                    .flatMap(Optional::stream)
                    .filter(other -> other.permission().map(player::hasPermission).orElse(true))
                    .count();
        }

        return glowService.config().definitions().stream()
                .filter(other -> !other.id().equalsIgnoreCase(def.id()))
                .filter(other -> other.permission().map(player::hasPermission).orElse(true))
                .count();
    }

    private String currentGlowLegacyColorCode(Player player) {
        var equippedOpt = glowService.equipped(player);
        if (equippedOpt.isEmpty()) {
            return "";
        }

        var state = equippedOpt.get();
        var defOpt = glowService.config().definition(state.glowId());
        if (defOpt.isEmpty()) {
            return "";
        }

        GlowDefinition def = defOpt.get();
        if (def.colors().isEmpty()) {
            return "";
        }

        int cycle = Math.max(1, def.cycleTicks());
        int idx = (int) ((state.ticksLived() / cycle) % def.colors().size());

        String key = def.colors().get(idx);
        return toLegacyColorCode(key);
    }

    private static String toLegacyColorCode(String key) {
        if (key == null) return "";
        String k = key.trim().toLowerCase(Locale.ROOT);

        return switch (k) {
            case "black" -> "&0";
            case "dark_blue" -> "&1";
            case "dark_green" -> "&2";
            case "dark_aqua" -> "&3";
            case "dark_red" -> "&4";
            case "dark_purple" -> "&5";
            case "gold", "orange" -> "&6";
            case "gray", "grey" -> "&7";
            case "dark_gray", "dark_grey" -> "&8";
            case "blue" -> "&9";
            case "green" -> "&a";
            case "aqua", "cyan" -> "&b";
            case "red" -> "&c";
            case "light_purple", "pink" -> "&d";
            case "yellow" -> "&e";
            case "white" -> "&f";
            default -> "";
        };
    }


    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return onRequest(player, params);
    }
}