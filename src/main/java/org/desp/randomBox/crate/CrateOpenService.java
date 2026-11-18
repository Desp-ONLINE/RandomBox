package org.desp.randomBox.crate;

import com.binggre.binggreapi.utils.ColorManager;
import net.kyori.adventure.text.Component;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.boxUtils.BoxUtils;
import org.desp.randomBox.boxUtils.DateUtils;
import org.desp.randomBox.crate.opening.OpeningConfig;
import org.desp.randomBox.crate.opening.OpeningSession;
import org.desp.randomBox.database.ForcedRewardRepository;
import org.desp.randomBox.database.ItemDrawRepository;
import org.desp.randomBox.dto.ItemDrawLogDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.logging.Logger;

/** 상자 열기 공통 흐름. 블록 상자와 아이템 상자 모두 여기서 보상을 뽑고 마일스톤을 진행한다. */
public final class CrateOpenService {

    private CrateOpenService() {
    }

    /**
     * 보상을 뽑는다. 관리자가 강제 지정한 보상이 있으면 그것을 쓴다(1회 소모).
     * forcedKey 는 강제 보상 설정의 박스ID 자리에 들어가는 값이다(아이템 상자는 박스ID, 블록 상자는 상자ID).
     */
    public static CrateReward roll(Player player, Crate crate, String forcedKey) {
        Logger logger = RandomBox.getInstance().getLogger();
        Document forced = ForcedRewardRepository.getInstance().consume(player.getName(), forcedKey);
        if (forced != null) {
            CrateReward reward = crate.getReward(forced.getString("reward_id"));
            if (reward != null) {
                logger.info("[Crate] " + player.getName() + " 강제 보상 적용: " + forcedKey + " / " + reward.getId());
                return reward;
            }
            logger.warning("[Crate] 강제 보상 ID(" + forced.getString("reward_id") + ")를 상자 " + crate.getId() + "에서 찾지 못해 일반 뽑기로 진행합니다.");
        }
        return crate.roll(CrateManager.getInstance().getRarities());
    }

    /** 오픈 수와 마일스톤을 1 올리고, 도달한 마일스톤 보상을 지급한다. */
    public static void progressMilestones(Player player, Crate crate) {
        progressMilestones(player, crate, 1);
    }

    /** times 번 연 것으로 진행한다. 저장은 마지막에 한 번만 한다. */
    public static void progressMilestones(Player player, Crate crate, int times) {
        CrateUserRepository users = CrateUserRepository.getInstance();
        CrateUserRepository.CrateProgress progress = users.get(player, crate.getId());
        for (int i = 0; i < times; i++) {
            progressOnce(player, crate, progress);
        }
        users.saveAsync(player, crate.getId());
    }

