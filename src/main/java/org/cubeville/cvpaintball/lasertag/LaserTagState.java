package org.cubeville.cvpaintball.lasertag;

import org.bukkit.Color;

public class LaserTagState {
    Integer team;
    Long lastRecharge = null;
    Long lastHit = null;
    int timesFired, timesHit, points = 0;
    int armorFlashID = -1;
    boolean flashingFirstColor = true;
    boolean isInvulnerable = false;

    public LaserTagState(int team) {
        this.team = team;
    }
}
