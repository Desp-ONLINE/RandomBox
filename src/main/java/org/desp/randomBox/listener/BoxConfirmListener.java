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
import org.bson.Document;
import org.desp.randomBox.boxUtils.BoxUtils;
import org.desp.randomBox.boxUtils.DateUtils;
import org.desp.randomBox.boxUtils.ImmortalMasteryBookMapper;
import org.desp.randomBox.command.ForcedRewardCommand;
import org.desp.randomBox.database.BoxDataRepository;
import org.desp.randomBox.database.ForcedRewardRepository;
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

            // 관리자가 강제 지정한 보상이 있으면 첫 뽑기에 적용한다. (1회 소모, 유저에게는 일반 뽑기와 동일하게 보임)
            Document forcedRewardDoc = ForcedRewardRepository.getInstance().consume(player.getName(), box_id);
            ItemDataDto forcedItem = null;
            if (forcedRewardDoc != null) {
                forcedItem = ForcedRewardCommand.findLegacyItem(boxDataDto, forcedRewardDoc.getString("reward_id"));
                if (forcedItem == null) {
                    RandomBox.getInstance().getLogger().warning("[RandomBox-Debug] " + player.getName()
                            + " 강제 보상 ID(" + forcedRewardDoc.getString("reward_id") + ") 를 박스 " + box_id + " 에서 찾지 못함 — 일반 뽑기로 진행");
                }
            }

            for (int i = 0; i < openCount; i++) {
                ItemDataDto randomItem = (i == 0 && forcedItem != null) ? forcedItem : getRandomItem(availableItems);

                ItemStack rewardItem = getValidRewardItem(randomItem);
                ItemStack bonusBook = ImmortalMasteryBookMapper.bonusFor(playerRightHandItemID, rewardItem);
                Component rewardItemComponent = rewardItem.getItemMeta().displayName();

                String itemId = randomItem.getItem_id();
                int amount = randomItem.getAmount();
                double chance = randomItem.getChance();
                boolean notice = randomItem.isNotice();

                RandomBoxOpenEvent randomBoxOpenEvent = new RandomBoxOpenEvent(player, itemId, amount, chance, notice, box_id);
                Bukkit.getPluginManager().callEvent(randomBoxOpenEvent);
                if (randomBoxOpenEvent.isCancelled()) {
                    // 천장 보상으로 대체되어 미지급된 경우, 강제 보상 설정을 되돌려 다음 뽑기에 다시 적용되게 한다.
                    if (i == 0 && forcedItem != null) {
                        ForcedRewardRepository.getInstance().restore(forcedRewardDoc);
                    }
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
                    if (bonusBook != null) {
                        player.sendMessage("§f추가 보상으로 " + bonusBook.getItemMeta().getDisplayName() + "§f를 획득했습니다!");
                    }
                } else {
                    rewardSummary.merge(rewardItem.getItemMeta().getDisplayName(), amount, Integer::sum);
                    if (bonusBook != null) {
                        rewardSummary.merge(bonusBook.getItemMeta().getDisplayName(), amount, Integer::sum);
                    }
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

                // 불멸고서에서 불멸 마스터리북이 나왔으면 같은 수량만큼 레전더리 마스터리북을 같은 우편에 추가 지급
                ItemStack bonusBook = ImmortalMasteryBookMapper.bonusFor(playerRightHandItemID, base);
                if (bonusBook != null) {
                    RandomBox.getInstance().getLogger().info("[RandomBox-Debug] 불멸 마스터리북 추가 지급: "
                            + MMOItems.getID(base) + " + " + MMOItems.getID(bonusBook) + " x " + totalAmount);
                    int bonusMaxStack = Math.max(1, bonusBook.getMaxStackSize());
                    int bonusRemaining = totalAmount;
                    while (bonusRemaining > 0) {
                        ItemStack copy = bonusBook.clone();
                        int chunk = Math.min(bonusRemaining, bonusMaxStack);
                        copy.setAmount(chunk);
                        stacks.add(copy);
                        bonusRemaining -= chunk;
                    }
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
