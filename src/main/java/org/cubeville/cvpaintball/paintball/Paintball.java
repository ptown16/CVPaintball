package org.cubeville.cvpaintball.paintball;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
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

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Paintball extends TeamSelectorGame {

    int rechargeZoneChecker;
    private String error;
    private List<HashMap<String, Object>> teams;
    private Integer maxHealth;
    private final String[] healthColorCodes = {
            "§7§o", "§c", "§6", "§e", "§a"
    };


    public Paintball(String id, String arenaName) {
        super(id, arenaName);
        addGameVariable("spectate-lobby", new GameVariableLocation("The location the players go when they are eliminated"));
        addGameVariable("ammo", new GameVariableInt("The amount of ammo players get on game start and on recharge"), 16);
        addGameVariable("recharge-zones", new GameVariableList<>(GameVariableRegion.class, "Regions that will refill the player's ammo"));
        addGameVariable("recharge-cooldown", new GameVariableInt("The amount of time (in seconds) between ammo refills"), 15);
        addGameVariable("fire-cooldown", new GameVariableDouble("The amount of time (in seconds) between paintball shots"), 0.5);
        addGameVariableTeamsList(
            new HashMap<>(){{
                put("tps", new GameVariableList<>(GameVariableLocation.class, "The locations that players on this team will spawn in at"));
                put("loadout-team", new GameVariableString("The name of the team used for loadouts"));
                put("damaged-teams", new GameVariableList<>(GameVariableString.class,"A list of teams used for putting armor on the players (least damaged -> most damaged)"));
            }}
        );
        addGameVariable("loadout-paintball", new GameVariableString("The base loadout used for paintball"));
        addGameVariable("invuln-duration", new GameVariableInt("The length of invulnerability after being shot (in seconds)"), 2);
        addGameVariable("invuln1-loadout-team", new GameVariableString("The first loadout team used for the \"blinking\" effect when a player is invulnerable"));
        addGameVariable("invuln2-loadout-team", new GameVariableString("The second loadout team used for the \"blinking\" effect when a player is invulnerable"));
        addGameVariable("invuln-shooting", new GameVariableFlag("Whether the player can shoot while they are invulnerable"), false);
        addGameVariable("infinite-ammo", new GameVariableFlag("Whether the player has infinite ammo"), false);
    }

    @Nullable
    protected PaintballState getState(Player p) {
        if (state.get(p) == null || !(state.get(p) instanceof PaintballState)) return null;
        return (PaintballState) state.get(p);
    }

    @Override
    public void onGameStart(List<Set<Player>> teamPlayersList) {
        teams = (List<HashMap<String, Object>>) getVariable("teams");
        maxHealth = (((List<String>) teams.get(0).get("damaged-teams")).size() * 4);

        for (int i = 0; i < teams.size(); i++) {
            HashMap<String, Object> team = teams.get(i);
            Set<Player> teamPlayers = teamPlayersList.get(i);

            if (teamPlayers == null) { continue; }

            String teamName = (String) team.get("name");
            ChatColor chatColor = (ChatColor) team.get("chat-color");
            List<Location> tps = (List<Location>) team.get("tps");

            int j = 0;
            for (Player player : teamPlayers) {
                state.put(player, new PaintballState(i, maxHealth));

                player.getInventory().clear();

                CVLoadouts.getInstance().applyLoadoutToPlayer(player,
                        (String) getVariable("loadout-paintball"),
                        List.of((String) team.get("loadout-team"))
                );

                Location tpLoc = tps.get(j % tps.size());
                if (!tpLoc.getChunk().isLoaded()) {
                    tpLoc.getChunk().load();
                }
                player.teleport(tpLoc);
                if (teams.size() > 1) {
                    player.sendMessage(chatColor + "You are on §l" + teamName + chatColor + "!");
                    player.sendMessage(chatColor + "Last team standing wins!");
                } else {
                    player.sendMessage(chatColor + "It's a free for all!");
                    player.sendMessage(chatColor + "Last player standing wins!");
                }
                j++;
            }
        }

        List<GameRegion> rechargeZones = (List<GameRegion>) getVariable("recharge-zones");
        int cooldown = (int) getVariable("recharge-cooldown");
        rechargeZoneChecker = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVPaintball.getInstance(), () -> {
            for (GameRegion rechargeZone : rechargeZones) {
                for (Player player : state.keySet()) {
                    if (rechargeZone.containsPlayer(player) && !getState(player).isInvulnerable) {
                        Long lastRecharge = getState(player).lastRecharge;
                        if (lastRecharge == null || System.currentTimeMillis() - lastRecharge > (cooldown * 1000L)) {
                            getState(player).lastRecharge = System.currentTimeMillis();
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
        for (Player player : state.keySet()) {
            PaintballState ps = getState(player);
            if (ps == null || ps.health == 0 || stillInGame.contains(ps.team)) { continue; }
            stillInGame.add(ps.team);
        }
        return stillInGame;
    }

    private Set<Player> remainingPlayers() {
        Set<Player> stillInGame = new HashSet<>();
        for (Player player : state.keySet()) {
            PaintballState ps = getState(player);
            if (ps == null || ps.health == 0) { continue; }
            stillInGame.add(player);
        }
        return stillInGame;
    }

    private void resetPlayerSnowballs(Player player) {
        HashMap<String, Object> team = teams.get(getState(player).team);
        PlayerInventory inv = player.getInventory();

        String loadoutName = (String) getVariable("loadout-paintball");
        String teamName = (String) team.get("loadout-team");


        ItemStack snowballs = CVLoadouts.getInstance().getLoadoutItem(loadoutName, teamName, 0);

        if (snowballs == null) { finishGameWithError("Could not find snowballs in slot 0 in loadout " + loadoutName + " with team " + teamName); return; }
        inv.setItem(0, snowballs);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {

        if (event.getEntityType().equals(EntityType.SNOWBALL) && event.getHitEntity() instanceof Player && event.getEntity().getShooter() instanceof Player) {
            Snowball s = (Snowball) event.getEntity();
            Player hit = (Player) event.getHitEntity();
            Player attacker = (Player) s.getShooter();

            PaintballState hitState = getState(hit);
            PaintballState attackerState = getState(attacker);

            // return if either player is not in the game
            if (hitState == null || attackerState == null) { return; }

            // if the player isn't shooting themselves or their teammate
            if (hit.equals(attacker) || (hitState.team == attackerState.team && teams.size() > 1)) { return; }

            if (hitState.isInvulnerable) { return; }

            hitState.isInvulnerable = true;
            hitState.health -= 1;
            attackerState.successfulShots += 1;
            hitState.lastHit = System.currentTimeMillis();

            attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 0.7F);
            hit.playSound(hit.getLocation(), Sound.ENTITY_BOAT_PADDLE_WATER, 1.0F, 1.2F);
            sendMessageToArena(teams.get(attackerState.team).get("chat-color") + attacker.getDisplayName() + "§e has hit " + teams.get(hitState.team).get("chat-color") + hit.getDisplayName());
            updateScoreboard();

            if (hitState.health == 0) {
                if (testGameEnd()) { return; }
                hit.sendMessage("§4§lYou have been eliminated!");
                hit.getInventory().clear();
                hit.teleport((Location) getVariable("spectate-lobby"));
                return;
            }

            String loadoutName = (String) getVariable("loadout-paintball");
            int replacingSlot = (maxHealth - (hitState.health + 1)) % 4;
            try {
                String damagedTeam = ((ArrayList<String>) teams.get(hitState.team).get("damaged-teams")).get((maxHealth - (hitState.health + 1)) / 4);
                ItemStack damagedArmor = CVLoadouts.getInstance().getLoadoutItem(loadoutName, damagedTeam, 45 + replacingSlot);
                ItemStack damagedItem = CVLoadouts.getInstance().getLoadoutItem(loadoutName, damagedTeam, 36 + replacingSlot);
                if (damagedArmor == null) {
                    finishGameWithError("Could not find damaged armor in slot " + (45 + replacingSlot) + " in loadout " + loadoutName + " with team " + damagedTeam);
                    return;
                }

                switch (replacingSlot) {
                    case 0:
                        hit.getInventory().setHelmet(damagedArmor);
                        hit.getInventory().setItem(5, damagedItem);
                        break;
                    case 1:
                        hit.getInventory().setChestplate(damagedArmor);
                        hit.getInventory().setItem(6, damagedItem);
                        break;
                    case 2:
                        hit.getInventory().setLeggings(damagedArmor);
                        hit.getInventory().setItem(7, damagedItem);
                        break;
                    case 3:
                        hit.getInventory().setBoots(damagedArmor);
                        hit.getInventory().setItem(8, damagedItem);
                        break;
                }
            } catch (IndexOutOfBoundsException e) {
                finishGameWithError("Could not find loadout when player was hit on team " + (hitState.team + 1));
            }

            hitState.inventoryContents = hit.getInventory().getContents();
            long invunlDuration = ((int) getVariable("invuln-duration") * 1000L);
            List<String> teamLoadouts = Arrays.asList(((String) teams.get(hitState.team).get("loadout-team")).split(";"));

            hitState.armorFlashID = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVPaintball.getInstance(), () -> {
                PaintballState playerState = getState(hit);
                if (System.currentTimeMillis() - playerState.lastHit > invunlDuration) {
                    for (int i = 0; i < playerState.inventoryContents.length; i++) {
                        // don't replace the snowballs in the first slot if you can shoot while invulnerable
                        if (((Boolean) getVariable("invuln-shooting")) && i == 0) continue;
                        hit.getInventory().setItem(i, playerState.inventoryContents[i]);
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
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!event.getEntityType().equals(EntityType.SNOWBALL) || !(event.getEntity().getShooter() instanceof Player)) return;
        PaintballState pbs = getState((Player) event.getEntity().getShooter());
        if (pbs == null) return;
        Double cooldown = (Double) getVariable("fire-cooldown");

        if (pbs.lastFire != null && (System.currentTimeMillis() - pbs.lastFire) <= (cooldown * 1000L)) {
            // don't fire too fast
            event.setCancelled(true);
            return;
        }

        if ((Boolean) getVariable("infinite-ammo")) {
            resetPlayerSnowballs((Player) event.getEntity().getShooter());
        }

        pbs.lastFire = System.currentTimeMillis();
        pbs.timesFired += 1;
    }

    private boolean testGameEnd() {
        if (teams.size() == 1) {
            if (remainingPlayers().size() <= 1) {
                finishGame();
                return true;
            }
        } else {
            if (remainingTeams().size() <= 1) {
                finishGame();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPlayerLeave(Player p) {
        if (getState(p) != null) {
            Bukkit.getScheduler().cancelTask(getState(p).armorFlashID);
            getState(p).armorFlashID = -1;
        }
        p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
        state.remove(p);
        testGameEnd();
    }

    private void finishGameWithError(String error) {
        this.error = error;
        finishGame();
    }

    @Override
    public void onGameFinish() {
        for (Player player : state.keySet()) {
            if (state.containsKey(player) && getState(player).armorFlashID != -1) {
                Bukkit.getScheduler().cancelTask(getState(player).armorFlashID);
                getState(player).armorFlashID = -1;
            }
        }

        Bukkit.getScheduler().cancelTask(rechargeZoneChecker);
        rechargeZoneChecker = 0;

        if (error != null) {
            sendMessageToArena("§c§lERROR: §c" + error);
        } else if (teams.size() > 1) {
            finishTeamGame();
        } else {
            finishFFAGame();
        }
        error = null;
        state.keySet().forEach(p -> p.getScoreboard().clearSlot(DisplaySlot.SIDEBAR));
    }

    private void finishFFAGame() {
        Player remainingPlayer = null;
        // ffa game
        for (Player player : remainingPlayers()) { remainingPlayer = player; }
        if (remainingPlayer == null) { return; }

        ChatColor chatColor = (ChatColor) teams.get(0).get("chat-color");

        sendMessageToArena(chatColor + "§l" + remainingPlayer.getDisplayName() + chatColor + "§l has won the game!");
        for (Player player : state.keySet()) {
            sendStatistics(player);
        }
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

    private void finishTeamGame() {
        int remainingTeam = -1;
        for (int rt : remainingTeams()) { remainingTeam = rt; }
        if (remainingTeam < 0) { return; }

        ChatColor chatColor = (ChatColor) teams.get(remainingTeam).get("chat-color");

        String teamName = (String) teams.get(remainingTeam).get("name");
        sendMessageToArena(chatColor + "§l" + teamName + chatColor + "§l has won the game!");
        for (Player player : state.keySet()) {
            sendStatistics(player);
        }
    }

    private void sendStatistics(Player player) {
        PaintballState ps = getState(player);
        player.sendMessage("§7Shots fired: §f" + ps.timesFired);
        player.sendMessage("§7Shots hit: §f" + ps.successfulShots);
        if (ps.timesFired == 0 || ps.successfulShots == 0) {
            player.sendMessage("§7Accuracy: §f0.00%");
        } else {
            player.sendMessage("§7Accuracy: §f" + String.format("%.2f", ((float) ps.successfulShots / (float) ps.timesFired) * 100F) + "%");
        }
    }

    private void updateScoreboard() {
        Scoreboard scoreboard;
        if (teams.size() == 1) {
            ArrayList<String> scoreboardLines = new ArrayList<>();
            state.keySet().stream().sorted(Comparator.comparingInt(o -> -1 * getState(o).health)).forEach( p -> {
                int health = getState(p).health;
                scoreboardLines.add(healthColorCodes[Math.min(health, 4)] + p.getDisplayName() + "§f: " + health + " HP");
            });
            scoreboard = GameUtils.createScoreboard(arena, "§b§lFFA paintball", scoreboardLines);
        } else {
            HashMap<Integer, Integer> countPerTeam = new HashMap<>();
            for (Player p : remainingPlayers()) {
                int team = getState(p).team;
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
            scoreboard = GameUtils.createScoreboard(arena, "§b§lTeam paintball", scoreboardLines);
        }
        sendScoreboardToArena(scoreboard);
    }
}
