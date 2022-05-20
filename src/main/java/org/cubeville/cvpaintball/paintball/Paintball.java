package org.cubeville.cvpaintball.paintball;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scoreboard.*;
import org.cubeville.cvgames.CVGames;
import org.cubeville.cvgames.models.Game;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.models.GameRegion;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvpaintball.CVPaintball;

import java.util.*;

public class Paintball extends Game {

    int rechargeZoneChecker;
    private final HashMap<Player, PaintballState> state = new HashMap<>();
    private List<HashMap<String, Object>> teams;
    private String[] healthColorCodes = {
            "§7§o", "§c", "§6", "§e", "§a"
    };


    public Paintball(String id) {
        super(id);
        addGameVariable("spectate-lobby", new GameVariableLocation());
        addGameVariable("ammo", new GameVariableInt(), 16);
        addGameVariable("recharge-zones", new GameVariableList<>(GameVariableRegion.class));
        addGameVariable("recharge-cooldown", new GameVariableInt(), 15);
        addGameVariable("fire-cooldown", new GameVariableDouble(), 0.5);
        addGameVariable("teams", new GameVariableList<>(PaintballTeam.class));
    }

    @Override
    public void onGameStart(List<Player> players) {
        teams = (List<HashMap<String, Object>>) getVariable("teams");
        List<Float> percentages = new ArrayList<>();
        List<String> teamKeys = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            teamKeys.add(Integer.toString(i));
            percentages.add(1.0F / ((float) teams.size()));
        }


        Map<String, List<Player>> teamsMap = GameUtils.divideTeams(players, teamKeys, percentages);

        for (int i = 0; i < teams.size(); i++) {
            HashMap<String, Object> team = teams.get(i);
            List<Player> teamPlayers = teamsMap.get(Integer.toString(i));

            if (teamPlayers == null) { continue; }

            String teamName = (String) team.get("name");
            ChatColor chatColor = (ChatColor) team.get("chat-color");
            List<Location> tps = (List<Location>) team.get("tps");

            int j = 0;
            for (Player player : teamPlayers) {
                state.put(player, new PaintballState(i));

                player.getInventory().clear();
                resetPlayerSnowballs(player);
                setPlayerArmor(player);

                Location tpLoc = tps.get(j);
                if (!tpLoc.getChunk().isLoaded()) {
                    tpLoc.getChunk().load();
                }
                player.teleport(tpLoc);
                if (teams.size() > 1) {
                    player.sendMessage(chatColor + "You are on §l" + teamName + chatColor + "!");
                } else {
                    player.sendMessage(chatColor + "It's a free for all!");
                }
                j++;
            }
        }

