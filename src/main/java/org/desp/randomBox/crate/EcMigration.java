package org.desp.randomBox.crate;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.desp.randomBox.RandomBox;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ExcellentCrates 데이터를 RandomBox 로 옮긴다.
 *  1) 상자 설정 : plugins/ExcellentCrates/crates/*.yml 을 읽어 Mongo CrateData 에 저장한다. EC가 꺼져 있어도 된다.
 *     보상 아이템 NBT 안의 MMOITEMS_ITEM_TYPE, MMOITEMS_ITEM_ID, count 를 읽어 MMOItems 보상으로 바꾼다.
 *  2) 유저 기록 : EC API 로 전체 유저의 오픈 수와 마일스톤 진행도를 받아 Mongo CrateUserData 에 저장한다. EC가 켜져 있어야 한다.
 */
public final class EcMigration {

    private static final Pattern MMO_TYPE = Pattern.compile("MMOITEMS_ITEM_TYPE:\"([^\"]+)\"");
    private static final Pattern MMO_ID = Pattern.compile("MMOITEMS_ITEM_ID:\"([^\"]+)\"");
    private static final Pattern COUNT = Pattern.compile("count:(\\d+),id:\"minecraft:[a-z0-9_]+\"}\\s*$");

    /** 기존 EtCetera CrateListener 의 열쇠 조건. {열쇠 MMOItems ID, 안내용 열쇠 이름} */
    private static final Map<String, String[]> KEYS = new HashMap<>();

    static {
        KEYS.put("common", new String[]{"기타_판도라의열쇠_일반", "일반 등급 판도라의 열쇠"});
        KEYS.put("rare", new String[]{"기타_판도라의열쇠_레어", "레어 등급 판도라의 열쇠"});
        KEYS.put("epic", new String[]{"기타_판도라의열쇠_에픽", "에픽 등급 판도라의 열쇠"});
        KEYS.put("unique", new String[]{"기타_판도라의열쇠_유니크", "유니크 등급 판도라의 열쇠"});
        KEYS.put("legend", new String[]{"기타_판도라의열쇠_레전더리", "레전더리 등급 판도라의 열쇠"});
        KEYS.put("legendary", new String[]{"기타_판도라의열쇠", "판도라의 열쇠"});
        KEYS.put("elvenheim", new String[]{"기타_엘븐하임열쇠", "네이처 키"});
        KEYS.put("kalima", new String[]{"기타_칼리마열쇠", "샌드스톤 키"});
        KEYS.put("inferium", new String[]{"기타_인페리움열쇠", "라바플로우 키"});
        KEYS.put("epsilon", new String[]{"기타_엡실론열쇠", "포가튼 키"});
        KEYS.put("arctica", new String[]{"기타_아르크티카열쇠", "프로스트 키"});
        KEYS.put("vespera", new String[]{"기타_베스페라열쇠", "스타라이트 키"});
    }

    /** 기존 EtCetera 와 같이 10번 퀘스트 데이터(9번 메인 퀘스트 클리어)가 있어야 연다. */
    private static final int DEFAULT_REQUIRED_QUEST = 10;

    private EcMigration() {
    }

    public static void run(CommandSender sender, boolean includeUsers) {
        RandomBox plugin = RandomBox.getInstance();
        String folder = CrateManager.getInstance().getConfig().getString("ec-folder", "plugins/ExcellentCrates");
        File crateFolder = new File(folder, "crates");
        File[] files = crateFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            sender.sendMessage("§c[상자 이전] " + crateFolder.getPath() + " 폴더에서 상자 파일을 찾지 못했습니다.");
            return;
        }

        List<Crate> crates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (File file : files) {
            String crateId = file.getName().substring(0, file.getName().length() - 4);
            try {
                crates.add(readCrate(crateId, YamlConfiguration.loadConfiguration(file), warnings));
            } catch (Exception e) {
                warnings.add(crateId + " 파일을 읽지 못했습니다: " + e.getMessage());
            }
        }

