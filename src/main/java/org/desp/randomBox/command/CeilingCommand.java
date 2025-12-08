package org.desp.randomBox.command;

import com.binggre.binggreapi.utils.ColorManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.desp.randomBox.database.CeilingRepository;
import org.desp.randomBox.database.PlayerCeilingRepository;
import org.desp.randomBox.dto.CeilingDataDto;
import org.desp.randomBox.dto.DetailedCeilingDto;
import org.desp.randomBox.dto.PlayerCeilingDto;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class CeilingCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        Player player = (Player) commandSender;

        PlayerCeilingRepository playerCeilingRepository = PlayerCeilingRepository.getInstance();
        CeilingRepository ceilingRepository = CeilingRepository.getInstance();
        PlayerCeilingDto playerCacheData = playerCeilingRepository.getPlayerCacheData(player);

        HashMap<String, DetailedCeilingDto> playerCeilingData = playerCacheData.getCeilingData();


        player.sendMessage(ColorManager.format("#54daf4§n                                                                                                      §f"));
        player.sendMessage(ColorManager.format(""));

        for (String ceilingID : playerCeilingData.keySet()) {
            DetailedCeilingDto detailedCeilingDto = playerCeilingData.get(ceilingID);
            CeilingDataDto ceilingData = ceilingRepository.getCeilingData(ceilingID);

            String resultMessage = "  §f- §6"+ceilingData.getName()+"§f: 보상";

            int toRewardCount = ceilingData.getAmount() - detailedCeilingDto.getAmount();

            if(toRewardCount <= 0){
                resultMessage += "을 이미 획득하셨기 때문에 §c더 이상 획득하실 수 없습니다.";
            } else {
                resultMessage += "§f 획득까지 §e"+toRewardCount+" 회 §f남았습니다. ";
            }


            if(ceilingData.isVolatile()){
                resultMessage+="§a ( 반복 가능 )";
            } else {
                resultMessage+="§c ( 반복 불가능 )";
            }

            player.sendMessage(resultMessage);
        }

        player.sendMessage(ColorManager.format("#54daf4§n                                                                                                      §f"));


        return false;
    }
}
