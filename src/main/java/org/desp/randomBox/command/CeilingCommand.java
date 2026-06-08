package org.desp.randomBox.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.desp.randomBox.database.CeilingRepository;
import org.desp.randomBox.database.PlayerCeilingRepository;
import org.desp.randomBox.dto.DetailedCeilingDto;
import org.desp.randomBox.dto.PlayerCeilingDto;
import org.desp.randomBox.gui.CeilingGui;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class CeilingCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("§c플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        Player player = (Player) commandSender;

        PlayerCeilingRepository playerCeilingRepository = PlayerCeilingRepository.getInstance();
        CeilingRepository ceilingRepository = CeilingRepository.getInstance();
        PlayerCeilingDto playerCacheData = playerCeilingRepository.getPlayerCacheData(player);

        if (playerCacheData == null) {
            player.sendMessage("§c천장 데이터를 불러오는 중입니다. 잠시 후 다시 시도해주세요.");
            return true;
        }

        HashMap<String, DetailedCeilingDto> playerCeilingData = playerCacheData.getCeilingData();
        if (playerCeilingData == null || playerCeilingData.isEmpty()) {
            player.sendMessage("§7표시할 천장 정보가 없습니다.");
            return true;
        }

        CeilingGui gui = new CeilingGui(playerCeilingData, ceilingRepository);
        player.openInventory(gui.getInventory());
        return true;
    }
}
