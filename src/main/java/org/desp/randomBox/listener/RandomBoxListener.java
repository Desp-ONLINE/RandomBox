package org.desp.randomBox.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.desp.randomBox.api.RandomBoxOpenEvent;
import org.desp.randomBox.database.PlayerCeilingRepository;

public class RandomBoxListener implements Listener {

    @EventHandler
    public void onPlayerJoin(RandomBoxOpenEvent event) {
        Player player = event.getPlayer();
        String randomBoxID = event.getRandomBoxID();

        switch (randomBoxID){
            case "기타_레전더리고서_파우스트1":
                if(!event.getItemID().equals("마스터리북_파우스트_레전더리1")){
                    PlayerCeilingRepository.getInstance().increaseCeilingAmount(player, "파우스트레전더리마북", 1);
                }
                return;
        }
    }


}