        // 유저 기록은 EC 메모리에 있는 접속자 값을 메인 스레드에서 먼저 확보한다.
        Map<UUID, CrateUserRepository.ImportRow> onlineRows = new HashMap<>();
        boolean usersAvailable = includeUsers && CrateManager.isExcellentCratesActive();
        if (usersAvailable) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                CrateUserRepository.ImportRow row = Ec.onlineRow(player);
                if (row != null) {
                    onlineRows.put(player.getUniqueId(), row);
                }
            }
        }

        sender.sendMessage("§e[상자 이전] 상자 " + crates.size() + "개를 저장하고 있습니다.");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int rewardCount = 0;
            for (Crate crate : crates) {
                CrateRepository.getInstance().save(crate);
                rewardCount += crate.getRewards().size();
            }

            int userCount = -1;
            String userError = null;
            if (usersAvailable) {
                try {
                    Map<UUID, CrateUserRepository.ImportRow> rows = Ec.allRows();
                    rows.putAll(onlineRows);
                    userCount = CrateUserRepository.getInstance().bulkImport(new ArrayList<>(rows.values()));
                } catch (Throwable e) {
                    userError = e.getClass().getSimpleName() + ": " + e.getMessage();
                    e.printStackTrace();
                }
            }

            final int savedRewards = rewardCount;
            final int savedUsers = userCount;
            final String error = userError;
            Bukkit.getScheduler().runTask(plugin, () -> {
                CrateManager.getInstance().reload();
                CrateHologramManager.getInstance().refreshAll();
                sender.sendMessage("§a[상자 이전] 상자 " + crates.size() + "개와 보상 " + savedRewards + "개를 저장했습니다.");
                for (String warning : warnings) {
                    sender.sendMessage("§6[상자 이전] " + warning);
                }
                if (!includeUsers) {
                    sender.sendMessage("§7[상자 이전] 유저 기록은 옮기지 않았습니다.");
                } else if (!usersAvailable) {
                    sender.sendMessage("§c[상자 이전] ExcellentCrates 가 꺼져 있어 유저 마일스톤 기록은 옮기지 못했습니다. EC를 켠 상태에서 다시 실행해 주세요.");
                } else if (error != null) {
                    sender.sendMessage("§c[상자 이전] 유저 기록을 옮기다 오류가 났습니다: " + error);
                } else {
                    sender.sendMessage("§a[상자 이전] 유저 " + savedUsers + "명의 오픈 수와 마일스톤 진행도를 옮겼습니다.");
                    reloadOnlineCaches();
                }
                sender.sendMessage("§e[상자 이전] 확인이 끝나면 ExcellentCrates 를 서버에서 빼고 재시작해 주세요. EC가 켜져 있는 동안 RandomBox 상자는 열리지 않습니다.");
            });
        });
    }

    private static void reloadOnlineCaches() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () ->
                    CrateUserRepository.getInstance().replaceCache(uuid, CrateUserRepository.getInstance().read(uuid)));
        }
    }

    // ------------------------------------------------------------ 상자 파일

    private static Crate readCrate(String crateId, YamlConfiguration yml, List<String> warnings) {
        Crate crate = new Crate();
        crate.setId(crateId);
        crate.setName(yml.getString("Name", crateId));

        String[] key = KEYS.get(crateId);
        if (key != null) {
            crate.setKeyItemId(key[0]);
            crate.setKeyName(key[1]);
        } else {
            warnings.add(crateId + " 상자는 열쇠 정보가 없어 열쇠 없이 열립니다. 필요하면 DB의 key_item 을 채워 주세요.");
        }
        crate.setRequiredQuest(DEFAULT_REQUIRED_QUEST);

        crate.setPositions(new ArrayList<>(yml.getStringList("Block.Positions")));
        crate.setHologramEnabled(yml.getBoolean("Block.Hologram.Enabled", true));
        crate.setHologramTemplate(yml.getString("Block.Hologram.Template", "default"));
        crate.setHologramYOffset(yml.getDouble("Block.Hologram.Y_Offset", 0));
        crate.setEffectModel(yml.getString("Block.Effect.Model", "NONE"));
        String particle = yml.getString("Block.Effect.Particle.Name", "CLOUD");
        crate.setEffectParticle(particle == null || particle.equalsIgnoreCase("null") ? "CLOUD" : particle);

        crate.setAnimationEnabled(yml.getBoolean("Animation.Enabled", false));
        crate.setAnimationId(yml.getString("Animation.Id", "mystery"));
        crate.setPreviewId(yml.getString("Preview.Id", "default"));

        crate.setMilestonesRepeatable(yml.getBoolean("Milestones.Repeatable", true));
        ConfigurationSection milestoneSection = yml.getConfigurationSection("Milestones.List");
        if (milestoneSection != null) {
            for (String id : milestoneSection.getKeys(false)) {
                crate.getMilestones().add(new CrateMilestone(
                        milestoneSection.getInt(id + ".Openings"),
                        milestoneSection.getString(id + ".Reward_Id")));
            }
        }

        ConfigurationSection rewardSection = yml.getConfigurationSection("Rewards.List");
        if (rewardSection != null) {
            for (String rewardId : rewardSection.getKeys(false)) {
                ConfigurationSection reward = rewardSection.getConfigurationSection(rewardId);
                if (reward == null) {
                    continue;
                }
                List<CrateReward.RewardItem> items = new ArrayList<>();
                ConfigurationSection itemsData = reward.getConfigurationSection("ItemsData");
                if (itemsData != null) {
                    for (String itemKey : itemsData.getKeys(false)) {
                        String nbt = itemsData.getString(itemKey + ".Tag.Value", "");
                        Matcher type = MMO_TYPE.matcher(nbt);
                        Matcher id = MMO_ID.matcher(nbt);
                        if (!type.find() || !id.find()) {
                            warnings.add(crateId + " / " + rewardId + " 보상에 MMOItems 가 아닌 아이템이 있어 건너뛰었습니다.");
                            continue;
                        }
                        Matcher count = COUNT.matcher(nbt);
                        int amount = count.find() ? Integer.parseInt(count.group(1)) : 1;
                        items.add(new CrateReward.RewardItem(type.group(1), id.group(1), amount));
                    }
                }
                if (items.isEmpty()) {
                    warnings.add(crateId + " / " + rewardId + " 보상에 옮길 아이템이 없어 건너뛰었습니다.");
                    continue;
                }
                crate.getRewards().put(rewardId, new CrateReward(rewardId,
                        reward.getDouble("Weight", 0),
                        reward.getString("Rarity", "common").toLowerCase(),
                        reward.getBoolean("Broadcast", false),
                        items));
            }
        }

        for (CrateMilestone milestone : crate.getMilestones()) {
            if (crate.getReward(milestone.getRewardId()) == null) {
                warnings.add(crateId + " 마일스톤 보상 " + milestone.getRewardId() + " 을(를) 보상 목록에서 찾지 못했습니다.");
            }
        }
        return crate;
    }

    // ------------------------------------------------------------ EC API

    /** EC 클래스는 이 안에서만 참조한다. EC가 없는 서버에서도 RandomBox 가 정상 로드되게 하기 위해서다. */
    private static final class Ec {

        static CrateUserRepository.ImportRow onlineRow(Player player) {
            su.nightexpress.excellentcrates.user.CrateUser user = su.nightexpress.excellentcrates.CratesAPI.getUserData(player);
            return user == null ? null : toRow(user);
        }

        static Map<UUID, CrateUserRepository.ImportRow> allRows() {
            Map<UUID, CrateUserRepository.ImportRow> rows = new HashMap<>();
            for (su.nightexpress.excellentcrates.user.CrateUser user
                    : su.nightexpress.excellentcrates.CratesAPI.getPlugin().getDataHandler().getUsers()) {
                CrateUserRepository.ImportRow row = toRow(user);
                if (row != null) {
                    rows.put(user.getId(), row);
                }
            }
            return rows;
        }

        private static CrateUserRepository.ImportRow toRow(su.nightexpress.excellentcrates.user.CrateUser user) {
            Map<String, CrateUserRepository.CrateProgress> crates = new HashMap<>();
            for (Map.Entry<String, su.nightexpress.excellentcrates.data.crate.UserCrateData> entry : user.getCrateDataMap().entrySet()) {
                su.nightexpress.excellentcrates.data.crate.UserCrateData data = entry.getValue();
                if (data == null) {
                    continue;
                }
                crates.put(entry.getKey(), new CrateUserRepository.CrateProgress(data.getOpenings(), data.getMilestone()));
            }
            if (crates.isEmpty()) {
                return null;
            }
            return new CrateUserRepository.ImportRow(user.getId().toString(), user.getName(), crates);
        }
    }
}
