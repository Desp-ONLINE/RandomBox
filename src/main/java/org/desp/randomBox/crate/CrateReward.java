package org.desp.randomBox.crate;

import lombok.Getter;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.kyori.adventure.text.Component;
import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 상자 보상 하나. EC와 달리 아이템을 NBT로 들고 있지 않고 MMOItems 타입과 ID로 매번 새로 만든다.
 * Mongo 형식 : { reward_id, weight, rarity, broadcast, items: [ { type, item_id, amount } ] }
 */
@Getter
public class CrateReward {

    private final String id;
    private final double weight;
    private final String rarity;
    private final boolean broadcast;
    private final List<RewardItem> items;

    public CrateReward(String id, double weight, String rarity, boolean broadcast, List<RewardItem> items) {
        this.id = id;
        this.weight = weight;
        this.rarity = rarity;
        this.broadcast = broadcast;
        this.items = items;
    }

    @Getter
    public static class RewardItem {
        private final String type;
        private final String itemId;
        private final int amount;

        public RewardItem(String type, String itemId, int amount) {
            this.type = type;
            this.itemId = itemId;
            this.amount = amount;
        }

        /** MMOItems 아이템을 만든다. 타입이나 ID가 잘못되어 있으면 null. */
        public ItemStack create() {
            Type mmoType = Type.get(type);
            if (mmoType == null) {
                return null;
            }
            ItemStack item = MMOItems.plugin.getItem(mmoType, itemId);
            if (item == null) {
                return null;
            }
            item.setAmount(Math.max(1, amount));
            return item;
        }

        public Document toDocument() {
            return new Document("type", type).append("item_id", itemId).append("amount", amount);
        }

        public static RewardItem fromDocument(Document document) {
            Number amount = document.get("amount", Number.class);
            return new RewardItem(document.getString("type"), document.getString("item_id"), amount == null ? 1 : amount.intValue());
        }
    }

    /** 지급할 아이템 목록. 만들 수 없는 아이템은 빠진다. */
    public List<ItemStack> createItems() {
        List<ItemStack> result = new ArrayList<>();
        for (RewardItem item : items) {
            ItemStack stack = item.create();
            if (stack != null) {
                result.add(stack);
            }
        }
        return result;
    }

    /** 미리보기와 애니메이션에 보여 줄 대표 아이템. */
    public ItemStack previewItem() {
        for (RewardItem item : items) {
            ItemStack stack = item.create();
            if (stack != null) {
                return stack;
            }
        }
        return new ItemStack(Material.BARRIER);
    }

    /** 보상 이름. EC처럼 대표 아이템 이름을 쓴다. */
    public Component displayName() {
        return CrateText.itemName(previewItem());
    }

    public Document toDocument() {
        List<Document> itemDocs = new ArrayList<>();
        for (RewardItem item : items) {
            itemDocs.add(item.toDocument());
        }
        return new Document("reward_id", id)
                .append("weight", weight)
                .append("rarity", rarity)
                .append("broadcast", broadcast)
                .append("items", itemDocs);
    }

    @SuppressWarnings("unchecked")
    public static CrateReward fromDocument(Document document) {
        List<RewardItem> items = new ArrayList<>();
        List<Document> itemDocs = (List<Document>) document.get("items");
        if (itemDocs != null) {
            for (Document itemDoc : itemDocs) {
                items.add(RewardItem.fromDocument(itemDoc));
            }
        }
        Number weight = document.get("weight", Number.class);
        String rarity = document.getString("rarity");
        return new CrateReward(
                document.getString("reward_id"),
                weight == null ? 0 : weight.doubleValue(),
                rarity == null ? "common" : rarity,
                Boolean.TRUE.equals(document.getBoolean("broadcast")),
                items);
    }
}
