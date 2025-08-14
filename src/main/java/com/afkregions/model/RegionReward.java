
package com.afkregions.model;

import java.util.concurrent.ThreadLocalRandom;

public class RegionReward {
    public final boolean always;   // si true, elegible cada segundo
    public final int atSeconds;    // si >=0, se dispara exactamente al segundo
    public final double chance;    // 0..1
    public final String command;   // comando con {player}

    public RegionReward(boolean always, int atSeconds, double chance, String command) {
        this.always = always;
        this.atSeconds = atSeconds;
        this.chance = chance;
        this.command = command;
    }

    public boolean shouldTrigger(int elapsed, int max) {
        if (always) return true;
        if (atSeconds >= 0) return elapsed == atSeconds;
        return elapsed == max; // por seguridad
    }

    public boolean roll() {
        return chance >= 1.0 || ThreadLocalRandom.current().nextDouble() < chance;
    }
}