        List<GameRegion> rechargeZones = (List<GameRegion>) getVariable("recharge-zones");
        int cooldown = (int) getVariable("recharge-cooldown");
        rechargeZoneChecker = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVPaintball.getInstance(), () -> {
            for (GameRegion rechargeZone : rechargeZones) {
                for (Player player : state.keySet()) {
                    if (rechargeZone.containsPlayer(player)) {
                        Long lastRecharge = state.get(player).lastRecharge;
                        if (lastRecharge == null || System.currentTimeMillis() - lastRecharge > (cooldown * 1000L)) {
                            state.get(player).lastRecharge = System.currentTimeMillis();
                            player.sendMessage("§b§lAmmo Recharged! §f§o(Cooldown: " + cooldown + " seconds)");
                            resetPlayerSnowballs(player);
                        }
                    }
                }
            }
        }, 0L, 2L);

        updateScoreboard();
    }

    private Set<Integer> remainingTeams() {
        Set<Integer> stillInGame = new HashSet<>();
        for (PaintballState ps : state.values()) {
            if (ps.health == 0 || stillInGame.contains(ps.team)) { continue; }
            stillInGame.add(ps.team);
        }
        return stillInGame;
    }

    private Set<Player> remainingPlayers() {
        Set<Player> stillInGame = new HashSet<>();
        for (Player player : state.keySet()) {
            PaintballState ps = state.get(player);
            if (ps.health == 0) { continue; }
            stillInGame.add(player);
        }
        return stillInGame;
    }

    private void resetPlayerSnowballs(Player player) {
        HashMap<String, Object> team = teams.get(state.get(player).team);
        PlayerInventory inv = player.getInventory();

        // clear out all other snowballs
        ItemStack[] invContents = inv.getContents();
        for (int i = 0; i < invContents.length; i++) {
            if (invContents[i] != null && invContents[i].getType().equals(Material.SNOWBALL)) {
                inv.setItem(i, null);
            }
        }

        inv.addItem(GameUtils.customItem(
                Material.SNOWBALL,
                (String) team.get("snowball-name"),
                (int) getVariable("ammo")
        ));
    }

    private void setPlayerArmor(Player player) {
        List<Material> armorMats = List.of(Material.LEATHER_CHESTPLATE, Material.LEATHER_BOOTS, Material.LEATHER_LEGGINGS, Material.LEATHER_HELMET);
        HashMap<String, Object> team = teams.get(state.get(player).team);

        Color healthyColor = (Color) team.get("armor-color");
        Color damagedColor = (Color) team.get("armor-color-damaged");

        int health = state.get(player).health;
        PlayerInventory inv = player.getInventory();

        // clear out all armor
        ItemStack[] invContents = inv.getContents();
        for (int i = 0; i < invContents.length; i++) {
            if (invContents[i] != null && armorMats.contains(invContents[i].getType())) {
                inv.setItem(i, null);
            }
        }

        ItemStack helmet = GameUtils.createColoredLeatherArmor(Material.LEATHER_HELMET, health >= 4 ? healthyColor : damagedColor);
        ItemStack chest = GameUtils.createColoredLeatherArmor(Material.LEATHER_CHESTPLATE, health >= 3 ? healthyColor : damagedColor);
        ItemStack leggings = GameUtils.createColoredLeatherArmor(Material.LEATHER_LEGGINGS, health >= 2 ? healthyColor : damagedColor);
        ItemStack boots = GameUtils.createColoredLeatherArmor(Material.LEATHER_BOOTS, health >= 1 ? healthyColor : damagedColor);

        inv.setItem(5, helmet);
        inv.setItem(6, chest);
        inv.setItem(7, leggings);
        inv.setItem(8, boots);

        inv.setHelmet(helmet);
        inv.setChestplate(chest);
        inv.setLeggings(leggings);
        inv.setBoots(boots);
    }



    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {

        if (event.getEntityType().equals(EntityType.SNOWBALL) && event.getHitEntity() instanceof Player && event.getEntity().getShooter() instanceof Player) {
            Snowball s = (Snowball) event.getEntity();
            Player hit = (Player) event.getHitEntity();
            Player attacker = (Player) s.getShooter();

            PaintballState hitState = state.get(hit);
            PaintballState attackerState = state.get(attacker);

            // return if either player is not in the game
            if (hitState == null || attackerState == null) { return; }

            // if the player isn't shooting themselves or their teammate
            if (hit.equals(attacker) || (hitState.team == attackerState.team && teams.size() > 1)) { return; }

            hitState.health -= 1;
            attackerState.timesHit += 1;

            attacker.sendMessage("§aYou have hit " + hit.getName() + "!");
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 0.7F);
            hit.sendMessage("§cYou have been hit by " + attacker.getName() + "!");

            updateScoreboard();
            setPlayerArmor(hit);

            if (hitState.health == 0) {
                hit.getInventory().clear();
                if (!testGameEnd()) {
                    hit.sendMessage("§4§lYou have been eliminated!");
                    hit.teleport((Location) getVariable("spectate-lobby"));
                }
            }
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!event.getEntityType().equals(EntityType.SNOWBALL) || !(event.getEntity().getShooter() instanceof Player)) return;
        PaintballState pbs = state.get((Player) event.getEntity().getShooter());
        if (pbs == null) return;
        Double cooldown = (Double) getVariable("fire-cooldown");
        System.out.println(cooldown);
        System.out.println(pbs.lastFire != null ? (System.currentTimeMillis() - pbs.lastFire) : "null");

        if (pbs.lastFire != null && (System.currentTimeMillis() - pbs.lastFire) <= (cooldown * 1000L)) {
            // don't fire too fast
            event.setCancelled(true);
            return;
        }
        pbs.lastFire = System.currentTimeMillis();
        pbs.timesFired += 1;
    }

    private boolean testGameEnd() {
        if (teams.size() == 1) {
            if (remainingPlayers().size() <= 1) {
                finishGame(new ArrayList<>(state.keySet()));
                return true;
            }
        } else {
            if (remainingTeams().size() <= 1) {
                finishGame(new ArrayList<>(state.keySet()));
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPlayerLogout(Player p) {
        state.remove(p);
        testGameEnd();
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    @Override
    public void onGameFinish(List<Player> players) {
        Bukkit.getScheduler().cancelTask(rechargeZoneChecker);
        rechargeZoneChecker = 0;

        int remainingTeam = -1;
        Player remainingPlayer = null;

        if (teams.size() > 1) {
            for (int rt : remainingTeams()) { remainingTeam = rt; }
            if (remainingTeam < 0) { return; }
        } else {
            // ffa game
            remainingTeam = 0;
            for (Player player : remainingPlayers()) { remainingPlayer = player; }
            if (remainingPlayer == null) { return; }
        }
        ChatColor chatColor = (ChatColor) teams.get(remainingTeam).get("chat-color");
        if (teams.size() == 1) {
            for (Player player : players) {
                player.sendMessage(chatColor + "§l" + remainingPlayer.getDisplayName() + chatColor + "§l has won the game!");
                sendStatistics(player);
                player.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
            }
        } else {
            String teamName = (String) teams.get(remainingTeam).get("name");
            for (Player player : players) {
                player.sendMessage(chatColor + "§l" + teamName + chatColor + "§l has won the game!");
                sendStatistics(player);
                player.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
            }
        }
        state.clear();
    }

    private void sendStatistics(Player player) {
        PaintballState ps = state.get(player);
        player.sendMessage("§7Shots fired: §f" + ps.timesFired);
        player.sendMessage("§7Shots hit: §f" + ps.timesHit);
        if (ps.timesFired == 0 || ps.timesHit == 0) {
            player.sendMessage("§7Accuracy: §f0.00%");
        } else {
            player.sendMessage("§7Accuracy: §f" + String.format("%.2f", ((float) ps.timesHit / (float) ps.timesFired) * 100F) + "%");
        }
    }

    private void updateScoreboard() {
        Scoreboard scoreboard;
        if (teams.size() == 1) {
            ArrayList<String> scoreboardLines = new ArrayList<>();
            state.keySet().stream().sorted(Comparator.comparingInt(o -> -1 * state.get(o).health)).forEach( p -> {
                int health = state.get(p).health;
                scoreboardLines.add(healthColorCodes[health] + p.getDisplayName() + "§f: " + health + " HP");
            });
            scoreboard = GameUtils.createScoreboard(arena, "§b§lFFA Paintball", scoreboardLines);
        } else {
            HashMap<Integer, Integer> countPerTeam = new HashMap<>();
            for (Player p : remainingPlayers()) {
                int team = state.get(p).team;
                if (!countPerTeam.containsKey(team)) {
                    countPerTeam.put(team, 1);
                } else {
                    countPerTeam.put(team, countPerTeam.get(team) + 1);
                }
            }

            List<String> scoreboardLines = new ArrayList<>();
            for (int i = 0; i < teams.size(); i++) {
                String line = teams.get(i).get("name") + "§f: ";
                if (!countPerTeam.containsKey(i)) {
                    line += "§c\uD83D\uDDF4";
                } else {
                    int remaining = countPerTeam.get(i);
                    line += remaining;
                    if (remaining == 1) {
                        line += " player left";
                    } else {
                        line += " players left";
                    }

                }
                scoreboardLines.add(line);
            }
            scoreboard = GameUtils.createScoreboard(arena, "§b§lTeam Paintball", scoreboardLines);
        }
        this.state.keySet().forEach(p -> p.setScoreboard(scoreboard));
    }
}
