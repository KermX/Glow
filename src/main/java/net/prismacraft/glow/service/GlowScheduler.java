package net.prismacraft.glow.service;

import net.prismacraft.glow.render.GlowRenderService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class GlowScheduler {

    private final JavaPlugin plugin;
    private final GlowRenderService renderService;
    private int taskId = -1;

    public GlowScheduler(JavaPlugin plugin, GlowRenderService renderService) {
        this.plugin = plugin;
        this.renderService = renderService;
    }

    public void start() {
        if (taskId != -1) return;

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                renderService::tick,
                1L,
                1L
        );
    }

    public void stop() {
        if (taskId == -1) return;
        Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }
}