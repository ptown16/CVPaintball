package org.cubeville.cvpaintball.lasertag;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scoreboard.*;
import org.cubeville.cvgames.CVGames;
import org.cubeville.cvgames.models.Arena;
import org.cubeville.cvgames.models.Game;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.models.GameRegion;
import org.cubeville.cvgames.vartypes.*;
import org.cubeville.cvpaintball.CVPaintball;
import org.cubeville.effects.pluginhook.PluginHookEventReceiver;

import java.util.*;
import java.util.stream.Collectors;

public class LaserTag extends Game implements PluginHookEventReceiver {

    private int rechargeZoneScheduler, scoreboardSecondUpdater;
    private final HashMap<Player, LaserTagState> state = new HashMap<>();
    private final ArrayList<Integer> teamScores = new ArrayList<>();
    private long startTime = 0;
    private long currentTime;

    private List<HashMap<String, Object>> teams;
    Integer gameCollisionHook;


    public LaserTag(String id) {
        super(id);
        addGameVariable("spectate-lobby", new GameVariableLocation());
        addGameVariable("ammo", new GameVariableInt(), 16);
        addGameVariable("recharge-zones", new GameVariableList<>(GameVariableRegion.class));
        addGameVariable("recharge-cooldown", new GameVariableInt(), 15);
        addGameVariable("teams", new GameVariableList<>(LaserTagTeam.class));
        addGameVariable("region", new GameVariableRegion());
        addGameVariable("duration", new GameVariableInt(), 5);
        addGameVariable("max-score", new GameVariableInt(), 20);
        addGameVariable("invin-duration", new GameVariableInt(), 2);
        addGameVariable("armor-color-invin-1", new GameVariableArmorColor(), "#FFFF00");
        addGameVariable("armor-color-invin-2", new GameVariableArmorColor(), "#000000");


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

            String teamName = (String) team.get("name");
            ChatColor chatColor = (ChatColor) team.get("chat-color");
            List<Location> tps = (List<Location>) team.get("tps");

            teamScores.add(0);

            int j = 0;
            for (Player player : teamPlayers) {
                state.put(player, new LaserTagState(i));

                player.getInventory().clear();
                resetPlayerGun(player);
                Color armorColor = (Color) team.get("armor-color");
                setPlayerArmor(player, armorColor, true);

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
                            resetPlayerGun(player);
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

    private void resetPlayerGun(Player player) {
        HashMap<String, Object> team = teams.get(state.get(player).team);
        PlayerInventory inv = player.getInventory();
        ItemStack laserGun = (ItemStack) team.get("laser-gun");

        // clear out the other ammo
        ItemStack[] invContents = inv.getContents();
        for (int i = 0; i < invContents.length; i++) {
            if (invContents[i] != null &&
                    invContents[i].getType().equals(laserGun.getType()) &&
                    Objects.requireNonNull(invContents[i].getItemMeta()).getDisplayName().equals(Objects.requireNonNull(laserGun.getItemMeta()).getDisplayName())
            ) {
                inv.setItem(i, null);
            }
        }

        laserGun.setAmount((int) getVariable("ammo"));
        inv.addItem(laserGun);
    }

    private void clearArmorFromInventory(PlayerInventory inv, int safeIndex) {
        List<Material> armorMats = List.of(Material.LEATHER_CHESTPLATE, Material.LEATHER_BOOTS, Material.LEATHER_LEGGINGS, Material.LEATHER_HELMET);

        // clear out all armor
        ItemStack[] invContents = inv.getContents();
        for (int i = 0; i < invContents.length; i++) {
            if (i == safeIndex) continue;
            if (invContents[i] != null && armorMats.contains(invContents[i].getType())) {
                inv.setItem(i, null);
            }
        }

    }
    private void setPlayerArmor(Player player, Color color, boolean setInventoryItem) {
        Color healthyColor = (Color) teams.get(state.get(player).team).get("armor-color");
        setPlayerArmor(player, color, setInventoryItem, healthyColor);
    }

    private void setPlayerArmor(Player player, Color color, boolean setInventoryItem, Color secondaryColor) {
        PlayerInventory inv = player.getInventory();
        clearArmorFromInventory(inv, setInventoryItem ? -1 : 8);

        ItemStack chest = GameUtils.createColoredLeatherArmor(Material.LEATHER_CHESTPLATE, color);

        if (setInventoryItem) {
            inv.setItem(8, chest);
        }
        inv.setChestplate(chest);
        inv.setHelmet(GameUtils.createColoredLeatherArmor(Material.LEATHER_HELMET, secondaryColor));
        inv.setLeggings(GameUtils.createColoredLeatherArmor(Material.LEATHER_LEGGINGS, secondaryColor));
        inv.setBoots(GameUtils.createColoredLeatherArmor(Material.LEATHER_BOOTS, secondaryColor));
    }

    @Override
    public void onPlayerLogout(Player p) {
        if (isLastOnTeam(p)) {
            teamScores.set(state.get(p).team, -1);
        }
        Bukkit.getScheduler().cancelTask(state.get(p).armorFlashID);
        state.get(p).armorFlashID = -1;
        state.remove(p);
        if (state.size() <= 1) { finishGame(new ArrayList<>(state.keySet())); }
        if (teamScores.stream().filter(score -> score != -1).count() <= 1) {
            finishGame(new ArrayList<>(state.keySet()));
        }
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
        Bukkit.getScheduler().cancelTask(rechargeZoneScheduler);
        rechargeZoneScheduler = -1;
        Bukkit.getScheduler().cancelTask(scoreboardSecondUpdater);
        scoreboardSecondUpdater = -1;

        for (Player player : players) {
            Bukkit.getScheduler().cancelTask(state.get(player).armorFlashID);
            state.get(player).armorFlashID = -1;
            clearArmorFromInventory(player.getInventory(), -1);
        }

        CVPaintball.getFXPlugin().getPluginHookManager().unhook(gameCollisionHook);
        gameCollisionHook = null;

        if (teams.size() > 1) {
            finishTeamGame(players);
        } else {
            finishFFAGame(players);
        }
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
        player.sendMessage("§7Shots fired: §f" + ps.timesFired);
        if (ps.timesFired == 0 || ps.points == 0) {
            player.sendMessage("§7Accuracy: §f0.00%");
        } else {
            player.sendMessage("§7Accuracy: §f" + String.format("%.2f", ((float) ps.points / (float) ps.timesFired) * 100F) + "%");
        }
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
            for (int i = 0; i < teams.size(); i++) {
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
            if (hit.equals(attacker) || (hitState.team == attackerState.team && teams.size() > 1)) { return; }

            if (hitState.isInvulnerable) { return; }

            attackerState.points += 1;
            hitState.timesHit += 1;
            hitState.lastHit = System.currentTimeMillis();
            hitState.isInvulnerable = true;
            teamScores.set(attackerState.team, teamScores.get(attackerState.team) + 1);

            if (teams.size() > 1) {
                if (teamScores.get(attackerState.team) >= (int) getVariable("max-score")) {
                    finishGame(new ArrayList<>(state.keySet()));
                }
            } else {
                if (attackerState.points >= (int) getVariable("max-score")) {
                    finishGame(new ArrayList<>(state.keySet()));
                }
            }

            long invinDuration = ((int) getVariable("invin-duration") * 1000L);
            Color hitColor1 = (Color) getVariable("armor-color-invin-1");
            Color hitColor2 = (Color) getVariable("armor-color-invin-2");

            hitState.armorFlashID = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVPaintball.getInstance(), () -> {
                LaserTagState playerState = state.get(hit);
                if (System.currentTimeMillis() - playerState.lastHit > invinDuration) {
                    playerState.isInvulnerable = false;
                    setPlayerArmor(hit, (Color) teams.get(playerState.team).get("armor-color"), true);
                    Bukkit.getScheduler().cancelTask(playerState.armorFlashID);
                    playerState.armorFlashID = -1;
                } else {
                    if (playerState.currentArmorColor.equals(hitColor1)) {
                        playerState.currentArmorColor = hitColor2;
                        setPlayerArmor(hit, hitColor2, false);
                    } else {
                        playerState.currentArmorColor = hitColor1;
                        setPlayerArmor(hit, hitColor1, false);
                    }
                }
            }, 0L, 5L);

            attacker.sendMessage("§aYou have hit " + hit.getName() + "!");
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 0.7F);
            hit.sendMessage("§cYou have been hit by " + attacker.getName() + "!");
            setPlayerArmor(hit, hitColor1, true);
            hitState.currentArmorColor = hitColor1;
            updateScoreboard();
        }
    }
}