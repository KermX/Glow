package net.prismacraft.glow;

import com.github.retrooper.packetevents.PacketEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.prismacraft.glow.config.GlowConfig;
import net.prismacraft.glow.gui.GlowMenu;
import net.prismacraft.glow.placeholder.GlowPlaceholderExpansion;
import net.prismacraft.glow.render.GlowRenderService;
import net.prismacraft.glow.service.GlowScheduler;
import net.prismacraft.glow.service.GlowService;
import net.prismacraft.glow.service.VisibilityService;
import net.prismacraft.glow.storage.GlowProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Glow extends JavaPlugin implements Listener {

    private GlowConfig glowConfig;

    private GlowProfileStore profileStore;
    private GlowService glowService;

    private VisibilityService visibilityService;
    private GlowRenderService glowRenderService;
    private GlowScheduler glowScheduler;

    private GlowMenu glowMenu;

    @Override
    public void onEnable() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().init();

        this.glowConfig = GlowConfig.load(this);

        this.profileStore = new GlowProfileStore(this);
        this.profileStore.load();

        this.glowService = new GlowService(glowConfig, profileStore);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GlowPlaceholderExpansion(glowService).register();
        }

        boolean tabCompatEnabled = getConfig().getBoolean("tab-compat.enabled", false);

        this.visibilityService = new VisibilityService();
        this.glowRenderService = new GlowRenderService(glowService, visibilityService, tabCompatEnabled);
        this.glowScheduler = new GlowScheduler(this, glowRenderService);
        this.glowScheduler.start();

        this.glowMenu = new GlowMenu(glowService, glowRenderService);

        Bukkit.getPluginManager().registerEvents(this, this);

        registerCommands();

        for (var p : Bukkit.getOnlinePlayers()) {
            boolean equipped = glowService.loadFor(p);
            p.setGlowing(equipped);
            glowRenderService.replayTo(p);
        }
    }

    @Override
    public void onDisable() {
        if (glowScheduler != null) {
            glowScheduler.stop();
        }

        Bukkit.getOnlinePlayers().forEach(p -> p.setGlowing(false));

        if (profileStore != null) {
            profileStore.save();
        }

        if (glowService != null) {
            glowService.clearAll();
        }

        if (PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }
    }

    private void registerCommands() {

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var root = Commands.literal("glow")
                    .requires(src -> canOpenSelf(src.getSender()) || canOpenOthers(src.getSender()))
                    .executes(ctx -> {
                        var sender = ctx.getSource().getSender();

                        if (sender instanceof org.bukkit.entity.Player player) {
                            if (!canOpenSelf(sender)) {
                                sender.sendMessage("You don't have permission to open your Glow menu.");
                                return 0;
                            }
                            glowMenu.open(player);
                            return 1;
                        }

                        sender.sendMessage("Usage: /glow <player>");
                        return 0;
                    })
                    .then(Commands.argument("player", StringArgumentType.word())
                            .requires(src -> canOpenOthers(src.getSender()))
                            .executes(ctx -> {
                                String targetName = StringArgumentType.getString(ctx, "player");
                                var target = Bukkit.getPlayerExact(targetName);

                                if (target == null) {
                                    ctx.getSource().getSender().sendMessage("Player not found (must be online): " + targetName);
                                    return 0;
                                }

                                glowMenu.open(target);
                                ctx.getSource().getSender().sendMessage("Opened Glow menu for " + target.getName());
                                return 1;
                            }));

            event.registrar().register(root.build(), "glow");
        });
    }

    private static boolean canOpenSelf(CommandSender sender) {
        return sender.hasPermission("glow.menu") || sender.hasPermission("glow.menu.self");
    }

    private static boolean canOpenOthers(CommandSender sender) {
        return sender.hasPermission("glow.menu") || sender.hasPermission("glow.menu.others");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        boolean equipped = glowService.loadFor(e.getPlayer());
        e.getPlayer().setGlowing(equipped);

        glowRenderService.replayTo(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        glowRenderService.clearTargetForAllViewers(e.getPlayer());
        glowRenderService.cleanupViewer(e.getPlayer());
        e.getPlayer().setGlowing(false);
    }
}