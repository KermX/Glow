package net.prismacraft.glow.render;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.prismacraft.glow.config.GlowDefinition;
import net.prismacraft.glow.model.ViewerTargetKey;
import net.prismacraft.glow.service.GlowService;
import net.prismacraft.glow.service.VisibilityService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GlowRenderService {

    private final GlowService glowService;
    private final VisibilityService visibilityService;
    private final PlayerManager playerManager = PacketEvents.getAPI().getPlayerManager();

    /**
     * If enabled, this plugin will NOT send scoreboard team packets.
     * Intended for Velocity networks where TAB owns teams (sorting) and reads glow color via placeholder.
     */
    private final boolean tabCompatEnabled;

    private final Map<UUID, Set<NamedTextColor>> viewerTeams = new ConcurrentHashMap<>();
    private final Map<ViewerTargetKey, NamedTextColor> viewerTargetColor = new ConcurrentHashMap<>();

    public GlowRenderService(GlowService glowService, VisibilityService visibilityService, boolean tabCompatEnabled) {
        this.glowService = glowService;
        this.visibilityService = visibilityService;
        this.tabCompatEnabled = tabCompatEnabled;
    }

    public void tick() {
        // TAB compatibility mode:
        // - keep ticking glow state so placeholders reflect cycling colors
        // - ensure glowing flag is on
        // - do NOT create / modify scoreboard teams (TAB handles it)
        if (tabCompatEnabled) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                var equippedOpt = glowService.equipped(target);
                if (equippedOpt.isEmpty()) {
                    continue;
                }

                var state = equippedOpt.get();
                var defOpt = glowService.config().definition(state.glowId());
                if (defOpt.isEmpty()) {
                    continue;
                }

                GlowDefinition def = defOpt.get();
                if (def.colors().isEmpty()) {
                    continue;
                }

                state.tick();

                if (!target.isGlowing()) {
                    target.setGlowing(true);
                }
            }
            return;
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            var equippedOpt = glowService.equipped(target);
            if (equippedOpt.isEmpty()) {
                continue;
            }

            var state = equippedOpt.get();
            var defOpt = glowService.config().definition(state.glowId());
            if (defOpt.isEmpty()) {
                continue;
            }

            GlowDefinition def = defOpt.get();
            if (def.colors().isEmpty()) {
                continue;
            }

            state.tick();

            NamedTextColor color = resolveActiveColor(def, state.ticksLived());

            if (!target.isGlowing()) {
                target.setGlowing(true);
            }

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!visibilityService.canViewerSeeTarget(viewer, target) && viewer != target) {
                    clearForViewer(viewer, target);
                    continue;
                }

                if (viewer == target || visibilityService.canViewerSeeTarget(viewer, target)) {
                    applyForViewer(viewer, target, color);
                }
            }
        }
    }

    public void replayTo(Player viewer) {
        if (tabCompatEnabled) {
            return;
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            var equippedOpt = glowService.equipped(target);
            if (equippedOpt.isEmpty()) {
                continue;
            }

            var state = equippedOpt.get();
            var defOpt = glowService.config().definition(state.glowId());
            if (defOpt.isEmpty()) {
                continue;
            }

            GlowDefinition def = defOpt.get();
            if (def.colors().isEmpty()) {
                continue;
            }

            NamedTextColor color = resolveActiveColor(def, state.ticksLived());

            if (viewer == target || visibilityService.canViewerSeeTarget(viewer, target)) {
                applyForViewer(viewer, target, color);
            }
        }
    }

    public void clearTargetForAllViewers(Player target) {
        if (tabCompatEnabled) {
            return;
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            clearForViewer(viewer, target);
        }
    }

    public void cleanupViewer(Player viewer) {
        if (tabCompatEnabled) {
            return;
        }

        viewerTeams.remove(viewer.getUniqueId());

        UUID viewerId = viewer.getUniqueId();
        viewerTargetColor.keySet().removeIf(k -> k.viewer().equals(viewerId));
    }

    private void applyForViewer(Player viewer, Player target, NamedTextColor desired) {
        ensureTeamExists(viewer, desired);

        ViewerTargetKey key = new ViewerTargetKey(viewer.getUniqueId(), target.getUniqueId());
        NamedTextColor previous = viewerTargetColor.put(key, desired);

        String entry = target.getName();

        if (previous != null && previous != desired) {
            sendTeamRemoveEntry(viewer, previous, entry);
        }

        if (previous == null || previous != desired) {
            sendTeamAddEntry(viewer, desired, entry);
        }
    }

    private void clearForViewer(Player viewer, Player target) {
        ViewerTargetKey key = new ViewerTargetKey(viewer.getUniqueId(), target.getUniqueId());
        NamedTextColor prev = viewerTargetColor.remove(key);
        if (prev == null) return;

        sendTeamRemoveEntry(viewer, prev, target.getName());
    }

    private void ensureTeamExists(Player viewer, NamedTextColor color) {
        UUID viewerId = viewer.getUniqueId();
        Set<NamedTextColor> teams = viewerTeams.computeIfAbsent(viewerId, k -> ConcurrentHashMap.newKeySet());

        if (teams.add(color)) {
            sendTeamCreate(viewer, color);
        }
    }

    private void sendTeamCreate(Player viewer, NamedTextColor color) {
        String name = teamName(viewer.getUniqueId(), color);

        var info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(),
                Component.empty(),
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                WrapperPlayServerTeams.CollisionRule.ALWAYS,
                color,
                WrapperPlayServerTeams.OptionData.NONE
        );

        var create = new WrapperPlayServerTeams(
                name,
                WrapperPlayServerTeams.TeamMode.CREATE,
                info,
                Collections.emptyList()
        );

        playerManager.sendPacket(viewer, create);
    }

    private void sendTeamAddEntry(Player viewer, NamedTextColor color, String entry) {
        var add = new WrapperPlayServerTeams(
                teamName(viewer.getUniqueId(), color),
                WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
                entry
        );
        playerManager.sendPacket(viewer, add);
    }

    private void sendTeamRemoveEntry(Player viewer, NamedTextColor color, String entry) {
        var remove = new WrapperPlayServerTeams(
                teamName(viewer.getUniqueId(), color),
                WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
                (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
                entry
        );
        playerManager.sendPacket(viewer, remove);
    }

    private static String teamName(UUID viewerId, NamedTextColor color) {
        String v = viewerId.toString().replace("-", "").substring(0, 8);
        String c = color.toString().toLowerCase(Locale.ROOT);
        return "glw_" + v + "_" + c;
    }

    private static NamedTextColor resolveActiveColor(GlowDefinition def, long ticksLived) {
        int cycle = Math.max(1, def.cycleTicks());
        int idx = (int) ((ticksLived / cycle) % def.colors().size());

        String key = def.colors().get(idx);
        NamedTextColor parsed = parseNamedTextColor(key);
        return parsed != null ? parsed : NamedTextColor.WHITE;
    }

    private static NamedTextColor parseNamedTextColor(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(Locale.ROOT);

        return switch (k) {
            case "black" -> NamedTextColor.BLACK;
            case "dark_blue" -> NamedTextColor.DARK_BLUE;
            case "dark_green" -> NamedTextColor.DARK_GREEN;
            case "dark_aqua" -> NamedTextColor.DARK_AQUA;
            case "dark_red" -> NamedTextColor.DARK_RED;
            case "dark_purple" -> NamedTextColor.DARK_PURPLE;
            case "gold", "orange" -> NamedTextColor.GOLD;
            case "gray", "grey" -> NamedTextColor.GRAY;
            case "dark_gray", "dark_grey" -> NamedTextColor.DARK_GRAY;
            case "blue" -> NamedTextColor.BLUE;
            case "green" -> NamedTextColor.GREEN;
            case "aqua", "cyan" -> NamedTextColor.AQUA;
            case "red" -> NamedTextColor.RED;
            case "light_purple", "pink" -> NamedTextColor.LIGHT_PURPLE;
            case "yellow" -> NamedTextColor.YELLOW;
            case "white" -> NamedTextColor.WHITE;
            default -> null;
        };
    }
}