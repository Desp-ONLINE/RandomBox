package org.desp.randomBox.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.desp.randomBox.database.CeilingRepository;
import org.desp.randomBox.dto.CeilingDataDto;
import org.desp.randomBox.dto.DetailedCeilingDto;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

// 플레이어의 천장 진행 현황을 보여주는 읽기 전용 GUI.
// 커스텀 InventoryHolder 로 식별되어 CeilingGuiListener 에서 클릭이 차단된다.
public class CeilingGui implements InventoryHolder {

    public static final String TITLE = "§8천장 현황";

    private final Inventory inventory;

    public CeilingGui(HashMap<String, DetailedCeilingDto> playerCeilingData, CeilingRepository ceilingRepository) {
        int count = playerCeilingData.size();
        int rows = Math.max(1, Math.min(6, (count + 8) / 9)); // 9칸당 1줄, 최대 6줄
        this.inventory = Bukkit.createInventory(this, rows * 9, TITLE);

        // 천장 이름(글자) 기준 오름차순으로 정렬해 배치한다. (이름이 없으면 ceilingID 기준)
        List<String> ceilingIDs = new ArrayList<>(playerCeilingData.keySet());
        ceilingIDs.sort(Comparator.comparing(id -> {
            CeilingDataDto data = ceilingRepository.getCeilingData(id);
            return (data != null && data.getName() != null) ? data.getName() : id;
        }, String.CASE_INSENSITIVE_ORDER));

        for (String ceilingID : ceilingIDs) {
            CeilingDataDto ceilingData = ceilingRepository.getCeilingData(ceilingID);
            if (ceilingData == null) {
                continue;
            }
            if (inventory.firstEmpty() == -1) {
                break;
            }
            inventory.addItem(buildIcon(ceilingRepository, ceilingData, playerCeilingData.get(ceilingID)));
        }
    }

    private ItemStack buildIcon(CeilingRepository ceilingRepository, CeilingDataDto ceilingData, DetailedCeilingDto detail) {
        int goal = ceilingData.getAmount();
        int current = (detail != null && detail.getAmount() != null) ? detail.getAmount() : 0;
        int toReward = goal - current;
        boolean completed = toReward <= 0;

        // 아이콘은 실제 천장 보상 아이템(MMOItems)을 사용한다. 첫 번째 보상을 아이콘으로 쓴다.
        List<ItemStack> rewards = ceilingRepository.buildRewardItems(ceilingData.getCeilingID(), 1);

        ItemStack item;
        if (!rewards.isEmpty()) {
            item = rewards.get(0).clone();
            item.setAmount(1); // 아이콘은 1개로 고정, 실제 수량은 로어에 표기
        } else {
            // 보상을 불러오지 못하면 기본 아이콘으로 폴백.
            Material material = (completed && !ceilingData.isVolatile()) ? Material.GRAY_DYE : Material.ENCHANTED_BOOK;
            item = new ItemStack(material);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + ceilingData.getName());

            List<String> lore = new ArrayList<>();
            lore.add("§7진행도 §f: §e" + current + " §7/ §e" + goal);
            if (completed) {
                lore.add("§c보상을 이미 획득하여 더 이상 획득할 수 없습니다.");
            } else {
                lore.add("§f보상 획득까지 §e" + toReward + "회 §f남았습니다.");
            }
            lore.add("§f");

            if (!rewards.isEmpty()) {
                lore.add("§7천장 보상");
                for (ItemStack reward : rewards) {
                    ItemMeta rewardMeta = reward.getItemMeta();
                    String rewardName = (rewardMeta != null && rewardMeta.hasDisplayName())
                            ? rewardMeta.getDisplayName()
                            : reward.getType().name();
                    lore.add("  §e- " + rewardName + " §7x" + reward.getAmount());
                }
                lore.add("§f");
            }

            lore.add(ceilingData.isVolatile() ? "§a( 반복 가능 )" : "§c( 반복 불가능 )");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
