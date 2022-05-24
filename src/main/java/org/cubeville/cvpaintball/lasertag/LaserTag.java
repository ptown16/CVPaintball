package org.cubeville.cvpaintball.lasertag;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scoreboard.*;
import org.cubeville.cvgames.models.Game;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.models.GameRegion;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvloadouts.CVLoadouts;
import org.cubeville.cvpaintball.CVPaintball;
import org.cubeville.effects.pluginhook.PluginHookEventReceiver;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LaserTag extends Game implements PluginHookEventReceiver {

    private int rechargeZoneScheduler, scoreboardSecondUpdater;
    private String error;
    private final HashMap<Player, LaserTagState> state = new HashMap<>();
    private final ArrayList<Integer> teamScores = new ArrayList<>();
    private long startTime = 0;
    private long currentTime;

    private List<HashMap<String, Object>> teams;
    Integer gameCollisionHook;

    public LaserTag(String id) {
        super(id);
        addGameVariable("recharge-zones", new GameVariableList<>(GameVariableRegion.class));
        addGameVariable("recharge-cooldown", new GameVariableInt(), 15);
        addGameVariable("teams", new GameVariableList<>(LaserTagTeam.class));
        addGameVariable("region", new GameVariableRegion());
        addGameVariable("duration", new GameVariableInt(), 5);
        addGameVariable("max-score", new GameVariableInt(), 20);
        addGameVariable("invuln-duration", new GameVariableInt(), 2);
        addGameVariable("loadout-name", new GameVariableString());
        addGameVariable("invuln1-loadout-team", new GameVariableString());
        addGameVariable("invuln2-loadout-team", new GameVariableString());
        addGameVariable("invuln-shooting", new GameVariableFlag(), false);


    }

    @Override
    public void onGameStart(List<Player> players) {
        // Hook into FX
        GameRegion gameRegion = (GameRegion) getVariable("region");
        gameCollisionHook = CVPaintball.getFXPlugin().getPluginHookManager().hook(gameRegion.getMin().getWorld(), gameRegion.getMin().toVector(), gameRegion.getMax().toVector(), this);

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

            teamScores.add(0);

            int j = 0;
            for (Player player : teamPlayers) {
                state.put(player, new LaserTagState(i));

                CVLoadouts.getInstance().applyLoadoutToPlayer(player,
                        (String) getVariable("loadout-name"),
                        Arrays.asList(((String) team.get("loadout-team")).split(";"))
                );

                Location tpLoc = tps.get(j % tps.size());
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
        rechargeZoneScheduler = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVPaintball.getInstance(), () -> {
            for (Player player : state.keySet()) {

                LaserTagState playerState = state.get(player);
                // check recharge
                for (GameRegion rechargeZone : rechargeZones) {
                    if (rechargeZone.containsPlayer(player)) {
                        Long lastRecharge = playerState.lastRecharge;
                        if (lastRecharge == null || System.currentTimeMillis() - lastRecharge > (cooldown * 1000L)) {
                            playerState.lastRecharge = System.currentTimeMillis();
                            player.sendMessage("§b§lAmmo Recharged! §f§o(Cooldown: " + cooldown + " seconds)");
                            reloadPlayerGun(player);
                        }
                    }
                }
            }
        }, 0L, 2L);

        startTime = System.currentTimeMillis();

        long duration = ((int) getVariable("duration")) * 60L * 1000L;
        scoreboardSecondUpdater = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVPaintball.getInstance(), () -> {
            currentTime = duration - (System.currentTimeMillis() - startTime);
            if (currentTime > 0) {
                updateScoreboard();
            } else {
                finishGame(new ArrayList<>(state.keySet()));
            }
        }, 0L, 20L);

        updateScoreboard();
    }

    private void reloadPlayerGun(Player player) {
        HashMap<String, Object> team = teams.get(state.get(player).team);
        PlayerInventory inv = player.getInventory();
        // Get the first item
        String loadoutName = (String) getVariable("loadout-name");
        String teamName = ((String) team.get("loadout-team")).split(";")[0];
        ItemStack laserGun = CVLoadouts.getInstance().getLoadoutItem(loadoutName, teamName, 0);

        if (laserGun == null) { finishGameWithError("Could not find a laser gun in slot 0 of loadout " + loadoutName + " with team " + teamName); return; }

        GameUtils.clearItemsFromInventory(inv, List.of(laserGun));
        inv.addItem(laserGun);
    }

    @Override
    public void onPlayerLogout(Player p) {
        if (isLastOnTeam(p)) {
            teamScores.set(state.get(p).team, -1);
        }
        Bukkit.getScheduler().cancelTask(state.get(p).armorFlashID);
        state.get(p).armorFlashID = -1;
        state.remove(p);
        if (state.size() <= 1 || teamScores.stream().filter(score -> score != -1).count() <= 1) { finishGame(new ArrayList<>(state.keySet())); }
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    private boolean isLastOnTeam(Player p) {
        LaserTagState lts = state.get(p);
        if (lts == null) return false;
        for (Player player : state.keySet()) {
            if (!player.equals(p) && state.get(p).team == lts.team) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onGameFinish(List<Player> players) {
        for (Player player : players) {
            if (state.containsKey(player) && state.get(player).armorFlashID != -1) {
                Bukkit.getScheduler().cancelTask(state.get(player).armorFlashID);
                state.get(player).armorFlashID = -1;
            }
        }

        Bukkit.getScheduler().cancelTask(rechargeZoneScheduler);
        rechargeZoneScheduler = -1;
        Bukkit.getScheduler().cancelTask(scoreboardSecondUpdater);
        scoreboardSecondUpdater = -1;

        if (gameCollisionHook != null) {
            CVPaintball.getFXPlugin().getPluginHookManager().unhook(gameCollisionHook);
            gameCollisionHook = null;
        }

        if (error != null) {
            GameUtils.messagePlayerList(players, "§c§lERROR: §c" + error);
        } else if (teams.size() > 1) {
            finishTeamGame(players);
        } else {
            finishFFAGame(players);
        }
        error = null;
        teamScores.clear();
        state.clear();
    }

    private void finishFFAGame(List<Player> players) {
        List<Player> topPlayers = new ArrayList<>();
        for (Player player : players) {
            if (topPlayers.size() == 0) {
                topPlayers.add(player);
                continue;
            }
            int maxScore = state.get(topPlayers.get(0)).points;
            if (maxScore < state.get(player).points) {
                topPlayers = List.of(player);
            } else if (maxScore == state.get(player).points) {
                topPlayers.add(player);
            }
        }

        ChatColor chatColor = (ChatColor) teams.get(0).get("chat-color");
        List<Player> finalTopPlayers = topPlayers;


        if (finalTopPlayers.size() != 1) {
            players.forEach(p -> {
                String output = "";
                output += chatColor;
                for (int i = 0; i < finalTopPlayers.size(); i++) {
                    if (i == (finalTopPlayers.size() - 1)) {
                        output += ", " + finalTopPlayers.get(i).getDisplayName();
                    } else if (i == 0) {
                        output += finalTopPlayers.get(i).getDisplayName();
                    } else {
                        output += "and " + finalTopPlayers.get(i).getDisplayName();
                    }
                }
                output += " win the game!";
                p.sendMessage(output);
                playerPostGame(p);
            });
            return;
        }

        players.forEach(p -> {
            p.sendMessage(chatColor + finalTopPlayers.get(0).getDisplayName() + " has won the game!");
            playerPostGame(p);
        });
    }


    private void finishTeamGame(List<Player> players) {
        List<Integer> topTeams = new ArrayList<>(List.of(0));
        for (int i = 1; i < teamScores.size(); i++) {
            int maxScore = teamScores.get(topTeams.get(0));
            if (maxScore < teamScores.get(i)) {
                topTeams = List.of(i);
            } else if (maxScore == teamScores.get(i)) {
                topTeams.add(i);
            }
        }

        if (topTeams.size() != 1) {
            players.forEach(p -> {
                p.sendMessage("§f§lTie Game!");
                playerPostGame(p);
            });
            return;
        }

        String teamName = (String) teams.get(topTeams.get(0)).get("name");
        ChatColor chatColor = (ChatColor) teams.get(topTeams.get(0)).get("chat-color");
        players.forEach(p -> {
            p.sendMessage(chatColor + "§l" + teamName + chatColor + "§l has won the game!");
            playerPostGame(p);
        });
    }

    private void playerPostGame(Player p) {
        sendStatistics(p);
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    private void sendStatistics(Player player) {
        LaserTagState ps = state.get(player);
        player.sendMessage("§7Points: §f" + ps.points);
        player.sendMessage("§7Times Hit: §f" + ps.timesHit);
        if (ps.points == 0) {
            player.sendMessage("§7K/D Ratio: §f0.00");
        } else if (ps.timesHit == 0) {
            player.sendMessage("§7K/D Ratio: §f" + ps.points + ".00");
        } else {
            player.sendMessage("§7K/D Ratio: §f" + String.format("%.2f", ((float) ps.points / (float) ps.timesHit)));
        }
//        if (ps.timesFired == 0 || ps.points == 0) {
//            player.sendMessage("§7Accuracy: §f0.00%");
//        } else {
//            player.sendMessage("§7Accuracy: §f" + String.format("%.2f", ((float) ps.points / (float) ps.timesFired) * 100F) + "%");
//        }
    }

    private void updateScoreboard() {
        Scoreboard scoreboard;
        ArrayList<String> scoreboardLines = new ArrayList<>();

        scoreboardLines.add("§bTime remaining: §f" +
                String.format("%d:%02d", (int) currentTime / 60000, (int) (currentTime / 1000) % 60)
        );
        scoreboardLines.add("   ");

        if (teams.size() == 1) {
            state.keySet().stream().sorted(Comparator.comparingInt(o -> -1 * state.get(o).points)).forEach( p -> {
                int points = state.get(p).points;
                scoreboardLines.add("§a" + p.getDisplayName() + "§f: " + points + " points");
            });
            scoreboard = GameUtils.createScoreboard(arena, "§b§lFFA Laser Tag", scoreboardLines);
        } else {
            for (int i = 0; i < teamScores.size(); i++) {
                String line = teams.get(i).get("name") + "§f: ";
                line += teamScores.get(i);
                line += " points";
                scoreboardLines.add(line);
            }
            scoreboard = GameUtils.createScoreboard(arena, "§b§lTeam Laser Tag", scoreboardLines);
        }
        this.state.keySet().forEach(p -> p.setScoreboard(scoreboard));
    }

    @Override
    public void onBlockCollisionEvent(Player player, Block block) {
        // do something eventually :D
        LaserTagState pState = state.get(player);
        if (pState != null) {
            pState.timesFired += 1;
        }
        return;
    }

    private void finishGameWithError(String error) {
        this.error = error;
        finishGame(new ArrayList<>(state.keySet()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // prevent anyone in the game from moving stuff around their inventory
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (state.containsKey(player)) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onEntityCollisionEvent(Player attacker, Entity entity) {
        LaserTagState attackerState = state.get(attacker);
        if (attackerState == null) { return; }
        attackerState.timesFired += 1;
        if (entity instanceof Player) {
            Player hit = (Player) entity;

            LaserTagState hitState = state.get(hit);

            // return if either player is not in the game
            if (hitState == null ) { return; }

            // if the player isn't shooting themselves or their teammate
            if (hit.equals(attacker) || (hitState.team.equals(attackerState.team) && teams.size() > 1)) { return; }

            if (hitState.isInvulnerable) { return; }

            attackerState.points += 1;
            hitState.timesHit += 1;
            hitState.lastHit = System.currentTimeMillis();
            hitState.isInvulnerable = true;
            hitState.laserGun = hit.getInventory().getItem(0);
            teamScores.set(attackerState.team, teamScores.get(attackerState.team) + 1);

            if (teams.size() > 1) {
                if (teamScores.get(attackerState.team) >= (int) getVariable("max-score")) {
                    finishGame(new ArrayList<>(state.keySet()));
                    return;
                }
            } else {
                if (attackerState.points >= (int) getVariable("max-score")) {
                    finishGame(new ArrayList<>(state.keySet()));
                    return;
                }
            }

            long invunlDuration = ((int) getVariable("invuln-duration") * 1000L);
            String loadoutName = (String) getVariable("loadout-name");
            List<String> teamLoadouts = Arrays.asList(((String) teams.get(hitState.team).get("loadout-team")).split(";"));

            hitState.armorFlashID = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVPaintball.getInstance(), () -> {
                LaserTagState playerState = state.get(hit);
                if (System.currentTimeMillis() - playerState.lastHit > invunlDuration) {
                    CVLoadouts.getInstance().applyLoadoutToPlayer(hit, loadoutName, teamLoadouts);
                    if (!((Boolean) getVariable("invuln-shooting"))) {
                        hit.getInventory().setItem(0, playerState.laserGun);
                    }
                    playerState.isInvulnerable = false;
                    Bukkit.getScheduler().cancelTask(playerState.armorFlashID);
                    playerState.armorFlashID = -1;
                    playerState.flashingFirstColor = true;
                } else {
                    if (playerState.flashingFirstColor) {
                        List<String> invuln1Loadouts = Arrays.asList(((String) getVariable("invuln1-loadout-team")).split(";"));
                        CVLoadouts.getInstance().applyLoadoutToPlayer(hit, loadoutName,
                                Stream.of(invuln1Loadouts, teamLoadouts).flatMap(Collection::stream).collect(Collectors.toList()));
                    } else {
                        List<String> invuln2Loadouts = Arrays.asList(((String) getVariable("invuln2-loadout-team")).split(";"));
                        CVLoadouts.getInstance().applyLoadoutToPlayer(hit, loadoutName,
                                Stream.of(invuln2Loadouts, teamLoadouts).flatMap(Collection::stream).collect(Collectors.toList()));
                    }
                    playerState.flashingFirstColor = !playerState.flashingFirstColor;
                }
            }, 0L, 5L);

            attacker.sendMessage("§aYou have hit " + hit.getName() + "!");
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 0.7F);
            hit.sendMessage("§cYou have been hit by " + attacker.getName() + "!");
            hit.playSound(hit.getLocation(), Sound.ENTITY_VEX_HURT, 1.0F, 0.5F);
            hit.playSound(hit.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 1.0F, 1.5F);
            updateScoreboard();
        }
    }
}