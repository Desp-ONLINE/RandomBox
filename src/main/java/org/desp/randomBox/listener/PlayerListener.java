package org.desp.randomBox.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.desp.randomBox.database.PlayerCeilingRepository;
import org.desp.randomBox.dto.PlayerCeilingDto;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerCeilingRepository.getInstance().loadPlayerData(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerCeilingRepository.getInstance().savePlayerData(player);
        PlayerCeilingDto playerCacheData = PlayerCeilingRepository.getInstance().getPlayerCacheData(player);
        playerCacheData.getCeilingData().keySet().forEach(key -> {
            System.out.println("key = " + key);
        });
    }
}
