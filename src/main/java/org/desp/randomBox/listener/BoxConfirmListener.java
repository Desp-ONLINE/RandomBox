package org.desp.randomBox.listener;

import com.binggre.velocitysocketclient.VelocityClient;
import net.Indyuce.mmoitems.MMOItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.api.RandomBoxOpenEvent;
import org.desp.randomBox.boxUtils.BoxUtils;
import org.desp.randomBox.boxUtils.DateUtils;
import org.desp.randomBox.database.BoxDataRepository;
import org.desp.randomBox.database.ItemDrawRepository;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.dto.BoxDataDto;
import org.desp.randomBox.dto.ItemDataDto;
import org.desp.randomBox.dto.ItemDrawLogDto;
import org.dople.dataSync.inventory.InventorySyncListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.desp.randomBox.boxUtils.BoxUtils.getRandomItem;
import static org.desp.randomBox.boxUtils.BoxUtils.getValidRewardItem;

public class BoxConfirmListener implements Listener {

    public BoxDataRepository boxDataRepository;
    public ItemDrawRepository itemDrawRepository;

    private final Map<UUID, Long> joinTimeMap = new ConcurrentHashMap<>();

    public BoxConfirmListener() {
        this.boxDataRepository = BoxDataRepository.getInstance();
        this.itemDrawRepository = ItemDrawRepository.getInstance();
    }

    private static final class MailJob {
        final List<ItemStack> reward;
        final String drawItemId;

