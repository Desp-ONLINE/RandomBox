package org.desp.randomBox.listener;

import static org.desp.randomBox.boxUtils.BoxUtils.getRandomItem;
import static org.desp.randomBox.boxUtils.BoxUtils.getValidRewardItem;

import com.binggre.velocitysocketclient.VelocityClient;
import java.util.List;
import java.util.Map;
import net.Indyuce.mmoitems.MMOItems;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.boxUtils.BoxUtils;
import org.desp.randomBox.boxUtils.DateUtils;
import org.desp.randomBox.database.BoxDataRepository;
import org.desp.randomBox.database.ItemDrawRepository;
import org.desp.randomBox.dto.BoxDataDto;
import org.desp.randomBox.dto.ItemDataDto;
import org.desp.randomBox.dto.ItemDrawLogDto;

public class BoxConfirmListener implements Listener {

    public BoxDataRepository boxDataRepository;
    public ItemDrawRepository itemDrawRepository;

    public BoxConfirmListener() {
        this.boxDataRepository = BoxDataRepository.getInstance();
        this.itemDrawRepository = ItemDrawRepository.getInstance();
    }

    @EventHandler
    public void onConfirm(PlayerInteractEvent event) {

        Map<String, BoxDataDto> boxData = boxDataRepository.getBoxData();

        Player player = event.getPlayer();
        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();

        String itemInMainHandDisplayName = itemInMainHand.getItemMeta().getDisplayName().replace("&", "§");

        String playerRightHandItemID = MMOItems.getID(itemInMainHand);

        if (event.getAction().isRightClick() && boxData.containsKey(playerRightHandItemID)) {
            BoxDataDto boxDataDto = boxData.get(playerRightHandItemID);

            String box_id = boxDataDto.getBox_id();

            List<ItemDataDto> availableItems = boxDataDto.getAvailableItems();

            ItemDataDto randomItem = getRandomItem(availableItems);

            ItemStack item = getValidRewardItem(randomItem);
            String result = item.getItemMeta().getDisplayName().replace("&", "§");

            String itemId = randomItem.getItem_id();
            BoxUtils.sendReward(box_id, BoxUtils.getReward(randomItem), player);

            if (randomItem.isNotice()) {
                String message = "§f" + player.getName() + " 님께서 §a" + itemInMainHandDisplayName + "§f에서 " + result+ "§f를 획득하셨습니다!";

                Bukkit.broadcast(Component.text(message));
                VelocityClient.getInstance().getConnectClient().send(VelocityProxyListener.class, message);
            } else {
                player.sendMessage(itemInMainHandDisplayName+"§f으로 "+result+"§f를 뽑기에 성공했습니다!");
            }

            if (itemInMainHand.getAmount() > 1) {
                itemInMainHand.setAmount(itemInMainHand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }

            ItemDrawLogDto playerDto = ItemDrawLogDto.builder()
                    .user_id(player.getName())
                    .uuid(player.getUniqueId().toString())
                    .usedItem(box_id)
                    .drawItem(itemId)
                    .build();

            itemDrawRepository.insertDrawLog(playerDto, DateUtils.getCurrentTime());
        }
    }
}
