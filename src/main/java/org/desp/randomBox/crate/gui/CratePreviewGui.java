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
import org.desp.randomBox.crate.CrateRarity;
import org.desp.randomBox.crate.CrateReward;
import org.desp.randomBox.crate.CrateText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 상자 보상 미리보기. EC previews/default.yml 배치를 그대로 따른다. */
public class CratePreviewGui implements InventoryHolder {

    private static final String SKIN_MILESTONE = "1daf09284530ce92ed2df2a62e1b05a11f1871f85ae559042844206d66c0b5b0";
    private static final String SKIN_EXIT = "27548362a24c0fa8453e4d93e68c5969ddbde57bf6666c0319c1ed1e84d89065";
    private static final String SKIN_NEXT = "f32ca66056b72863e98f7f32bd7d94c7a0d796af691c9ac3a9136331352288f9";
    private static final String SKIN_PREVIOUS = "86971dd881dbaf4fd6bcaa93614493c612f869641ed59d1c9363a3666a5fa6";

    @Getter
    private final Crate crate;
    @Getter
    private final int page;
    @Getter
    private final int maxPage;
    private final Inventory inventory;

    @Getter
    private final int milestoneSlot;
    @Getter
    private final int exitSlot;
    @Getter
    private final int nextSlot;
    @Getter
    private final int previousSlot;

    private CratePreviewGui(Crate crate, int requestedPage) {
        this.crate = crate;
        YamlConfiguration config = CrateManager.getInstance().getConfig();
        Map<String, CrateRarity> rarities = CrateManager.getInstance().getRarities();

        List<Integer> rewardSlots = config.getIntegerList("preview.reward-slots");
        List<CrateReward> rewards = new ArrayList<>(crate.getRewards().values());
        int perPage = Math.max(1, rewardSlots.size());
        this.maxPage = Math.max(1, (rewards.size() + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(requestedPage, maxPage - 1));

        this.milestoneSlot = config.getInt("preview.milestone-slot", 4);
        this.exitSlot = config.getInt("preview.exit-slot", 40);
        this.nextSlot = config.getInt("preview.next-slot", 26);
        this.previousSlot = config.getInt("preview.previous-slot", 18);

        int rows = Math.max(1, Math.min(6, config.getInt("preview.rows", 5)));
        this.inventory = Bukkit.createInventory(this, rows * 9,
                CrateText.mm("<black><crate_name></black>", CrateText.parsed("crate_name", crate.getName())));

        ItemStack black = CrateItems.pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack gray = CrateItems.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot : config.getIntegerList("preview.black-pane-slots")) {
            set(slot, black);
        }
        for (int slot : config.getIntegerList("preview.gray-pane-slots")) {
            set(slot, gray);
        }

        List<String> loreTemplate = config.getStringList("preview.lore." + crate.getPreviewId());
        if (loreTemplate.isEmpty()) {
            loreTemplate = config.getStringList("preview.lore.default");
        }
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < rewards.size(); i++) {
            CrateReward reward = rewards.get(start + i);
            set(rewardSlots.get(i), rewardIcon(crate, reward, rarities, loreTemplate));
        }

        if (crate.hasMilestones()) {
            set(milestoneSlot, CrateItems.head(SKIN_MILESTONE, CrateText.mm("<lyellow>오픈 보상 확인하기</lyellow>"), null));
        }
        set(exitSlot, CrateItems.head(SKIN_EXIT, CrateText.mm("<lred>나가기</lred>"), null));
        if (page + 1 < maxPage) {
            set(nextSlot, CrateItems.head(SKIN_NEXT, CrateText.mm("<lgray>다음 페이지 →</lgray>"), null));
        }
        if (page > 0) {
            set(previousSlot, CrateItems.head(SKIN_PREVIOUS, CrateText.mm("<lgray>← 이전 페이지</lgray>"), null));
        }
    }

    private static ItemStack rewardIcon(Crate crate, CrateReward reward, Map<String, CrateRarity> rarities, List<String> loreTemplate) {
        ItemStack item = reward.previewItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        CrateRarity rarity = rarities.get(reward.getRarity().toLowerCase());
        String rarityName = rarity == null ? reward.getRarity() : rarity.getName();
        lore.addAll(CrateText.mm(loreTemplate,
                CrateText.unparsed("chance", CrateText.formatChance(crate.rewardChance(reward, rarities))),
                CrateText.parsed("rarity_name", rarityName),
                CrateText.unparsed("rarity_chance", CrateText.formatChance(crate.rarityChance(reward.getRarity(), rarities)))));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    public static void open(Player player, Crate crate, int page) {
        player.openInventory(new CratePreviewGui(crate, page).getInventory());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