        MailJob(List<ItemStack> reward, String drawItemId) {
            this.reward = reward;
            this.drawItemId = drawItemId;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        joinTimeMap.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        joinTimeMap.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onConfirm(PlayerInteractEvent event) {

        Map<String, BoxDataDto> boxData = boxDataRepository.getBoxData();

        Player player = event.getPlayer();
        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
        if(itemInMainHand.equals(new ItemStack(Material.AIR))) {
            return;
        }

        Component useItemComponent = itemInMainHand.displayName();
        String useItemDisplayName = itemInMainHand.getItemMeta().getDisplayName();

        String playerRightHandItemID = MMOItems.getID(itemInMainHand);



        if (event.getAction().isRightClick() && boxData.containsKey(playerRightHandItemID)) {
            BoxDataDto boxDataDto = boxData.get(playerRightHandItemID);

            String box_id = boxDataDto.getBox_id();

            List<ItemDataDto> availableItems = boxDataDto.getAvailableItems();

            Long joinTime = joinTimeMap.get(player.getUniqueId());
            if (joinTime != null && System.currentTimeMillis() - joinTime < 3000L) {
                player.sendMessage("§c 데이터가 로드중입니다.");
                return;
            }

            if (InventorySyncListener.isDataLoading(player)) {
                player.sendMessage("§c 데이터가 로드중입니다.");
                return;
            }

            int openCount = player.isSneaking() ? itemInMainHand.getAmount() : 1;

            if (itemInMainHand.getAmount() > openCount) {
                itemInMainHand.setAmount(itemInMainHand.getAmount() - openCount);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }

            Map<String, Integer> rewardSummary = new LinkedHashMap<>();
            Map<String, Integer> aggregatedAmount = new LinkedHashMap<>();
            Map<String, ItemDataDto> aggregatedSample = new LinkedHashMap<>();

            for (int i = 0; i < openCount; i++) {
                ItemDataDto randomItem = getRandomItem(availableItems);

                ItemStack rewardItem = getValidRewardItem(randomItem);
                Component rewardItemComponent = rewardItem.getItemMeta().displayName();

                String itemId = randomItem.getItem_id();
                int amount = randomItem.getAmount();
                double chance = randomItem.getChance();
                boolean notice = randomItem.isNotice();

                RandomBoxOpenEvent randomBoxOpenEvent = new RandomBoxOpenEvent(player, itemId, amount, chance, notice, box_id);
                Bukkit.getPluginManager().callEvent(randomBoxOpenEvent);
                if (randomBoxOpenEvent.isCancelled()) {
                    continue;
                }

                if (openCount == 1) {
                    if (notice) {
                        TextComponent message = Component.text("§f " + player.getName() + " 님께서 §a").append(useItemComponent).append(Component.text("§f 에서 "))
                                .append(rewardItemComponent)
                                .append(Component.text("§f를 획득하셨습니다!"));

                        Bukkit.broadcast(message);
                        String serializedComponent = JSONComponentSerializer.json().serialize(message);
                        VelocityClient.getInstance().getConnectClient().send(VelocityProxyListener.class, serializedComponent);
                    } else {
                        player.sendMessage(useItemDisplayName + "§f으로 " + rewardItem.getItemMeta().getDisplayName() + "§f를 획득했습니다!");
                    }
                } else {
                    rewardSummary.merge(rewardItem.getItemMeta().getDisplayName(), amount, Integer::sum);
                }

                String key = randomItem.getType() + "|" + itemId;
                aggregatedAmount.merge(key, amount, Integer::sum);
                aggregatedSample.putIfAbsent(key, randomItem);
            }

            if (openCount > 1) {
                player.sendMessage(useItemDisplayName + "§f을(를) §e" + openCount + "§f개 소모하여 다음 보상을 획득하셨습니다:");
                for (Map.Entry<String, Integer> entry : rewardSummary.entrySet()) {
                    player.sendMessage("§7- " + entry.getKey() + "§7 x §e" + entry.getValue());
                }
            }

            List<MailJob> mailJobs = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : aggregatedAmount.entrySet()) {
                ItemDataDto sample = aggregatedSample.get(entry.getKey());
                int totalAmount = entry.getValue();

                ItemStack base = getValidRewardItem(sample);
                int maxStack = base.getMaxStackSize();
                if (maxStack < 1) maxStack = 1;

                List<ItemStack> stacks = new ArrayList<>();
                int remaining = totalAmount;
                while (remaining > 0) {
                    ItemStack copy = base.clone();
                    int chunk = Math.min(remaining, maxStack);
                    copy.setAmount(chunk);
                    stacks.add(copy);
                    remaining -= chunk;
                }

                mailJobs.add(new MailJob(stacks, sample.getItem_id()));
            }

            if (!mailJobs.isEmpty()) {
                final String playerName = player.getName();
                final String playerUuid = player.getUniqueId().toString();
                final int totalJobs = mailJobs.size();
                final java.util.logging.Logger logger = RandomBox.getInstance().getLogger();
                logger.info("[RandomBox-Debug] " + playerName + " 메일 발송 시작: box=" + box_id + ", 총 " + totalJobs + "개");

                for (int i = 0; i < mailJobs.size(); i++) {
                    final MailJob job = mailJobs.get(i);
                    final int seq = i + 1;
                    Bukkit.getScheduler().runTaskLater(RandomBox.getInstance(), () -> {
                        try {
                            logger.info("[RandomBox-Debug] (" + seq + "/" + totalJobs + ") sendReward 시도 — item=" + job.drawItemId + ", rewardSize=" + job.reward.size());
                            BoxUtils.sendReward(box_id, job.reward, player);
                            logger.info("[RandomBox-Debug] (" + seq + "/" + totalJobs + ") sendReward 완료");
                        } catch (Exception e) {
                            logger.warning("[RandomBox-Debug] (" + seq + "/" + totalJobs + ") sendReward 실패: " + e.getClass().getSimpleName() + " — " + e.getMessage());
                            e.printStackTrace();
                        }

                        Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () -> {
                            try {
                                ItemDrawLogDto playerDto = ItemDrawLogDto.builder()
                                        .user_id(playerName)
                                        .uuid(playerUuid)
                                        .usedItem(box_id)
                                        .drawItem(job.drawItemId)
                                        .build();
                                itemDrawRepository.insertDrawLog(playerDto, DateUtils.getCurrentTime());
                            } catch (Exception e) {
                                logger.warning("[RandomBox-Debug] (" + seq + "/" + totalJobs + ") insertDrawLog 실패: " + e.getClass().getSimpleName() + " — " + e.getMessage());
                                e.printStackTrace();
                            }
                        });

                        if (seq == totalJobs) {
                            logger.info("[RandomBox-Debug] " + playerName + " 메일 발송 종료 (총 " + totalJobs + "개)");
                        }
                    }, i);
                }
            }
        }
    }
}
