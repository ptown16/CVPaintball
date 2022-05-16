package org.cubeville.cvpaintball.paintball;

class PaintballState {

    int health = 4;
    int team;
    Long lastRecharge = null;
    Long lastFire = null;
    int timesFired = 0;
    int timesHit = 0;

    public PaintballState(int team) {
        this.team = team;
    }
}
