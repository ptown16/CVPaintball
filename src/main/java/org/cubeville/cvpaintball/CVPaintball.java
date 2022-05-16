package org.cubeville.cvpaintball;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubeville.cvgames.CVGames;
import org.cubeville.cvpaintball.lasertag.LaserTag;
import org.cubeville.cvpaintball.paintball.Paintball;

public final class CVPaintball extends JavaPlugin {

    @Override
    public void onEnable() {
        CVGames.gameManager().registerGame("paintball", Paintball.class);
        CVGames.gameManager().registerGame("lasertag", LaserTag.class);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
