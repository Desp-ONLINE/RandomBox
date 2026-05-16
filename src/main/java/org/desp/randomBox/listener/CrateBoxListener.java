package org.desp.randomBox.listener;

import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.boxUtils.BoxUtils;
import org.desp.randomBox.boxUtils.DateUtils;
import org.desp.randomBox.database.CrateBoxRepository;
import org.desp.randomBox.database.ItemDrawRepository;
import org.desp.randomBox.dto.CrateBoxDto;
import org.desp.randomBox.dto.ItemDrawLogDto;
import org.dople.dataSync.inventory.InventorySyncListener;
import su.nightexpress.excellentcrates.CratesAPI;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.api.crate.Reward;
import su.nightexpress.excellentcrates.api.crate.RewardType;
import su.nightexpress.excellentcrates.api.item.ItemProvider;
import su.nightexpress.excellentcrates.crate.CrateManager;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.crate.reward.impl.ItemReward;
import su.nightexpress.excellentcrates.data.crate.UserCrateData;
import su.nightexpress.excellentcrates.user.CrateUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CrateBoxListener implements Listener {

    public CrateBoxRepository crateBoxRepository;
    public ItemDrawRepository itemDrawRepository;

    public CrateBoxListener() {
        this.crateBoxRepository = CrateBoxRepository.getInstance();
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
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
        if (itemInMainHand.equals(new ItemStack(Material.AIR))) {
            return;
        }

        String playerRightHandItemID = MMOItems.getID(itemInMainHand);
        if (playerRightHandItemID == null) return;

        CrateBoxDto crateBox = crateBoxRepository.get(playerRightHandItemID);
        if (crateBox == null) return;

        if (!player.isSneaking()) return;

        if (InventorySyncListener.isDataLoading(player)) {
            player.sendMessage("§c 데이터가 로드중입니다.");
            return;
        }

        Plugin ecPlugin = Bukkit.getPluginManager().getPlugin("ExcellentCrates");
        if (!(ecPlugin instanceof CratesPlugin)) {
            player.sendMessage("§c ExcellentCrates 플러그인이 활성화되어 있지 않습니다.");
            return;
        }
        CrateManager crateManager = ((CratesPlugin) ecPlugin).getCrateManager();
        Crate crate = crateManager.getCrateById(crateBox.getCrate_id());
        if (crate == null) {
            player.sendMessage("§c 연결된 Crate(" + crateBox.getCrate_id() + ")를 찾을 수 없습니다.");
            return;
        }

        String box_id = crateBox.getBox_id();
        String useItemDisplayName = itemInMainHand.getItemMeta().getDisplayName();

        int openCount = 1;

        final Logger logger = RandomBox.getInstance().getLogger();
        logger.info("[CrateBox-Debug] " + player.getName() + " 진입: box=" + box_id + ", crate=" + crateBox.getCrate_id() + ", openCount=" + openCount);

        if (itemInMainHand.getAmount() > openCount) {
            itemInMainHand.setAmount(itemInMainHand.getAmount() - openCount);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        Map<String, Integer> rewardSummary = new LinkedHashMap<>();
        Map<String, List<ItemStack>> aggregatedStacks = new LinkedHashMap<>();

        CrateUser crateUser = CratesAPI.getUserData(player);
        UserCrateData crateData = crateUser != null ? crateUser.getCrateData(crate) : null;

        for (int i = 0; i < openCount; i++) {
            if (crateData != null && crate.hasMilestones()) {
                crateData.addMilestones(1);
                crateManager.triggerMilestones(player, crate, crateData.getMilestone());
                if (crateData.getMilestone() >= crate.getMaxMilestone() && crate.isMilestonesRepeatable()) {
                    crateData.setMilestone(0);
                }
                logger.info("[CrateBox-Debug]   milestone=" + crateData.getMilestone() + "/" + crate.getMaxMilestone());
            }

            Reward reward = crate.rollReward(player);
            if (reward == null) {
                logger.warning("[CrateBox-Debug] iter " + (i + 1) + "/" + openCount + ": rollReward 결과 null");
                continue;
            }
            logger.info("[CrateBox-Debug] iter " + (i + 1) + "/" + openCount + ": reward.id=" + reward.getId() + ", type=" + reward.getType() + ", class=" + reward.getClass().getName());
            if (reward.getType() != RewardType.ITEM) {
                logger.warning("[CrateBox-Debug]   ITEM 타입 아님 — 건너뜀");
                continue;
            }
            if (!(reward instanceof ItemReward)) {
                logger.warning("[CrateBox-Debug]   ItemReward 인스턴스 아님 (실제: " + reward.getClass().getName() + ") — 건너뜀");
                continue;
            }

            ItemReward itemReward = (ItemReward) reward;
            String rewardId = itemReward.getId();

            List<ItemProvider> providers = itemReward.getItems();
            logger.info("[CrateBox-Debug]   providers.size=" + (providers == null ? "null" : providers.size()));
            if (providers == null) continue;

            List<ItemStack> stacks = new ArrayList<>();
            for (ItemProvider p : providers) {
                ItemStack s = p.createItemStack();
                if (s == null) {
                    logger.warning("[CrateBox-Debug]   createItemStack null — 건너뜀");
                    continue;
                }
                stacks.add(s);

                String itemName;
                if (s.getItemMeta() != null && s.getItemMeta().hasDisplayName()) {
                    itemName = s.getItemMeta().getDisplayName();
                } else {
                    itemName = s.getType().name();
                }
                rewardSummary.merge(itemName, s.getAmount(), Integer::sum);
                logger.info("[CrateBox-Debug]   집계 — " + itemName + " x " + s.getAmount());
            }

            aggregatedStacks.computeIfAbsent(rewardId, k -> new ArrayList<>()).addAll(stacks);
        }

        logger.info("[CrateBox-Debug] rewardSummary.size=" + rewardSummary.size() + ", aggregatedStacks.size=" + aggregatedStacks.size());

        if (!rewardSummary.isEmpty()) {
            player.sendMessage(useItemDisplayName + "§f을(를) §e" + openCount + "§f개 소모하여 다음 보상을 획득하셨습니다:");
            for (Map.Entry<String, Integer> entry : rewardSummary.entrySet()) {
                player.sendMessage("§7- " + entry.getKey() + "§7 x §e" + entry.getValue());
            }
        } else {
            logger.warning("[CrateBox-Debug] rewardSummary 비어있음 — 보상 메시지 미발송");
            player.sendMessage("§c 보상이 비어 있습니다. 관리자에게 문의하세요.");
        }

        List<MailJob> mailJobs = new ArrayList<>();
        for (Map.Entry<String, List<ItemStack>> entry : aggregatedStacks.entrySet()) {
            mailJobs.add(new MailJob(entry.getValue(), entry.getKey()));
        }

        if (!mailJobs.isEmpty()) {
            final String playerName = player.getName();
            final String playerUuid = player.getUniqueId().toString();
            final int totalJobs = mailJobs.size();
            logger.info("[CrateBox-Debug] " + playerName + " 메일 발송 시작: box=" + box_id + ", crate=" + crateBox.getCrate_id() + ", 총 " + totalJobs + "개");

            for (int i = 0; i < mailJobs.size(); i++) {
                final MailJob job = mailJobs.get(i);
                final int seq = i + 1;
                Bukkit.getScheduler().runTaskLater(RandomBox.getInstance(), () -> {
                    try {
                        logger.info("[CrateBox-Debug] (" + seq + "/" + totalJobs + ") sendReward 시도 — rewardId=" + job.drawItemId + ", stacks=" + job.reward.size());
                        BoxUtils.sendReward(box_id, job.reward, player);
                        logger.info("[CrateBox-Debug] (" + seq + "/" + totalJobs + ") sendReward 완료");
                    } catch (Exception e) {
                        logger.warning("[CrateBox-Debug] (" + seq + "/" + totalJobs + ") sendReward 실패: " + e.getClass().getSimpleName() + " — " + e.getMessage());
                        e.printStackTrace();
                    }

                    Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () -> {
                        try {
                            ItemDrawLogDto dto = ItemDrawLogDto.builder()
                                    .user_id(playerName)
                                    .uuid(playerUuid)
                                    .usedItem(box_id)
                                    .drawItem(job.drawItemId)
                                    .build();
                            itemDrawRepository.insertDrawLog(dto, DateUtils.getCurrentTime());
                        } catch (Exception e) {
                            logger.warning("[CrateBox-Debug] (" + seq + "/" + totalJobs + ") insertDrawLog 실패: " + e.getClass().getSimpleName() + " — " + e.getMessage());
                            e.printStackTrace();
                        }
                    });

                    if (seq == totalJobs) {
                        logger.info("[CrateBox-Debug] " + playerName + " 메일 발송 종료 (총 " + totalJobs + "개)");
                    }
                }, i);
            }
        }
    }
}