    private static void progressOnce(Player player, Crate crate, CrateUserRepository.CrateProgress progress) {
        progress.openings++;
        if (crate.hasMilestones()) {
            progress.milestone++;
            for (CrateMilestone milestone : crate.getMilestones()) {
                if (milestone.getOpenings() != progress.milestone) {
                    continue;
                }
                CrateReward reward = crate.getReward(milestone.getRewardId());
                if (reward == null) {
                    RandomBox.getInstance().getLogger().warning("[Crate] 마일스톤 보상 " + milestone.getRewardId() + "이(가) 상자 " + crate.getId() + "에 없습니다.");
                    continue;
                }
                giveToInventory(player, mailName(crate), reward.createItems());
                player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 1f, 1f);
                player.sendMessage(CrateText.mm(
                        "<lgray><lgreen><openings>회 오픈 보상 </lgreen>을 수령했습니다! <lgreen>보상: <reward_name></lgreen> </lgray>",
                        CrateText.unparsed("openings", String.valueOf(milestone.getOpenings())),
                        CrateText.component("reward_name", reward.displayName())));
                logDraw(player, "milestone:" + crate.getId(), reward.getId());
            }
            if (crate.isMilestonesRepeatable() && progress.milestone >= crate.getMaxMilestone()) {
                progress.milestone = 0;
            }
        }
    }

    /**
     * 애니메이션이 켜진 상자면 애니메이션을 보여 준 뒤, 아니면 바로 onComplete 를 실행한다.
     * 애니메이션 설정을 찾지 못하면 바로 지급한다.
     */
    public static void present(Player player, Crate crate, CrateReward reward, Runnable onComplete) {
        if (crate.isAnimationEnabled()) {
            OpeningConfig config = CrateManager.getInstance().getOpenings().get(crate.getAnimationId());
            if (OpeningSession.start(player, crate, config, reward, onComplete)) {
                return;
            }
            RandomBox.getInstance().getLogger().warning("[Crate] 애니메이션 " + crate.getAnimationId() + " 설정이 없어 바로 지급합니다.");
        }
        onComplete.run();
    }

    /** 블록 상자 보상 지급. EC와 같이 인벤토리로 바로 주고 안내와 공지를 보낸다. */
    public static void deliverBlockReward(Player player, Crate crate, CrateReward reward, Location blockLocation) {
        List<ItemStack> items = reward.createItems();
        giveToInventory(player, mailName(crate), items);

        Component crateName = CrateText.mm(crate.getName());
        Component rewardName = reward.displayName();
        if (player.isOnline()) {
            player.sendMessage(CrateText.mm("<#919191> <lgreen><crate_name></lgreen> 에서 <lgreen><reward_name></lgreen> 보상을 획득하였습니다!!",
                    CrateText.component("crate_name", crateName), CrateText.component("reward_name", rewardName)));
        }
        if (reward.isBroadcast()) {
            broadcast(player, crateName, rewardName);
        }
        logDraw(player, "crate:" + crate.getId(), reward.getId());

        if (blockLocation != null && !crate.isAnimationEnabled()
                && CrateManager.getInstance().getConfig().getBoolean("display-reward-above-block", true)) {
            CrateHologramManager.getInstance().showReward(blockLocation, crate, reward);
        }
    }

    /**
     * 한번에 여러 개를 연 블록 상자 보상 지급. 획득 목록은 아이템별로 합쳐서 한 번에 보여 준다.
     * 공지 대상 보상은 하나씩 공지하고, 상자 위에는 마지막 공지 보상(없으면 마지막 보상)을 띄운다.
     */
    public static void deliverBlockRewards(Player player, Crate crate, List<CrateReward> rewards, Location blockLocation) {
        if (rewards.size() == 1) {
            deliverBlockReward(player, crate, rewards.get(0), blockLocation);
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        Map<String, Integer> amounts = new LinkedHashMap<>();
        Map<String, Component> names = new HashMap<>();
        CrateReward shown = null;
        Component crateName = CrateText.mm(crate.getName());
        for (CrateReward reward : rewards) {
            for (ItemStack stack : reward.createItems()) {
                items.add(stack);
                Component name = CrateText.itemName(stack);
                String key = CrateText.plain(name);
                names.putIfAbsent(key, name);
                amounts.merge(key, stack.getAmount(), Integer::sum);
            }
            if (reward.isBroadcast()) {
                shown = reward;
                broadcast(player, crateName, reward.displayName());
            }
            logDraw(player, "crate:" + crate.getId(), reward.getId());
        }
        giveToInventory(player, mailName(crate), items);

        if (player.isOnline()) {
            player.sendMessage(CrateText.mm("<#919191> <lgreen><crate_name></lgreen> 을(를) <lgreen><count>개</lgreen> 한번에 열어 다음 보상을 획득하였습니다!!",
                    CrateText.component("crate_name", crateName),
                    CrateText.unparsed("count", String.valueOf(rewards.size()))));
            for (Map.Entry<String, Integer> entry : amounts.entrySet()) {
                player.sendMessage(CrateText.mm("<#919191> ㄴ> <name> <lgray>x<amount></lgray>",
                        CrateText.component("name", names.get(entry.getKey())),
                        CrateText.unparsed("amount", String.valueOf(entry.getValue()))));
            }
        }

        if (shown == null) {
            shown = rewards.get(rewards.size() - 1);
        }
        if (blockLocation != null && CrateManager.getInstance().getConfig().getBoolean("display-reward-above-block", true)) {
            CrateHologramManager.getInstance().showReward(blockLocation, crate, shown);
        }
    }

    /** 메일 제목에 쓸 상자 이름(§ 색코드). */
    public static String mailName(Crate crate) {
        return CrateText.toLegacy(CrateText.mm(crate.getName()));
    }

    private static void broadcast(Player player, Component crateName, Component rewardName) {
        Component line = CrateText.mm("<lgray><lblue><player_name></lblue> 님께서 <lgreen><crate_name></lgreen> 에서 <lgreen><reward_name></lgreen> 을(를) 획득하셨습니다!!</lgray>",
                CrateText.unparsed("player_name", player.getName()),
                CrateText.component("crate_name", crateName), CrateText.component("reward_name", rewardName));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), "minecraft:block.note_block.bell", 1f, 1f);
            online.sendMessage(Component.text(" "));
            online.sendMessage(line);
            online.sendMessage(Component.text(" "));
        }
    }

    /** 인벤토리에 넣고, 남는 아이템은 메일로 보낸다. */
    public static void giveToInventory(Player player, String crateName, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        List<ItemStack> leftovers = new ArrayList<>();
        if (player.isOnline()) {
            HashMap<Integer, ItemStack> rest = player.getInventory().addItem(items.toArray(new ItemStack[0]));
            leftovers.addAll(rest.values());
        } else {
            leftovers.addAll(items);
        }
        if (!leftovers.isEmpty()) {
            BoxUtils.sendCrateReward(crateName, leftovers, player);
            if (player.isOnline()) {
                player.sendMessage(ColorManager.format("#FFD3A8[상자] #FFFFFF인벤토리가 가득 차서 남은 보상은 메일함에서 수령하실 수 있습니다."));
            }
        }
    }

    public static void logDraw(Player player, String usedItem, String drawItem) {
        String name = player.getName();
        String uuid = player.getUniqueId().toString();
        runAsync(() -> {
            try {
                ItemDrawRepository.getInstance().insertDrawLog(ItemDrawLogDto.builder()
                        .user_id(name).uuid(uuid).usedItem(usedItem).drawItem(drawItem).build(), DateUtils.getCurrentTime());
            } catch (Exception e) {
                RandomBox.getInstance().getLogger().warning("[Crate] 뽑기 로그 저장 실패: " + e.getMessage());
            }
        });
    }

    /** 비동기로 실행한다. 플러그인이 꺼지는 중이면 예약할 수 없으므로 바로 실행한다. */
    public static void runAsync(Runnable runnable) {
        if (RandomBox.getInstance().isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), runnable);
        } else {
            runnable.run();
        }
    }
}
