package org.desp.randomBox.listener;

import com.binggre.binggreapi.utils.ColorManager;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.boxUtils.BoxUtils;
import org.desp.randomBox.boxUtils.DateUtils;
import org.desp.randomBox.boxUtils.ImmortalMasteryBookMapper;
import org.desp.randomBox.boxUtils.SpecialWeaponTicketMapper;
import org.desp.randomBox.crate.Crate;
import org.desp.randomBox.crate.CrateManager;
import org.desp.randomBox.crate.CrateOpenService;
import org.desp.randomBox.crate.CrateReward;
import org.desp.randomBox.crate.CrateUserRepository;
import org.desp.randomBox.crate.QuestCompat;
import org.desp.randomBox.crate.opening.OpeningSession;
import org.desp.randomBox.database.CrateBoxRepository;
import org.desp.randomBox.database.ItemDrawRepository;
import org.desp.randomBox.database.SpecialWeaponStorageRepository;
import org.desp.randomBox.dto.CrateBoxDto;
import org.desp.randomBox.dto.ItemDrawLogDto;
import org.dople.dataSync.inventory.InventorySyncListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 상자 아이템(CrateBoxData) 또는 상자 열쇠(CrateData.key_item)를 들고 쉬프트 우클릭하면
 * 연결된 상자를 손에 든 개수만큼(mass-open-limit 까지) 한번에 연다.
 */
public class CrateBoxListener implements Listener {

    public CrateBoxRepository crateBoxRepository;
    public ItemDrawRepository itemDrawRepository;

    public CrateBoxListener() {
        this.crateBoxRepository = CrateBoxRepository.getInstance();
        this.itemDrawRepository = ItemDrawRepository.getInstance();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
        if (itemInMainHand.getType() == Material.AIR) {
            return;
        }

        String playerRightHandItemID = MMOItems.getID(itemInMainHand);
        if (playerRightHandItemID == null) return;

        // 상자 아이템이면 CrateBoxData 로, 아니면 상자 열쇠인지 CrateData.key_item 으로 찾는다.
        CrateBoxDto crateBox = crateBoxRepository.get(playerRightHandItemID);
        Crate keyCrate = crateBox == null ? CrateManager.getInstance().getCrateByKey(playerRightHandItemID) : null;
        if (crateBox == null && keyCrate == null) return;

        if (!player.isSneaking()) return;

        // 상자 블록을 누른 경우는 블록 상자(CrateBlockListener)가 처리한다.
        if (event.getClickedBlock() != null && CrateManager.getInstance().isBlockCrateEnabled()
                && CrateManager.getInstance().getCrateAt(event.getClickedBlock()) != null) return;
        event.setCancelled(true);

        if (InventorySyncListener.isDataLoading(player) || !CrateUserRepository.getInstance().isLoaded(player)) {
            player.sendMessage("§c 데이터가 로드중입니다.");
            return;
        }
        if (CrateManager.isExcellentCratesActive()) {
            player.sendMessage("§c 상자 시스템을 옮기는 중이라 지금은 열 수 없습니다. 잠시 후에 다시 시도해 주세요.");
            return;
        }
        if (OpeningSession.isOpening(player)) {
            return;
        }

        String crateId = crateBox != null ? crateBox.getCrate_id() : keyCrate.getId();
        Crate crate = keyCrate != null ? keyCrate : CrateManager.getInstance().getCrate(crateId);
        if (crate == null) {
            player.sendMessage("§c 연결된 상자(" + crateId + ")를 찾을 수 없습니다. 관리자에게 문의해 주세요.");
            return;
        }
        // 열쇠로 여는 경우는 블록 상자와 같은 퀘스트 조건을 건다. IDEQuest 가 없는 서버는 확인하지 않는다.
        if (keyCrate != null && crate.getRequiredQuest() > 0 && Bukkit.getPluginManager().isPluginEnabled("IDEQuest")
                && !QuestCompat.hasQuestData(player, crate.getRequiredQuest())) {
            player.sendMessage("§c  " + (crate.getRequiredQuest() - 1) + "번 메인 퀘스트를 먼저 클리어하세요!");
            return;
        }

        // 강제 보상과 뽑기 로그에 쓰는 ID. 상자 아이템은 박스ID, 열쇠는 상자ID다.
        String box_id = crateBox != null ? crateBox.getBox_id() : crate.getId();
        String useItemDisplayName = itemInMainHand.getItemMeta().getDisplayName();
        final Logger logger = RandomBox.getInstance().getLogger();

        // 손에 든 개수만큼 뽑는다. 관리자가 강제 지정한 보상이 있으면 그것을 먼저 쓴다(1회 소모).
        int limit = Math.max(1, CrateManager.getInstance().getConfig().getInt("mass-open-limit", 64));
        int count = Math.min(itemInMainHand.getAmount(), limit);
        List<CrateReward> rewards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CrateReward reward = CrateOpenService.roll(player, crate, box_id);
            if (reward == null) {
                break;
            }
            rewards.add(reward);
        }
        if (rewards.isEmpty()) {
            player.sendMessage("§c 보상이 비어 있습니다. 관리자에게 문의하세요.");
            return;
        }

