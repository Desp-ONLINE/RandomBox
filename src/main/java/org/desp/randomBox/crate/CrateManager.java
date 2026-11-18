package org.desp.randomBox.crate;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.crate.opening.OpeningConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** 상자 설정, 등급, 홀로그램 문구, 애니메이션 설정, 블록 위치 색인을 들고 있는 관리자. */
public class CrateManager {

    private static CrateManager instance;

    @Getter
    private YamlConfiguration config;
    @Getter
    private final Map<String, CrateRarity> rarities = new LinkedHashMap<>();
    @Getter
    private final Map<String, List<String>> hologramTemplates = new HashMap<>();
    @Getter
    private final Map<String, OpeningConfig> openings = new HashMap<>();
    private final Map<String, Crate> crates = new LinkedHashMap<>();
    /** "world:x:y:z" -> 상자 */
    private final Map<String, Crate> blockIndex = new HashMap<>();

    public static CrateManager getInstance() {
        if (instance == null) {
            instance = new CrateManager();
        }
        return instance;
    }

    /** crates.yml, openings 폴더, Mongo 상자 데이터를 모두 다시 읽는다. 메인 스레드에서 호출한다. */
    public void reload() {
        RandomBox plugin = RandomBox.getInstance();
        Logger logger = plugin.getLogger();

        File configFile = new File(plugin.getDataFolder(), "crates.yml");
        if (!configFile.exists()) {
            plugin.saveResource("crates.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        rarities.clear();
        ConfigurationSection raritySection = config.getConfigurationSection("rarities");
        if (raritySection != null) {
            for (String key : raritySection.getKeys(false)) {
                rarities.put(key.toLowerCase(), new CrateRarity(key.toLowerCase(),
                        raritySection.getString(key + ".name", key),
                        raritySection.getDouble(key + ".weight", 1.0)));
            }
        }

        hologramTemplates.clear();
        ConfigurationSection templateSection = config.getConfigurationSection("hologram-templates");
        if (templateSection != null) {
            for (String key : templateSection.getKeys(false)) {
                hologramTemplates.put(key, templateSection.getStringList(key));
            }
        }

        openings.clear();
        File openingFolder = new File(plugin.getDataFolder(), "openings");
        for (String id : new String[]{"mystery", "enclosing", "csgo", "roulette", "storm"}) {
            if (!new File(openingFolder, id + ".yml").exists()) {
                plugin.saveResource("openings/" + id + ".yml", false);
            }
        }
        File[] files = openingFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - 4);
                try {
                    openings.put(id, OpeningConfig.load(id, YamlConfiguration.loadConfiguration(file)));
                } catch (Exception e) {
                    logger.warning("[Crate] 애니메이션 설정 " + file.getName() + " 을(를) 읽지 못했습니다: " + e.getMessage());
                }
            }
        }

        crates.clear();
        for (Crate crate : CrateRepository.getInstance().loadAll()) {
            crates.put(crate.getId(), crate);
        }
        rebuildBlockIndex();
        logger.info("[Crate] 상자 " + crates.size() + "개, 애니메이션 " + openings.size() + "개를 불러왔습니다.");
    }

    public void rebuildBlockIndex() {
        blockIndex.clear();
        for (Crate crate : crates.values()) {
            for (String position : crate.getPositions()) {
                String key = keyOf(position);
                if (key != null) {
                    blockIndex.put(key, crate);
                }
            }
        }
    }

    public Collection<Crate> getCrates() {
        return crates.values();
    }

    public Crate getCrate(String id) {
        return id == null ? null : crates.get(id);
    }

    /** 이 아이템을 열쇠로 쓰는 상자. 없으면 null 이다. */
    public Crate getCrateByKey(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (Crate crate : getCrates()) {
            if (crate.requiresKey() && itemId.equals(crate.getKeyItemId())) {
                return crate;
            }
        }
        return null;
    }

    public Crate getCrateAt(Block block) {
        return blockIndex.get(block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ());
    }

    // ------------------------------------------------------------ 위치

    public static String positionOf(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ() + "," + block.getWorld().getName();
    }

    /** "x,y,z,world" 를 블록 위치로 바꾼다. 월드가 없으면 null. */
    public static Location toLocation(String position) {
        String[] split = position.split(",");
        if (split.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(split[3].trim());
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(split[0].trim()), Integer.parseInt(split[1].trim()), Integer.parseInt(split[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String keyOf(String position) {
        String[] split = position.split(",");
        if (split.length < 4) {
            return null;
        }
        return split[3].trim() + ":" + split[0].trim() + ":" + split[1].trim() + ":" + split[2].trim();
    }

    public List<Location> loadedPositions(Crate crate) {
        List<Location> result = new ArrayList<>();
        for (String position : crate.getPositions()) {
            Location location = toLocation(position);
            if (location != null) {
                result.add(location);
            }
        }
        return result;
    }

    // ------------------------------------------------------------ 설정값

    /** 이 서버에서 블록 상자를 켜는지. EtCetera channelType 이 block-channels 에 있어야 한다. */
    public boolean isBlockCrateEnabled() {
        List<String> channels = config.getStringList("block-channels");
        if (channels.isEmpty()) {
            return true;
        }
        Plugin etcetera = Bukkit.getPluginManager().getPlugin("EtCetera");
        if (etcetera == null) {
            return true;
        }
        String channel = etcetera.getConfig().getString("channelType", "");
        return channels.contains(channel);
    }

    /** EC가 아직 켜져 있으면 이중 지급을 막기 위해 자체 상자를 잠시 멈춘다. */
    public static boolean isExcellentCratesActive() {
        return Bukkit.getPluginManager().isPluginEnabled("ExcellentCrates");
    }

    public int visibilityDistance() {
        return config.getInt("visibility-distance", 24);
    }
}
