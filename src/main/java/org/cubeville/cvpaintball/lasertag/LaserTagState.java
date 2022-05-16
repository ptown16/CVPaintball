package org.cubeville.cvpaintball.lasertag;

public class LaserTagState {
    int health = 4;
    int team;
    Long lastRecharge = null;
    Long lastFire = null;
    int timesFired = 0;
    int timesHit = 0;

    public LaserTagState(int team) {
        this.team = team;
    }
}
