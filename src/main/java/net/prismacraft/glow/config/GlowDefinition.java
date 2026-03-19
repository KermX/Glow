package net.prismacraft.glow.config;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GlowDefinition {

    private final String id;
    private final List<String> colors;
    private final int cycleTicks;
    private final String permission;

    private final int requiresUnlockedGlows;
    private final List<String> requiresUnlockedFrom;

    private final Icon icon;
    private final String displayName;

    public GlowDefinition(
            String id,
            List<String> colors,
            int cycleTicks,
            String permission,
            int requiresUnlockedGlows,
            List<String> requiresUnlockedFrom,
            Icon icon,
            String displayName
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.colors = List.copyOf(Objects.requireNonNull(colors, "colors"));
        this.cycleTicks = cycleTicks;
        this.permission = permission;
        this.requiresUnlockedGlows = Math.max(0, requiresUnlockedGlows);
        this.requiresUnlockedFrom = requiresUnlockedFrom == null ? List.of() : List.copyOf(requiresUnlockedFrom);
        this.icon = icon;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public List<String> colors() {
        return colors;
    }

    public int cycleTicks() {
        return cycleTicks;
    }

    public Optional<String> permission() {
        return Optional.ofNullable(permission).filter(s -> !s.isBlank());
    }

    public int requiresUnlockedGlows() {
        return requiresUnlockedGlows;
    }

    public List<String> requiresUnlockedFrom() {
        return requiresUnlockedFrom;
    }

    public Optional<Icon> icon() {
        return Optional.ofNullable(icon);
    }

    public Optional<String> displayName() {
        return Optional.ofNullable(displayName).filter(s -> !s.isBlank());
    }

    public static final class Icon {
        private final String material;
        private final String name; // optional

        public Icon(String material, String name) {
            this.material = Objects.requireNonNull(material, "material");
            this.name = name;
        }

        public String material() {
            return material;
        }

        public Optional<String> name() {
            return Optional.ofNullable(name).filter(s -> !s.isBlank());
        }
    }
}