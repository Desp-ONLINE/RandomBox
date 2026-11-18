package org.desp.randomBox.crate.gui;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.desp.randomBox.crate.Crate;
import org.desp.randomBox.crate.CrateItems;
import org.desp.randomBox.crate.CrateManager;
import org.desp.randomBox.crate.CrateMilestone;
import org.desp.randomBox.crate.CrateReward;
import org.desp.randomBox.crate.CrateText;
import org.desp.randomBox.crate.CrateUserRepository;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 누적 오픈 보상(마일스톤) 화면. EC milestones.yml 배치를 그대로 따른다. */
public class CrateMilestoneGui implements InventoryHolder {

    private static final String SKIN_RETURN = "be9ae7a4be65fcbaee65181389a2f7d47e2e326db59ea3eb789a92c85ea46";
    private static final String SKIN_NEXT = "f32ca66056b72863e98f7f32bd7d94c7a0d796af691c9ac3a9136331352288f9";
    private static final String SKIN_PREVIOUS = "86971dd881dbaf4fd6bcaa93614493c612f869641ed59d1c9363a3666a5fa6";

    @Getter
    private final Crate crate;
    @Getter
    private final int page;
    @Getter
    private final int maxPage;
    @Getter
    private final int returnSlot;
    @Getter
    private final int nextSlot;
    @Getter
    private final int previousSlot;
    private final Inventory inventory;

    private CrateMilestoneGui(Player player, Crate crate, int requestedPage) {
        this.crate = crate;
        YamlConfiguration config = CrateManager.getInstance().getConfig();
        String base = "milestone-menu.";

        List<Integer> slots = config.getIntegerList(base + "slots");
        List<CrateMilestone> milestones = crate.getMilestones();
        int perPage = Math.max(1, slots.size());
        this.maxPage = Math.max(1, (milestones.size() + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(requestedPage, maxPage - 1));
        this.returnSlot = config.getInt(base + "return-slot", 22);
        this.nextSlot = config.getInt(base + "next-slot", 17);
        this.previousSlot = config.getInt(base + "previous-slot", 9);

        int rows = Math.max(1, Math.min(6, config.getInt(base + "rows", 3)));
        this.inventory = Bukkit.createInventory(this, rows * 9, CrateText.mm(config.getString(base + "title", "<black>보상</black>")));

        fill(config.getIntegerList(base + "black-pane-slots"), CrateItems.pane(Material.BLACK_STAINED_GLASS_PANE));
        fill(config.getIntegerList(base + "gray-pane-slots"), CrateItems.pane(Material.GRAY_STAINED_GLASS_PANE));
        fill(config.getIntegerList(base + "white-pane-slots"), CrateItems.pane(Material.WHITE_STAINED_GLASS_PANE));

        int progress = CrateUserRepository.getInstance().get(player, crate.getId()).milestone;
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < milestones.size(); i++) {
            CrateMilestone milestone = milestones.get(start + i);
            set(slots.get(i), milestoneIcon(config, milestone, progress));
        }

        set(returnSlot, CrateItems.head(SKIN_RETURN, CrateText.mm("<lgray>돌아가기</lgray>"), null));
        if (page + 1 < maxPage) {
            set(nextSlot, CrateItems.head(SKIN_NEXT, CrateText.mm("<lgray>다음 페이지 →</lgray>"), null));
        }
        if (page > 0) {
            set(previousSlot, CrateItems.head(SKIN_PREVIOUS, CrateText.mm("<lgray>← 이전 페이지</lgray>"), null));
        }
    }

    private ItemStack milestoneIcon(YamlConfiguration config, CrateMilestone milestone, int progress) {
        boolean completed = progress >= milestone.getOpenings();
        String section = "milestone-menu." + (completed ? "completed" : "incompleted");
        CrateReward reward = crate.getReward(milestone.getRewardId());
        Component rewardName = reward == null ? Component.text(milestone.getRewardId()) : reward.displayName();

        Component name = CrateText.mm(config.getString(section + ".name", ""),
                CrateText.unparsed("openings", String.valueOf(milestone.getOpenings())));
        List<Component> lore = CrateText.mm(config.getStringList(section + ".lore"),
                CrateText.unparsed("openings_left", String.valueOf(Math.max(0, milestone.getOpenings() - progress))),
                CrateText.component("reward_name", rewardName));

        String skin = config.getString(section + ".skin");
        if (skin != null && !skin.isEmpty()) {
            return CrateItems.head(skin, name, lore);
        }
        ItemStack item = reward == null ? new ItemStack(Material.CHEST) : reward.previewItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fill(List<Integer> slots, ItemStack item) {
        for (int slot : slots) {
            set(slot, item);
        }
    }

    private void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    public static void open(Player player, Crate crate, int page) {
        player.openInventory(new CrateMilestoneGui(player, crate, page).getInventory());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
