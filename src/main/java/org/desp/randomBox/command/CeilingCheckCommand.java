package org.desp.randomBox.command;

import com.binggre.binggreapi.utils.ColorManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.database.CeilingRepository;
import org.desp.randomBox.database.PlayerCeilingRepository;
import org.desp.randomBox.dto.CeilingDataDto;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CeilingCheckCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c사용법: /천장확인 <닉네임>");
            return true;
        }

        String nickname = args[0];
        PlayerCeilingRepository playerCeilingRepository = PlayerCeilingRepository.getInstance();
        CeilingRepository ceilingRepository = CeilingRepository.getInstance();

        Map<String, Integer> amounts = playerCeilingRepository.getCeilingAmountsByNickname(nickname);
        if (amounts == null) {
            sender.sendMessage("§c'" + nickname + "' 님의 천장 데이터를 찾을 수 없습니다.");
            return true;
        }

        sender.sendMessage(ColorManager.format("#54daf4§n                                                                                                      §f"));
        sender.sendMessage(ColorManager.format("#54daf4 [천장확인] §f" + nickname + " 님이 접속 시 지급받을 천장 보상"));
        sender.sendMessage("§f");

        boolean hasPending = false;
        for (Map.Entry<String, Integer> entry : amounts.entrySet()) {
            String ceilingID = entry.getKey();
            int playerAmount = entry.getValue();

            int times = ceilingRepository.getPendingRewardTimes(ceilingID, playerAmount);
            if (times <= 0) {
                continue;
            }

            CeilingDataDto ceilingData = ceilingRepository.getCeilingData(ceilingID);
            int goal = ceilingData.getAmount();
            int remaining = playerAmount % goal;

            hasPending = true;
            sender.sendMessage("§6- " + ceilingData.getName() + " §f: 누적 §e" + playerAmount
                    + "§f / 목표 §e" + goal + "§f → §a" + times + "회 지급§f (지급 후 잔여 §e" + remaining + "§f)");

            for (ItemStack item : ceilingRepository.buildRewardItems(ceilingID, times)) {
                String displayName = (item.getItemMeta() != null && item.getItemMeta().hasDisplayName())
                        ? item.getItemMeta().getDisplayName()
                        : item.getType().name();
                sender.sendMessage("   §e- " + displayName + " §fx" + item.getAmount());
            }
        }

        if (!hasPending) {
            sender.sendMessage("§7 지급 예정인 천장 보상이 없습니다. (volatile 천장 중 누적량이 목표 이상인 항목 없음)");
        }

        sender.sendMessage(ColorManager.format("#54daf4§n                                                                                                      §f"));
        return true;
    }
}
