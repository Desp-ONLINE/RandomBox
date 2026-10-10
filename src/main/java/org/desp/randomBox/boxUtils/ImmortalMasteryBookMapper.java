package org.desp.randomBox.boxUtils;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 불멸고서(기타_불멸고서_<직업>1) 에서 불멸 마스터리북(마스터리북_<직업>_불멸1)이 나오면
 * 레전더리 마스터리북(마스터리북_<직업>_레전더리1)을 추가로 지급한다.
 */
public class ImmortalMasteryBookMapper {

    private static final Pattern BOX_PATTERN = Pattern.compile("^기타_불멸고서_(.+)1$");
    private static final Pattern BOOK_PATTERN = Pattern.compile("^마스터리북_(.+)_불멸1$");

    private static final Map<String, Type> TYPE_CACHE = new HashMap<>();

    /**
     * 불멸고서에서 나온 불멸 마스터리북이면 추가 지급할 레전더리 마스터리북 ItemStack 을 반환, 아니면 null.
     * 수량은 원본과 동일하게 맞춘다.
     */
    public static ItemStack bonusFor(String boxItemId, ItemStack original) {
        if (boxItemId == null || original == null) return null;
        if (!BOX_PATTERN.matcher(boxItemId).matches()) return null;

        String rewardId = MMOItems.getID(original);
        if (rewardId == null) return null;

        Matcher matcher = BOOK_PATTERN.matcher(rewardId);
        if (!matcher.matches()) return null;

        ItemStack legendary = createItem("마스터리북_" + matcher.group(1) + "_레전더리1");
        if (legendary == null) return null;

        legendary.setAmount(original.getAmount());
        return legendary;
    }

    private static ItemStack createItem(String itemId) {
        Type cached = TYPE_CACHE.get(itemId);
        if (cached != null) {
            return MMOItems.plugin.getItem(cached, itemId);
        }

        for (Type type : MMOItems.plugin.getTypes().getAll()) {
            ItemStack item = MMOItems.plugin.getItem(type, itemId);
            if (item != null) {
                TYPE_CACHE.put(itemId, type);
                return item;
            }
        }
        return null;
    }
}
