package org.desp.randomBox.crate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 상자 시스템 문자열 처리 도구.
 * EC(nightcore)에서 쓰던 색상 태그(lyellow, lgray 등)를 같은 색으로 해석해 기존 화면과 똑같이 보이게 한다.
 */
public final class CrateText {

    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("black", "#000000"),
            Map.entry("white", "#ffffff"),
            Map.entry("gray", "#aaa8a8"),
            Map.entry("green", "#74ea31"),
            Map.entry("yellow", "#ead931"),
            Map.entry("orange", "#ea9631"),
            Map.entry("red", "#ea3131"),
            Map.entry("blue", "#3196ea"),
            Map.entry("cyan", "#31eace"),
            Map.entry("purple", "#bd31ea"),
            Map.entry("pink", "#ea31b2"),
            Map.entry("dgray", "#6c6c62"),
            Map.entry("lgray", "#d4d9d8"),
            Map.entry("lgreen", "#91f251"),
            Map.entry("lyellow", "#ffeea2"),
            Map.entry("lorange", "#fdba5e"),
            Map.entry("lred", "#fd5e5e"),
            Map.entry("lblue", "#5e9dfd"),
            Map.entry("lcyan", "#5edefd"),
            Map.entry("lpurple", "#e39fff")
    );

    private static final MiniMessage MINI;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    static {
        // 같은 이름의 태그는 먼저 등록한 쪽이 우선하므로 EC 색상을 표준 태그보다 앞에 둔다.
        TagResolver.Builder builder = TagResolver.builder();
        for (Map.Entry<String, String> entry : COLORS.entrySet()) {
            builder.resolver(TagResolver.resolver(entry.getKey(), Tag.styling(TextColor.fromHexString(entry.getValue()))));
        }
        builder.resolver(TagResolver.standard());
        MINI = MiniMessage.builder().tags(builder.build()).build();
    }

    private CrateText() {
    }

    /** MiniMessage 문자열을 컴포넌트로 바꾼다. 아이템 이름처럼 기울임이 기본인 곳에서 쓰도록 기울임을 끈다. */
    public static Component mm(String text, TagResolver... resolvers) {
        if (text == null) {
            return Component.empty();
        }
        return MINI.deserialize(text, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> mm(List<String> lines, TagResolver... resolvers) {
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            result.add(mm(line, resolvers));
        }
        return result;
    }

    /** §코드 문자열(MMOItems 아이템 이름 등)을 컴포넌트로 바꾼다. */
    public static Component legacy(String text) {
        if (text == null) {
            return Component.empty();
        }
        return LEGACY.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String toLegacy(Component component) {
        return LEGACY.serialize(component);
    }

    public static TagResolver component(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public static TagResolver parsed(String key, String value) {
        return Placeholder.parsed(key, value);
    }

    public static TagResolver unparsed(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    /** 아이템 표시 이름. 이름이 없으면 재료 이름을 쓴다. */
    /** 색과 꾸밈을 뺀 글자만 돌려준다. */
    public static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static Component itemName(ItemStack item) {
        if (item == null) {
            return Component.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                return name;
            }
        }
        return Component.translatable(item.getType().translationKey());
    }

    /** 받침 유무에 맞는 목적격 조사(을/를)를 붙인다. */
    public static String withObjectParticle(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        char last = word.charAt(word.length() - 1);
        if (last >= 0xAC00 && last <= 0xD7A3) {
            boolean hasFinal = (last - 0xAC00) % 28 != 0;
            return word + (hasFinal ? "을" : "를");
        }
        return word + "을(를)";
    }

    /** EC와 같은 방식으로 소수점 둘째 자리까지 확률을 표기한다. */
    public static String formatChance(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        String text = String.format("%.2f", rounded);
        if (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
