package org.cubeville.cvpaintball.lasertag;

import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class LaserTagState {
    Integer team;
    Long lastRecharge = null;
    Long lastHit = null;
    int timesFired, timesHit, points = 0;
    int armorFlashID = -1;
    boolean flashingFirstColor = true;
    boolean isInvulnerable = false;
    ItemStack laserGun;

    public LaserTagState(int team) {
        this.team = team;
    }
}
