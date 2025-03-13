package org.desp.randomBox.listener;

import static org.desp.randomBox.boxUtils.BoxUtils.getRandomItem;
import static org.desp.randomBox.boxUtils.BoxUtils.getValidRewardItem;

import com.binggre.binggreapi.BinggrePlugin;
import com.binggre.binggreapi.utils.ColorManager;
import com.binggre.velocitysocketclient.VelocityClient;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Map;

import net.Indyuce.mmoitems.MMOItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.md_5.bungee.api.ChatColor;
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

        Component useItemComponent = itemInMainHand.displayName();
        String useItemDisplayName = itemInMainHand.getItemMeta().getDisplayName();

        String playerRightHandItemID = MMOItems.getID(itemInMainHand);

        if (event.getAction().isRightClick() && boxData.containsKey(playerRightHandItemID)) {
            BoxDataDto boxDataDto = boxData.get(playerRightHandItemID);

            String box_id = boxDataDto.getBox_id();

            List<ItemDataDto> availableItems = boxDataDto.getAvailableItems();

            ItemDataDto randomItem = getRandomItem(availableItems);

            ItemStack rewardItem = getValidRewardItem(randomItem);
            Component rewardItemComponent = rewardItem.getItemMeta().displayName();

            String itemId = randomItem.getItem_id();
            BoxUtils.sendReward(box_id, BoxUtils.getReward(randomItem), player);

            if (randomItem.isNotice()) {
                TextComponent message = Component.text("§f " + player.getName() + " 님께서 §a").append(useItemComponent).append(Component.text("§f 에서 ") )
                        .append(rewardItemComponent)
                        .append(Component.text("§f를 획득하셨습니다!"));

                Bukkit.broadcast(message);
                String serializedComponent = JSONComponentSerializer.json().serialize(message);
                VelocityClient.getInstance().getConnectClient().send(VelocityProxyListener.class, serializedComponent);
            } else {
                player.sendMessage(useItemDisplayName + "§f으로 " + rewardItem.getItemMeta().getDisplayName() + "§f를 뽑기에 성공했습니다!");
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
