package org.desp.randomBox.boxUtils;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 크레이트 보상으로 나온 특수무기를 실물 대신 "특수무기 획득권" 아이템으로 치환하는 매퍼.
 * key: 공백 제거된 무기 이름 / value: 획득권 MMOItems ID
 */
public class SpecialWeaponTicketMapper {

    /** 획득권 MMOItems ID -> 지급할 특수무기 MMOItems ID (+0 강화) */
    private static final Map<String, String> TICKET_TO_WEAPON = new HashMap<>();

    private static final Map<String, String> WEAPON_TO_TICKET = new LinkedHashMap<>();
    private static final Map<String, Type> TICKET_TYPE_CACHE = new HashMap<>();
    private static final Map<String, Type> WEAPON_TYPE_CACHE = new HashMap<>();
    private static final List<String> KEYS_LONGEST_FIRST = new ArrayList<>();

    private static final String WEAPON_ID_PREFIX = "특수무기_";

    static {
        WEAPON_TO_TICKET.put("폭파광", "기타_특수무기획득권_폭파광");
        WEAPON_TO_TICKET.put("무한의포션", "기타_특수무기획득권_무한의포션");
        WEAPON_TO_TICKET.put("바람의검", "기타_특수무기획득권_바람의검");
        WEAPON_TO_TICKET.put("호루스의눈", "기타_특수무기획득권_호루스의눈");
        WEAPON_TO_TICKET.put("라이트소드", "기타_특수무기획득권_라이트소드");
        WEAPON_TO_TICKET.put("라이트스피어", "기타_특수무기획득권_라이트스피어");
        WEAPON_TO_TICKET.put("배틀댄서", "기타_특수무기획득권_배틀댄서");
        WEAPON_TO_TICKET.put("사이안스피어", "기타_특수무기획득권_사이안스피어");
        WEAPON_TO_TICKET.put("아이언헬버드", "기타_특수무기획득권_아이언헬버드");
        WEAPON_TO_TICKET.put("윈드시클", "기타_특수무기획득권_윈드시클");
        WEAPON_TO_TICKET.put("허리케인보우", "기타_특수무기획득권_허리케인보우");
        WEAPON_TO_TICKET.put("마나보우", "기타_특수무기획득권_마나보우");
        WEAPON_TO_TICKET.put("익스플로즈", "기타_특수무기획득권_익스플로즈");
        WEAPON_TO_TICKET.put("적수정검", "기타_특수무기획득권_적수정검");
        WEAPON_TO_TICKET.put("트릭오어트릿", "기타_특수무기획득권_트릭오어트릿");
        WEAPON_TO_TICKET.put("하드글래스스피어", "기타_특수무기획득권_하드글래스스피어");
        WEAPON_TO_TICKET.put("헤카테_화염", "기타_특수무기획득권_헤카테_화염");
        WEAPON_TO_TICKET.put("헤카테_얼음", "기타_특수무기획득권_헤카테_얼음");
        WEAPON_TO_TICKET.put("래피드보우", "기타_특수무기획득권_래피드보우");
        WEAPON_TO_TICKET.put("레드나이프", "기타_특수무기획득권_레드나이프");
        WEAPON_TO_TICKET.put("스톰사이드", "기타_특수무기획득권_스톰사이드");
        WEAPON_TO_TICKET.put("에너지소드", "기타_특수무기획득권_에너지소드");
        WEAPON_TO_TICKET.put("헤븐리해머", "기타_특수무기획득권_헤븐리해머");
        WEAPON_TO_TICKET.put("게브", "기타_특수무기획득권_게브");
        WEAPON_TO_TICKET.put("미트라", "기타_특수무기획득권_미트라");
        WEAPON_TO_TICKET.put("발두르", "기타_특수무기획득권_발두르");
        WEAPON_TO_TICKET.put("보이드사이드", "기타_특수무기획득권_보이드사이드");
        WEAPON_TO_TICKET.put("아이언피스트", "기타_특수무기획득권_아이언피스트");
        WEAPON_TO_TICKET.put("아이온", "기타_특수무기획득권_아이온");
        WEAPON_TO_TICKET.put("프로스트소드", "기타_특수무기획득권_프로스트소드");

        KEYS_LONGEST_FIRST.addAll(WEAPON_TO_TICKET.keySet());
        KEYS_LONGEST_FIRST.sort((a, b) -> b.length() - a.length());

        // 획득권 -> 특수무기 역방향. 지급은 항상 +0 강화 등급으로 한다.
        for (Map.Entry<String, String> entry : WEAPON_TO_TICKET.entrySet()) {
            TICKET_TO_WEAPON.put(entry.getValue(), WEAPON_ID_PREFIX + entry.getKey() + "0");
        }
    }

    /**
     * 특수무기 아이템이면 매칭되는 획득권 ItemStack을 반환, 아니면 null.
     * 수량은 원본과 동일하게 맞춘다.
     */
    public static ItemStack toTicket(ItemStack original) {
        if (original == null) return null;

        String ticketId = findTicketId(original);
        if (ticketId == null) return null;

        // 이미 획득권 자체인 경우 치환하지 않음
        String mmoId = MMOItems.getID(original);
        if (mmoId != null && mmoId.contains("특수무기획득권")) return null;

        ItemStack ticket = createTicket(ticketId);
        if (ticket == null) return null;

        ticket.setAmount(original.getAmount());
        return ticket;
    }

    /** 해당 MMOItems ID 가 등록된 특수무기 획득권인지 여부 */
    public static boolean isTicket(String mmoItemId) {
        return mmoItemId != null && TICKET_TO_WEAPON.containsKey(mmoItemId);
    }

    /**
     * 획득권 MMOItems ID 로 지급할 특수무기(+0) ItemStack 을 생성한다.
     * 등록되지 않은 획득권이거나 MMOItems 에 무기가 없으면 null.
     */
    public static ItemStack toWeapon(String ticketId) {
        String weaponId = TICKET_TO_WEAPON.get(ticketId);
        if (weaponId == null) return null;

        Type cached = WEAPON_TYPE_CACHE.get(weaponId);
        if (cached != null) {
            return MMOItems.plugin.getItem(cached, weaponId);
        }

        for (Type type : MMOItems.plugin.getTypes().getAll()) {
            ItemStack item = MMOItems.plugin.getItem(type, weaponId);
            if (item != null) {
                WEAPON_TYPE_CACHE.put(weaponId, type);
                return item;
            }
        }
        return null;
    }

    private static String findTicketId(ItemStack item) {
        String name = null;
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            name = item.getItemMeta().getDisplayName();
        }
        String normalizedName = normalize(name);
        String normalizedId = normalize(MMOItems.getID(item));

        for (String key : KEYS_LONGEST_FIRST) {
            if (normalizedName.contains(key) || normalizedId.contains(key)) {
                return WEAPON_TO_TICKET.get(key);
            }
        }
        return null;
    }

    private static ItemStack createTicket(String ticketId) {
        Type cached = TICKET_TYPE_CACHE.get(ticketId);
        if (cached != null) {
            return MMOItems.plugin.getItem(cached, ticketId);
        }

        for (Type type : MMOItems.plugin.getTypes().getAll()) {
            ItemStack item = MMOItems.plugin.getItem(type, ticketId);
            if (item != null) {
                TICKET_TYPE_CACHE.put(ticketId, type);
                return item;
            }
        }
        return null;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("§x(§[0-9a-fA-F]){6}", "")
                .replaceAll("§.", "")
                .replace(" ", "");
    }
}
