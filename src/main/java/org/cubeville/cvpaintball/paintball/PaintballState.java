package org.cubeville.cvpaintball.paintball;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.cubeville.cvgames.models.PlayerState;

class PaintballState extends PlayerState {

    int health;
    int team;
    Long lastRecharge = null;
    Long lastFire = null;
    Long lastHit = null;
    int timesFired = 0;
    int successfulShots = 0;
    int armorFlashID = -1;
    boolean flashingFirstColor = true;
    boolean isInvulnerable = false;
    ItemStack[] inventoryContents;

    public PaintballState(int team, int health) {
        this.team = team;
        this.health = health;
    }

    @Override
    public int getSortingValue() {
        return health;
    }
}
