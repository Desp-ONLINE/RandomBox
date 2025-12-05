package org.desp.randomBox.listener;

import com.binggre.binggreapi.utils.ColorManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.desp.randomBox.api.RandomBoxOpenEvent;
import org.desp.randomBox.database.CeilingRepository;
import org.desp.randomBox.database.PlayerCeilingRepository;
import org.desp.randomBox.dto.CeilingDataDto;
import org.desp.randomBox.dto.DetailedCeilingDto;
import org.desp.randomBox.dto.PlayerCeilingDto;

public class RandomBoxListener implements Listener {

    @EventHandler
    public void onPlayerJoin(RandomBoxOpenEvent event) {
        Player player = event.getPlayer();
        String randomBoxID = event.getRandomBoxID();

        CeilingRepository ceilingRepository = CeilingRepository.getInstance();
        PlayerCeilingRepository playerCeilingRepository = PlayerCeilingRepository.getInstance();


        boolean isContained = ceilingRepository.isContainedKeys("randombox", randomBoxID);

        if (!isContained) {
            return;
        }

        CeilingDataDto ceilingData = ceilingRepository.getCeilingData(randomBoxID);
        PlayerCeilingDto playerCacheData = playerCeilingRepository.getPlayerCacheData(player);
        DetailedCeilingDto detailedPlayerCeilingData = playerCacheData.getCeilingData().get(randomBoxID);

        if (event.getItemID().equals(ceilingData.getGoalString())) {
            playerCeilingRepository.volatileAmount(player, randomBoxID, false);
            player.sendMessage(ColorManager.format("#54daf4 [천장 시스템] §f" + ceilingData.getName() + " 목표 보상 획득으로 진척도가 초기화 되었습니다."));
            return;
        } else {
            playerCeilingRepository.increaseCeilingAmount(player, randomBoxID, 1);
            player.sendMessage(ColorManager.format("#54daf4 [천장 시스템] §f" + ceilingData.getName() +
                    " 천장 시스템 보상 획득까지 §e" + (ceilingData.getAmount() - detailedPlayerCeilingData.getAmount()) +
                    " 회 §f남았습니다. §7§o(" + ceilingData.getAmount() + "/" + detailedPlayerCeilingData.getAmount()) + ")");
            player.sendMessage("§7§o     ( /천장 명령어를 통해 더 자세히 확인하실 수 있습니다. )");
            return;
        }


    }


}
