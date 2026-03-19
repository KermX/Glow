package net.prismacraft.glow.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.prismacraft.glow.config.GlowDefinition;
import net.prismacraft.glow.render.GlowRenderService;
import net.prismacraft.glow.service.GlowService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class GlowMenu {

    private static final String TITLE = "Glows";
    private static final int MAX_ROWS = 6;
    private static final int COLORS_PER_LORE_LINE = 5;

    private final GlowService glowService;
    private final GlowRenderService glowRenderService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public GlowMenu(GlowService glowService, GlowRenderService glowRenderService) {
        this.glowService = glowService;
        this.glowRenderService = glowRenderService;
    }

    public void open(Player player) {
        List<GlowDefinition> unlocked = glowService.config().definitions().stream()
                .filter(def -> glowService.isUnlocked(player, def))
                .sorted(Comparator.comparing(d -> d.id().toLowerCase(Locale.ROOT)))
                .toList();

        List<GuiItem> items = buildItems(player, unlocked);

        boolean needsPagination = items.size() > (9 * 5);
        int itemRows = Math.max(1, (int) Math.ceil(items.size() / 9.0));
        int rows = clamp(1, MAX_ROWS, needsPagination ? Math.min(5, itemRows) + 1 : itemRows);

        ChestGui gui = new ChestGui(rows, TITLE);
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        int itemAreaRows = needsPagination ? rows - 1 : rows;
        PaginatedPane pages = new PaginatedPane(0, 0, 9, itemAreaRows);
        pages.populateWithGuiItems(items);
        gui.addPane(pages);

        if (needsPagination) {
            StaticPane controls = new StaticPane(0, rows - 1, 9, 1);

            controls.addItem(new GuiItem(navItem(Material.ARROW, "Previous page"), e -> {
                e.setCancelled(true);
                if (pages.getPage() > 0) {
                    pages.setPage(pages.getPage() - 1);
                    gui.update();
                }
            }), 3, 0);

            controls.addItem(new GuiItem(navItem(Material.ARROW, "Next page"), e -> {
                e.setCancelled(true);
                if (pages.getPage() < pages.getPages() - 1) {
                    pages.setPage(pages.getPage() + 1);
                    gui.update();
                }
            }), 5, 0);

            gui.addPane(controls);

            gui.setOnTopClick(e -> e.setCancelled(true));
        }

        gui.show(player);
    }

    private List<GuiItem> buildItems(Player player, List<GlowDefinition> defs) {
        if (defs.isEmpty()) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            meta.displayName(noItalics(Component.text("No glows unlocked", NamedTextColor.RED)));
            meta.lore(List.of(
                    noItalics(Component.text("You don't have permission for any glows.", NamedTextColor.GRAY))
            ));
            none.setItemMeta(meta);
            return List.of(new GuiItem(none, e -> e.setCancelled(true)));
        }

        String equippedId = glowService.equipped(player).map(e -> e.glowId()).orElse(null);

        List<GuiItem> items = new ArrayList<>(defs.size());
        for (GlowDefinition def : defs) {
            boolean equipped = equippedId != null && equippedId.equalsIgnoreCase(def.id());

            ItemStack item = createIconItem(def, equipped);
            items.add(new GuiItem(item, e -> {
                e.setCancelled(true);
                Player clicker = (Player) e.getWhoClicked();

                boolean isEquippedNow = glowService.equipped(clicker)
                        .map(eq -> eq.glowId().equalsIgnoreCase(def.id()))
                        .orElse(false);

                if (isEquippedNow) {
                    glowService.unequip(clicker);
                    clicker.setGlowing(false);
                    glowRenderService.clearTargetForAllViewers(clicker);
                } else {
                    boolean ok = glowService.equip(clicker, def.id());
                    if (ok) {
                        clicker.setGlowing(true);
                        // Rendering will be corrected on next tick; this keeps packets minimal.
                    }
                }

                open(clicker);
            }));
        }

        return items;
    }

    private ItemStack createIconItem(GlowDefinition def, boolean equipped) {
        ItemStack item = createIconStack(def);
        ItemMeta meta = item.getItemMeta();

        String nameRaw = def.displayName().orElseGet(() -> prettyName(def.id()));
        Component name = parseMini(nameRaw)
                .color(equipped ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, true);

        meta.displayName(noItalics(name));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();
        lore.add(noItalics(Component.text(
                equipped ? "Equipped" : "Not equipped",
                equipped ? NamedTextColor.GREEN : NamedTextColor.RED
        )));

        lore.add(Component.empty());

        lore.add(noItalics(Component.text("Colors:", NamedTextColor.YELLOW)));
        lore.addAll(formatColorsLore(def.colors()));

        lore.add(Component.empty());
        lore.add(noItalics(Component.text("Click to " + (equipped ? "unequip" : "equip"), NamedTextColor.AQUA)));

        meta.lore(lore);

        if (equipped) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createIconStack(GlowDefinition def) {
        String raw = def.icon().map(GlowDefinition.Icon::material).orElse(null);
        if (raw != null) {
            String trimmed = raw.trim();
            if (trimmed.regionMatches(true, 0, "player_head:", 0, "player_head:".length())) {
                String base64 = trimmed.substring("player_head:".length()).trim();
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);

                if (!base64.isBlank() && head.getItemMeta() instanceof SkullMeta skullMeta) {
                    try {
                        com.destroystokyo.paper.profile.PlayerProfile profile =
                                Bukkit.createProfile(UUID.randomUUID());
                        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64));

                        skullMeta.setPlayerProfile(profile);
                        head.setItemMeta(skullMeta);
                    } catch (Exception ignored) {
                        // leave as plain PLAYER_HEAD
                    }
                }

                return head;
            }
        }

        Material mat = def.icon()
                .map(i -> safeMaterial(i.material()))
                .orElse(Material.FLOWER_BANNER_PATTERN);

        return new ItemStack(mat);
    }

    private List<Component> formatColorsLore(List<String> colors) {
        List<String> pretty = colors.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(GlowMenu::prettyColorName)
                .toList();

        if (pretty.isEmpty()) {
            return List.of(noItalics(Component.text("None", NamedTextColor.DARK_GRAY)));
        }

        List<Component> out = new ArrayList<>();
        for (int i = 0; i < pretty.size(); i += COLORS_PER_LORE_LINE) {
            List<String> chunk = pretty.subList(i, Math.min(i + COLORS_PER_LORE_LINE, pretty.size()));
            out.add(noItalics(Component.text(String.join(", ", chunk), NamedTextColor.GRAY)));
        }
        return out;
    }

    private static String prettyColorName(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty()) return "";

        String[] parts = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder(cleaned.length());
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.toString();
    }

    private Component parseMini(String raw) {
        if (raw == null || raw.isBlank()) return Component.text("Glow");
        try {
            return miniMessage.deserialize(raw).decoration(TextDecoration.ITALIC, false);
        } catch (Exception ignored) {
            return Component.text(raw).decoration(TextDecoration.ITALIC, false);
        }
    }

    private static Component noItalics(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalics(Component.text(name, NamedTextColor.WHITE)));
        item.setItemMeta(meta);
        return item;
    }

    private static Material safeMaterial(String name) {
        if (name == null || name.isBlank()) return Material.FLOWER_BANNER_PATTERN;
        Material m = Material.matchMaterial(name);
        return m != null ? m : Material.FLOWER_BANNER_PATTERN;
    }

    private static String prettyName(String raw) {
        if (raw == null || raw.isBlank()) return "Glow";
        String cleaned = raw.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) return "Glow";
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    private static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }
}