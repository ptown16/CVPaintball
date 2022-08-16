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
import org.cubeville.cvgames.models.TeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.models.GameRegion;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvloadouts.CVLoadouts;
import org.cubeville.cvpaintball.CVPaintball;
import org.cubeville.effects.pluginhook.PluginHookEventReceiver;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LaserTag extends TeamSelectorGame implements PluginHookEventReceiver {

    private int rechargeZoneScheduler, scoreboardSecondUpdater;
    private String error;
    private final ArrayList<Integer[]> teamScores = new ArrayList<>();
    private long startTime = 0;
    private long currentTime;

    private List<HashMap<String, Object>> teams;
    Integer gameCollisionHook;

    public LaserTag(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("recharge-zones", new GameVariableList<>(GameVariableRegion.class));
        addGameVariable("recharge-cooldown", new GameVariableInt(), 15);
        addGameVariableTeamsList(
            new HashMap<>(){{
                put("loadout-team", new GameVariableString());
                put("tps", new GameVariableList<>(GameVariableLocation.class));
            }}
        );
        addGameVariable("duration", new GameVariableInt(), 5);
        addGameVariable("max-score", new GameVariableInt(), 20);
        addGameVariable("invuln-duration", new GameVariableInt(), 2);
        addGameVariable("loadout-lasertag", new GameVariableString());
        addGameVariable("invuln1-loadout-team", new GameVariableString());
        addGameVariable("invuln2-loadout-team", new GameVariableString());
        addGameVariable("invuln-shooting", new GameVariableFlag(), false);
    }

    @Nullable
    protected LaserTagState getState(Player p) {
        if (state.get(p) == null || !(state.get(p) instanceof LaserTagState)) return null;
        return (LaserTagState) state.get(p);
    }

    @Override
    public void onGameStart(List<Set<Player>> teamPlayersList) {
        // Hook into FX
        GameRegion gameRegion = (GameRegion) getVariable("region");
        gameCollisionHook = CVPaintball.getFXPlugin().getPluginHookManager().hook(gameRegion.getMin().getWorld(), gameRegion.getMin().toVector(), gameRegion.getMax().toVector(), this);
        teams = (List<HashMap<String, Object>>) getVariable("teams");


        for (int i = 0; i < teams.size(); i++) {
            HashMap<String, Object> team = teams.get(i);
            Set<Player> teamPlayers = teamPlayersList.get(i);

            if (teamPlayers == null) { continue; }

            String teamName = (String) team.get("name");
            ChatColor chatColor = (ChatColor) team.get("chat-color");
            List<Location> tps = (List<Location>) team.get("tps");

            teamScores.add(new Integer[]{ i, 0 });

            int j = 0;
            for (Player player : teamPlayers) {
                state.put(player, new LaserTagState(i));

                CVLoadouts.getInstance().applyLoadoutToPlayer(player,
                        (String) getVariable("loadout-lasertag"),
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
                player.sendMessage(chatColor + "First to " + getVariable("max-score") + " points wins!");
                j++;
            }
        }

        List<GameRegion> rechargeZones = (List<GameRegion>) getVariable("recharge-zones");
        int cooldown = (int) getVariable("recharge-cooldown");
        rechargeZoneScheduler = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVPaintball.getInstance(), () -> {
            for (Player player : state.keySet()) {

                LaserTagState playerState = getState(player);
                // check recharge
                for (GameRegion rechargeZone : rechargeZones) {
                    if (rechargeZone.containsPlayer(player) && !playerState.isInvulnerable) {
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
                updateDefaultScoreboard((int) currentTime, teamScores, "points");
                int seconds = ((int) currentTime / 1000);
                if (seconds == 30) {
                    sendTimeTitle("§b30 seconds remain");
                } else if (seconds == 60) {
                    sendTimeTitle("§b1 minute remains");
                } else if (seconds == 180) {
                    sendTimeTitle("§b3 minutes remain");
                }
            } else {
                finishGame();
            }
        }, 0L, 20L);

        updateDefaultScoreboard((int) currentTime, teamScores, "points");
    }

    private void sendTimeTitle(String title) {
        state.keySet().forEach(player -> player.sendTitle("", title, 2, 56, 2));
    }

    private void reloadPlayerGun(Player player) {
        HashMap<String, Object> team = teams.get(getState(player).team);
        PlayerInventory inv = player.getInventory();
        // Get the first item
        String loadoutName = (String) getVariable("loadout-lasertag");
        String teamName = ((String) team.get("loadout-team")).split(";")[0];
        ItemStack laserGun = CVLoadouts.getInstance().getLoadoutItem(loadoutName, teamName, 0);

        if (laserGun == null) { finishGameWithError("Could not find a laser gun in slot 0 of loadout " + loadoutName + " with team " + teamName); return; }

        GameUtils.clearItemsFromInventory(inv, List.of(laserGun));
        inv.addItem(laserGun);
    }

    @Override
    public void onPlayerLeave(Player p) {
        if (isLastOnTeam(p)) {
            teamScores.set(getState(p).team, new Integer[]{getState(p).team, -1});
        }
        Bukkit.getScheduler().cancelTask(getState(p).armorFlashID);
        getState(p).armorFlashID = -1;
        state.remove(p);
        if (state.size() <= 1 || teamScores.stream().filter(score -> score[1] != -1).count() <= 1) { finishGame(); }
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    private boolean isLastOnTeam(Player p) {
        LaserTagState lts = getState(p);
        if (lts == null) return false;
        for (Player player : state.keySet()) {
            if (!player.equals(p) && getState(p).team == lts.team) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onGameFinish() {
        for (Player player : state.keySet()) {
            if (getState(player).armorFlashID != -1) {
                Bukkit.getScheduler().cancelTask(getState(player).armorFlashID);
                getState(player).armorFlashID = -1;
            }
        }

        Bukkit.getScheduler().cancelTask(rechargeZoneScheduler);
        rechargeZoneScheduler = -1;
        Bukkit.getScheduler().cancelTask(scoreboardSecondUpdater);
        scoreboardSecondUpdater = -1;

        Bukkit.getScheduler().runTaskLater(CVPaintball.getInstance(), () -> {
            if (gameCollisionHook != null) {
                CVPaintball.getFXPlugin().getPluginHookManager().unhook(gameCollisionHook);
                gameCollisionHook = null;
            }
        }, 100L);

        if (error != null) {
            sendMessageToArena("§c§lERROR: §c" + error);
        } else if (teams.size() > 1) {
            finishTeamGame();
        } else {
            finishFFAGame();
        }
        error = null;
        teamScores.clear();
    }

    private void finishFFAGame() {
        List<Player> sortedPlayers  = state.keySet().stream().sorted(Comparator.comparingInt(o -> -1 * getState(o).points)).collect(Collectors.toList());

        ChatColor chatColor = (ChatColor) teams.get(0).get("chat-color");

        if (sortedPlayers.size() > 1 && getState(sortedPlayers.get(0)).points == getState(sortedPlayers.get(1)).points) {
            state.keySet().forEach(p -> {
                StringBuilder output = new StringBuilder();
                output.append(chatColor);
                for (int i = 0; i < sortedPlayers.size(); i++) {
                    if (i == (sortedPlayers.size() - 1) || getState(sortedPlayers.get(i)).points != getState(sortedPlayers.get(i + 1)).points) {
                        output.append(" and ").append(sortedPlayers.get(i).getDisplayName());
                        break;
                    } else if (i == 0) {
                        output.append(sortedPlayers.get(i).getDisplayName());
                    } else {
                        output.append(", ").append(sortedPlayers.get(i).getDisplayName());
                    }
                }
                output.append(" win the game!");
                sendMessageToArena(output.toString());
            });
        } else {
            sendMessageToArena(chatColor + sortedPlayers.get(0).getDisplayName() + " has won the game!");
        }

        sendMessageToArena("§b§l--- FINAL RESULTS ---");
        sortedPlayers.forEach(player -> {
            sendMessageToArena(chatColor + player.getDisplayName() + "§f: " + getState(player).points + " points");
        });

        state.keySet().forEach(this::playerPostGame);
    }


    private void finishTeamGame() {
        List<Integer[]> sortedTeams = teamScores.stream().sorted(Comparator.comparingInt(o -> -1 * o[1])).collect(Collectors.toList());
        if (Objects.equals(sortedTeams.get(0)[1], sortedTeams.get(1)[1])) {
            sendMessageToArena("§f§lTie Game!");
        } else {
            String teamName = (String) teams.get(sortedTeams.get(0)[0]).get("name");
            ChatColor chatColor = (ChatColor) teams.get(sortedTeams.get(0)[0]).get("chat-color");
            sendMessageToArena(chatColor + "§l" + teamName + chatColor + "§l has won the game!");
        }
        sendMessageToArena("§b§l--- FINAL RESULTS ---");
        sortedTeams.forEach(pair -> {
            String teamName = (String) teams.get(pair[0]).get("name");
            ChatColor chatColor = (ChatColor) teams.get(pair[0]).get("chat-color");
            sendMessageToArena(chatColor + teamName + "§f: " + pair[1] + " points");
        });

        state.keySet().forEach(this::playerPostGame);
    }

    private void playerPostGame(Player p) {
        sendStatistics(p);
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
    }

    private void sendStatistics(Player player) {
        LaserTagState ps = getState(player);
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

    @Override
    public void onBlockCollisionEvent(Player player, Block block) {
        // do something eventually :D
        LaserTagState pState = getState(player);
        if (pState != null) {
            pState.timesFired += 1;
        }
        return;
    }

    private void finishGameWithError(String error) {
        this.error = error;
        finishGame();
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
        LaserTagState attackerState = getState(attacker);
        if (attackerState == null) { return; }
        attackerState.timesFired += 1;
        if (entity instanceof Player) {
            Player hit = (Player) entity;

            LaserTagState hitState = getState(hit);

            // return if either player is not in the game
            if (hitState == null ) { return; }

            // if the player isn't shooting themselves or their teammate
            if (hit.equals(attacker) || (hitState.team == attackerState.team && teams.size() > 1)) { return; }

            if (hitState.isInvulnerable) { return; }

            attackerState.points += 1;
            hitState.timesHit += 1;
            hitState.lastHit = System.currentTimeMillis();
            hitState.isInvulnerable = true;
            hitState.laserGun = hit.getInventory().getItem(0);
            teamScores.set(attackerState.team, new Integer[]{ attackerState.team, teamScores.get(attackerState.team)[1] + 1 });

            int maxScore = (int) getVariable("max-score");
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 0.7F);
            hit.playSound(hit.getLocation(), Sound.ENTITY_VEX_HURT, 1.0F, 0.5F);
            hit.playSound(hit.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 1.0F, 1.5F);
            sendMessageToArena(teams.get(attackerState.team).get("chat-color") + attacker.getDisplayName() + "§e has hit " + teams.get(hitState.team).get("chat-color") + hit.getDisplayName());

            if (teams.size() > 1) {
                if (teamScores.get(attackerState.team)[1] >= maxScore) {
                    finishGame();
                    return;
                }
            } else {
                if (attackerState.points >= maxScore) {
                    finishGame();
                    return;
                }
            }

            long invunlDuration = ((int) getVariable("invuln-duration") * 1000L);
            String loadoutName = (String) getVariable("loadout-lasertag");
            List<String> teamLoadouts = Arrays.asList(((String) teams.get(hitState.team).get("loadout-team")).split(";"));

            hitState.armorFlashID = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVPaintball.getInstance(), () -> {
                LaserTagState playerState = getState(hit);
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

            updateDefaultScoreboard((int) currentTime, teamScores, "points");
        }
    }
}