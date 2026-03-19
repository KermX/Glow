package net.prismacraft.glow.model;

import java.util.Objects;

public class EquippedGlow {

    private final String glowId;
    private long ticksLived;

    public EquippedGlow(String glowId) {
        this.glowId = Objects.requireNonNull(glowId, "glowId");
    }

    public String glowId() {
        return glowId;
    }

    public long ticksLived() {
        return ticksLived;
    }

    public void tick() {
        ticksLived++;
    }
}
