package net.prismacraft.glow.service;

import org.bukkit.entity.Player;

public final class VisibilityService {

    public boolean canViewerSeeTarget(Player viewer, Player target) {
        if (viewer == null || target == null) return false;
        if (!viewer.isOnline() || !target.isOnline()) return false;
        if (viewer.getWorld() == null || target.getWorld() == null) return false;
        if (!viewer.getWorld().equals(target.getWorld())) return false;
        return viewer.canSee(target);
    }
}
