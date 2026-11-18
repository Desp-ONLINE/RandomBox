package org.desp.randomBox.crate.opening;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 인벤토리 오픈 애니메이션 설정. EC openings/inventory/*.yml 형식을 그대로 읽는다.
 *
 * Settings.Menu_Type        generic_9xN
 * Settings.Title            MiniMessage, <crate_name> 또는 %crate_name% 치환
 * Settings.WinSlots         실제 보상이 멈추는 칸
 * Settings.Completion_Pause_Ticks  모든 회전이 끝난 뒤 닫히기까지의 대기 틱
 * Settings.RunOnLaunch.ANIMATION / REWARD   회전 묶음
 * Spinners.ANIMATION.<id>.Items             장식 아이템 후보
 * Spinners.REWARD.<id>.Rarities             보상 후보 등급('*' 은 전체)
 * Content.Default.<id>                      고정 장식 아이템
 */
@Getter
public class OpeningConfig {

    public enum Mode { INDEPENDENT, SEQUENTIAL, SYNCHRONIZED }

    @Getter
    public static class SpinnerRun {
        private final boolean reward;
        private final String spinnerId;
        private final Mode mode;
        private final int[] slots;
        private final int delay;
        /** {횟수, 간격틱} 목록 */
        private final List<int[]> spins;
        private final String sound;
        private final float volume;
        private final float pitch;

        SpinnerRun(boolean reward, String spinnerId, Mode mode, int[] slots, int delay, List<int[]> spins,
                   String sound, float volume, float pitch) {
            this.reward = reward;
            this.spinnerId = spinnerId;
            this.mode = mode;
            this.slots = slots;
            this.delay = delay;
            this.spins = spins;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }

        public int totalSpins() {
            int total = 0;
            for (int[] spin : spins) {
                total += spin[0];
            }
            return total;
        }
    }

    @Getter
    public static class DecorItem {
        private final double chance;
        private final Material material;
        private final int amount;
        private final String displayName;
        private final List<String> lore;
        private final String skin;

        DecorItem(double chance, Material material, int amount, String displayName, List<String> lore, String skin) {
            this.chance = chance;
            this.material = material;
            this.amount = amount;
            this.displayName = displayName;
            this.lore = lore;
            this.skin = skin;
        }
    }

    @Getter
    public static class StaticContent {
        private final DecorItem item;
        private final int[] slots;

        StaticContent(DecorItem item, int[] slots) {
            this.item = item;
            this.slots = slots;
        }
    }

    private String id;
    private int size;
    private String title;
    private int[] winSlots;
    private int completionPause;
    private final List<SpinnerRun> runs = new ArrayList<>();
    private final Map<String, List<DecorItem>> animationSpinners = new HashMap<>();
    /** 보상 회전 id -> 허용 등급 목록('*' 포함 가능) */
    private final Map<String, List<String>> rewardSpinners = new HashMap<>();
    private final List<StaticContent> contents = new ArrayList<>();

