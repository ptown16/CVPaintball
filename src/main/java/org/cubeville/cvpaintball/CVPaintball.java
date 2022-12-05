package org.cubeville.cvpaintball;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubeville.cvgames.CVGames;
import org.cubeville.cvpaintball.lasertag.LaserTag;
import org.cubeville.cvpaintball.paintball.Paintball;
import org.cubeville.effects.Effects;

public final class CVPaintball extends JavaPlugin {

    private static CVPaintball instance;
    private static Effects effects;

    @Override
    public void onEnable() {
        instance = this;
        CVGames.gameManager().registerGame("paintball", Paintball::new);
        CVGames.gameManager().registerGame("lasertag", LaserTag::new);
        PluginManager pm = Bukkit.getPluginManager();
        effects = (Effects) pm.getPlugin("ArmamentsEffects");    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static CVPaintball getInstance() { return instance; }

    public static Effects getFXPlugin() {
        return effects;
    }
}
