
package net.mineaqua.afkregions.model;

import java.util.concurrent.ThreadLocalRandom;

public class RegionReward {
    private final boolean always;
    private final int atSeconds;
    private final double chance;
    private final String command;

    public RegionReward(boolean always, int atSeconds, double chance, String command) {
        this.always = always;
        this.atSeconds = atSeconds;
        this.chance = chance;
        this.command = command;
    }

    public boolean shouldTrigger(int elapsed, int max) {
        if (always) {
            return true;
        }

        if (atSeconds >= 0) {
            return elapsed == atSeconds;
        }

        return elapsed == max;
    }

    public boolean roll() {
        return chance >= 1.0 || ThreadLocalRandom.current().nextDouble() < chance;
    }

    public boolean always() {
        return always;
    }

    public int atSeconds() {
        return atSeconds;
    }

    public double chance() {
        return chance;
    }

    public String command() {
        return command;
    }
}