    public static OpeningConfig load(String id, YamlConfiguration yml) {
        OpeningConfig config = new OpeningConfig();
        config.id = id;

        String menuType = yml.getString("Settings.Menu_Type", "generic_9x3").toLowerCase();
        int rows = 3;
        int index = menuType.lastIndexOf('x');
        if (index >= 0) {
            try {
                rows = Integer.parseInt(menuType.substring(index + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        config.size = Math.max(1, Math.min(6, rows)) * 9;
        config.title = yml.getString("Settings.Title", "<black><crate_name></black>");
        config.winSlots = parseSlots(yml.getString("Settings.WinSlots", "13"));
        config.completionPause = yml.getInt("Settings.Completion_Pause_Ticks", 20);

        for (String type : new String[]{"ANIMATION", "REWARD"}) {
            ConfigurationSection section = yml.getConfigurationSection("Settings.RunOnLaunch." + type);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection run = section.getConfigurationSection(key);
                if (run == null) {
                    continue;
                }
                List<int[]> spins = new ArrayList<>();
                for (String spin : run.getStringList("Spins")) {
                    String[] split = spin.split(":");
                    try {
                        int times = Integer.parseInt(split[0].trim());
                        int interval = split.length > 1 ? Integer.parseInt(split[1].trim()) : 1;
                        spins.add(new int[]{times, Math.max(1, interval)});
                    } catch (NumberFormatException ignored) {
                    }
                }
                String sound = null;
                float volume = 1f;
                float pitch = 1f;
                String soundRaw = run.getString("Sound");
                if (soundRaw != null && !soundRaw.isEmpty()) {
                    String[] split = soundRaw.split(";");
                    sound = split[0].trim();
                    try {
                        if (split.length > 1) volume = Float.parseFloat(split[1]);
                        if (split.length > 2) pitch = Float.parseFloat(split[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                config.runs.add(new SpinnerRun(type.equals("REWARD"),
                        run.getString("SpinnerId", "default"),
                        parseMode(run.getString("Mode", "INDEPENDENT")),
                        parseSlots(run.getString("Slots", "")),
                        run.getInt("SpinDelay", 0),
                        spins, sound, volume, pitch));
            }
        }

        ConfigurationSection animationSection = yml.getConfigurationSection("Spinners.ANIMATION");
        if (animationSection != null) {
            for (String spinnerId : animationSection.getKeys(false)) {
                List<DecorItem> items = new ArrayList<>();
                ConfigurationSection itemSection = animationSection.getConfigurationSection(spinnerId + ".Items");
                if (itemSection != null) {
                    for (String itemKey : itemSection.getKeys(false)) {
                        ConfigurationSection item = itemSection.getConfigurationSection(itemKey);
                        if (item != null) {
                            items.add(readItem(item, item.getDouble("Chance", 1.0)));
                        }
                    }
                }
                config.animationSpinners.put(spinnerId, items);
            }
        }

        ConfigurationSection rewardSection = yml.getConfigurationSection("Spinners.REWARD");
        if (rewardSection != null) {
            for (String spinnerId : rewardSection.getKeys(false)) {
                config.rewardSpinners.put(spinnerId, rewardSection.getStringList(spinnerId + ".Rarities"));
            }
        }

        ConfigurationSection contentSection = yml.getConfigurationSection("Content.Default");
        if (contentSection != null) {
            for (String key : contentSection.getKeys(false)) {
                ConfigurationSection item = contentSection.getConfigurationSection(key + ".Item");
                if (item == null) {
                    continue;
                }
                config.contents.add(new StaticContent(readItem(item, 1.0),
                        parseSlots(contentSection.getString(key + ".Slots", ""))));
            }
        }
        return config;
    }

    private static DecorItem readItem(ConfigurationSection section, double chance) {
        String materialName = section.getString("Material", "minecraft:air").toLowerCase();
        if (materialName.startsWith("minecraft:")) {
            materialName = materialName.substring("minecraft:".length());
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.AIR;
        }
        String name = section.getString("Display_Name", section.getString("Name"));
        return new DecorItem(chance, material, section.getInt("Amount", 1), name,
                section.getStringList("Lore"), section.getString("SkinURL"));
    }

    private static Mode parseMode(String raw) {
        String value = raw.toUpperCase();
        // EC 설정 파일의 오타(SEQUENTAL, SYNCRHONIZED)도 그대로 받는다.
        if (value.startsWith("SEQ")) {
            return Mode.SEQUENTIAL;
        }
        if (value.startsWith("SYN")) {
            return Mode.SYNCHRONIZED;
        }
        return Mode.INDEPENDENT;
    }

    static int[] parseSlots(String raw) {
        List<Integer> slots = new ArrayList<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                try {
                    int slot = Integer.parseInt(part.trim());
                    if (slot >= 0) {
                        slots.add(slot);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        int[] result = new int[slots.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = slots.get(i);
        }
        return result;
    }
}