        itemInMainHand.setAmount(itemInMainHand.getAmount() - rewards.size());

        CrateOpenService.progressMilestones(player, crate, rewards.size());
        Runnable deliver = () -> deliver(player, box_id, playerRightHandItemID, useItemDisplayName, rewards, logger);
        if (rewards.size() == 1) {
            CrateOpenService.present(player, crate, rewards.get(0), deliver);
        } else {
            deliver.run();
        }
    }

    private void deliver(Player player, String box_id, String usedItemId, String useItemDisplayName,
                         List<CrateReward> rewards, Logger logger) {
        Map<String, Integer> rewardSummary = new LinkedHashMap<>();
        List<ItemStack> stacks = new ArrayList<>();
        boolean storedSpecialWeapon = false;

        List<ItemStack> created = new ArrayList<>();
        for (CrateReward reward : rewards) {
            created.addAll(reward.createItems());
        }

        for (ItemStack s : created) {
            ItemStack ticket = SpecialWeaponTicketMapper.toTicket(s);
            if (ticket != null) {
                s = ticket;
            }

            List<ItemStack> given = new ArrayList<>();
            given.add(s);
            ItemStack bonusBook = ImmortalMasteryBookMapper.bonusFor(usedItemId, s);
            if (bonusBook != null) {
                given.add(bonusBook);
            }

            for (ItemStack g : given) {
                // 특수무기 획득권은 실물 대신 EtCetera 특수무기 보관함(/특수무기)에 개수로 적립한다.
                String storageName = SpecialWeaponTicketMapper.storageNameOf(MMOItems.getID(g));
                if (storageName != null) {
                    SpecialWeaponStorageRepository.getInstance().add(
                            player.getUniqueId().toString(), player.getName(), storageName, g.getAmount());
                    storedSpecialWeapon = true;
                } else {
                    stacks.add(g);
                }

                String itemName = g.getItemMeta() != null && g.getItemMeta().hasDisplayName()
                        ? g.getItemMeta().getDisplayName() : g.getType().name();
                rewardSummary.merge(itemName, g.getAmount(), Integer::sum);
            }
        }

        if (!rewardSummary.isEmpty()) {
            player.sendMessage(useItemDisplayName + "§f을(를) §e" + rewards.size() + "§f개 소모하여 다음 보상을 획득하셨습니다:");
            for (Map.Entry<String, Integer> entry : rewardSummary.entrySet()) {
                player.sendMessage("§7- " + entry.getKey() + "§7 x §e" + entry.getValue());
            }
        } else {
            logger.warning("[CrateBox] " + player.getName() + " 보상 아이템을 만들지 못했습니다: box=" + box_id);
            player.sendMessage("§c 보상이 비어 있습니다. 관리자에게 문의하세요.");
        }
        if (storedSpecialWeapon) {
            player.sendMessage(ColorManager.format("#FFD3A8[특수무기] #A8E6B8획득한 특수무기는 보관함에 보관되었습니다. §7(/특수무기)"));
        }

        if (!stacks.isEmpty()) {
            try {
                BoxUtils.sendCrateReward(useItemDisplayName, stacks, player);
            } catch (Exception e) {
                logger.warning("[CrateBox] " + player.getName() + " 메일 발송 실패: " + e.getClass().getSimpleName() + " " + e.getMessage());
                e.printStackTrace();
            }
        }

        final String playerName = player.getName();
        final String playerUuid = player.getUniqueId().toString();
        final List<String> drawItemIds = new ArrayList<>();
        for (CrateReward reward : rewards) {
            drawItemIds.add(reward.getId());
        }
        CrateOpenService.runAsync(() -> {
            for (String drawItemId : drawItemIds) {
                try {
                    ItemDrawLogDto dto = ItemDrawLogDto.builder()
                            .user_id(playerName)
                            .uuid(playerUuid)
                            .usedItem(box_id)
                            .drawItem(drawItemId)
                            .build();
                    itemDrawRepository.insertDrawLog(dto, DateUtils.getCurrentTime());
                } catch (Exception e) {
                    logger.warning("[CrateBox] insertDrawLog 실패: " + e.getClass().getSimpleName() + " " + e.getMessage());
                }
            }
        });
    }
}
