package org.cubeville.cvpaintball;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubeville.cvgames.CVGames;
import org.cubeville.cvpaintball.lasertag.LaserTag;
import org.cubeville.cvpaintball.paintball.Paintball;
import org.cubeville.effects.Effects;

public final class CVPaintball extends JavaPlugin {

    private static Effects effects;

    @Override
    public void onEnable() {
        CVGames.gameManager().registerGame("paintball", Paintball.class);
        CVGames.gameManager().registerGame("lasertag", LaserTag.class);
        PluginManager pm = Bukkit.getPluginManager();
        effects = (Effects) pm.getPlugin("ArmamentsEffects");    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Effects getFXPlugin() {
        return effects;
    }
}
