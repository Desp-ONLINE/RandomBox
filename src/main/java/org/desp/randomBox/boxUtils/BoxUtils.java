package org.desp.randomBox.boxUtils;

import com.binggre.mmomail.MMOMail;
import com.binggre.mmomail.objects.Mail;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.dto.ItemDataDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BoxUtils {

    public static List<ItemStack> getReward(ItemDataDto itemDataDto) {
        List<ItemStack> rewardItems = new ArrayList<>();

        int amount = itemDataDto.getAmount();

        ItemStack item = getValidRewardItem(itemDataDto);
        item.setAmount(amount);
        rewardItems.add(item);

        return rewardItems;
    }

    public static ItemStack getValidRewardItem(ItemDataDto itemDataDto) {
        String type = itemDataDto.getType();
        ItemStack rewardItem = MMOItems.plugin.getItem(Type.get(type), itemDataDto.getItem_id());

        return rewardItem;
    }

    public static void sendReward(String box_id, List<ItemStack> reward, Player player) {
        MMOMail mmoMail = MMOMail.getInstance();
        Mail rewardMail = mmoMail.getMailAPI().createMail(
                "시스템",
                "랜덤박스, §a"+box_id+"§f의 보상입니다.",
                0,
                reward
        );
        mmoMail.getMailAPI().sendMail(player.getName(), rewardMail);
    }

    /** 보물상자(블록 상자, 상자 아이템) 보상 메일. crateName 은 § 색코드가 들어간 표시 이름이다. */
    public static void sendCrateReward(String crateName, List<ItemStack> reward, Player player) {
        MMOMail mmoMail = MMOMail.getInstance();
        Mail rewardMail = mmoMail.getMailAPI().createMail(
                "시스템",
                "보물상자, " + crateName + "§f의 보상입니다.",
                0,
                reward
        );
        mmoMail.getMailAPI().sendMail(player.getName(), rewardMail);
    }

    public static ItemDataDto getRandomItem(List<ItemDataDto> items) {
        Random random = new Random();

        double totalWeight = items.stream().mapToDouble(ItemDataDto::getChance).sum();
        double randomValue = random.nextDouble(totalWeight);

        double cumulativeWeight = 0;
        for (ItemDataDto item : items) {
            cumulativeWeight += item.getChance();
            if (randomValue < cumulativeWeight) {
                return item;
            }
        }
        return null;
    }
}
