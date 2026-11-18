package org.desp.randomBox.command;

import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.database.BoxDataRepository;
import org.desp.randomBox.database.CrateBoxRepository;
import org.desp.randomBox.database.ForcedRewardRepository;
import org.desp.randomBox.dto.BoxDataDto;
import org.desp.randomBox.dto.CrateBoxDto;
import org.desp.randomBox.dto.ItemDataDto;
import org.jetbrains.annotations.NotNull;
import org.desp.randomBox.crate.Crate;
import org.desp.randomBox.crate.CrateManager;

import java.util.List;
import java.util.stream.Collectors;

// 관리자 전용 명령어. 특정 유저가 다음에 여는 특정 박스의 보상을 강제 지정한다.
//  - /보상강제설정 <닉네임> <박스ID> <보상ID> : 설정 (다시 실행하면 덮어씀)
//  - /보상강제설정 해제 <닉네임> <박스ID>     : 설정 삭제
//  - /보상강제설정 목록                        : 현재 설정 전체 확인
// 대상 유저는 이 설정의 존재를 알 수 없어야 한다. 비관리자에게는 존재하지 않는 명령어처럼 응답하고,
// 대상 유저에게는 어떤 메시지도 보내지 않는다. (지급은 일반 뽑기와 완전히 동일한 흐름으로 진행)
public class ForcedRewardCommand implements CommandExecutor {

    private static final String UNKNOWN_COMMAND = "Unknown command. Type \"/help\" for help.";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 비관리자에게는 명령어가 존재하지 않는 것처럼 보이게 한다.
        if (!sender.isOp()) {
            sender.sendMessage(UNKNOWN_COMMAND);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("목록")) {
            showList(sender);
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("해제")) {
            removeForcedReward(sender, args[1], args[2]);
            return true;
        }

        if (args.length == 3) {
            setForcedReward(sender, args[0], args[1], args[2]);
            return true;
        }

        sender.sendMessage("§e[보상강제설정] 사용법:");
        sender.sendMessage("§7 /보상강제설정 <닉네임> <박스ID> <보상ID> §f- 해당 유저의 다음 뽑기 보상을 강제 지정");
        sender.sendMessage("§7 /보상강제설정 해제 <닉네임> <박스ID> §f- 설정 삭제");
        sender.sendMessage("§7 /보상강제설정 목록 §f- 현재 설정 전체 확인");
        return true;
    }

    private void setForcedReward(CommandSender sender, String nickname, String boxId, String rewardId) {
        BoxDataDto legacyBox = BoxDataRepository.getInstance().getBoxData().get(boxId);
        CrateBoxDto crateBox = CrateBoxRepository.getInstance().get(boxId);
        // 박스ID 자리에 상자ID를 넣으면 설치된 상자 블록의 다음 오픈 보상을 지정한다.
        Crate blockCrate = CrateManager.getInstance().getCrate(boxId);

        if (legacyBox == null && crateBox == null && blockCrate == null) {
            sender.sendMessage("§c[보상강제설정] 존재하지 않는 박스ID 입니다: " + boxId);
            return;
        }

        // 박스 종류(일반/크레이트)에 맞춰 보상ID가 실제 존재하는지 검증한다.
        boolean valid = false;
        if (legacyBox != null && findLegacyItem(legacyBox, rewardId) != null) {
            valid = true;
        }
        if (!valid && crateBox != null) {
            Crate crate = CrateManager.getInstance().getCrate(crateBox.getCrate_id());
            if (crate != null && crate.getReward(rewardId) != null) {
                valid = true;
            }
        }
        if (!valid && blockCrate != null && blockCrate.getReward(rewardId) != null) {
            valid = true;
        }

        if (!valid) {
            sender.sendMessage("§c[보상강제설정] 박스 §e" + boxId + "§c 에 존재하지 않는 보상ID 입니다: " + rewardId);
            sendAvailableRewardIds(sender, legacyBox, crateBox, blockCrate);
            return;
        }

        final String setBy = sender.getName();
        Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () -> {
            ForcedRewardRepository.getInstance().set(nickname, boxId, rewardId, setBy);
            Bukkit.getScheduler().runTask(RandomBox.getInstance(), () ->
                    sender.sendMessage("§a[보상강제설정] §e" + nickname + "§a 님이 다음에 여는 §e" + boxId
                            + "§a 의 보상을 §e" + rewardId + "§a (으)로 확정했습니다. §7(1회 적용 후 자동 해제)"));
        });
    }

    private void removeForcedReward(CommandSender sender, String nickname, String boxId) {
        Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () -> {
            boolean removed = ForcedRewardRepository.getInstance().remove(nickname, boxId);
            Bukkit.getScheduler().runTask(RandomBox.getInstance(), () -> {
                if (removed) {
                    sender.sendMessage("§a[보상강제설정] §e" + nickname + "§a / §e" + boxId + "§a 설정을 해제했습니다.");
                } else {
                    sender.sendMessage("§c[보상강제설정] 해당 설정이 없습니다: " + nickname + " / " + boxId);
                }
            });
        });
    }

    private void showList(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () -> {
            List<Document> documents = ForcedRewardRepository.getInstance().list();
            Bukkit.getScheduler().runTask(RandomBox.getInstance(), () -> {
                if (documents.isEmpty()) {
                    sender.sendMessage("§e[보상강제설정] 현재 설정된 강제 보상이 없습니다.");
                    return;
                }
                sender.sendMessage("§e[보상강제설정] 현재 설정 §f" + documents.size() + "§e건:");
                for (Document doc : documents) {
                    sender.sendMessage("§7- §f" + doc.getString("user_id")
                            + " §7/ §f" + doc.getString("box_id")
                            + " §7→ §e" + doc.getString("reward_id")
                            + " §8(" + doc.getString("set_by") + ", " + doc.getString("set_date") + ")");
                }
            });
        });
    }

    private void sendAvailableRewardIds(CommandSender sender, BoxDataDto legacyBox, CrateBoxDto crateBox, Crate blockCrate) {
        if (legacyBox != null) {
            String ids = legacyBox.getAvailableItems().stream()
                    .map(ItemDataDto::getItem_id)
                    .collect(Collectors.joining("§7, §f"));
            sender.sendMessage("§7 사용 가능한 보상ID: §f" + ids);
        }
        if (crateBox != null) {
            Crate crate = CrateManager.getInstance().getCrate(crateBox.getCrate_id());
            if (crate != null) {
                sender.sendMessage("§7 사용 가능한 보상ID: §f" + String.join("§7, §f", crate.getRewards().keySet()));
            } else {
                sender.sendMessage("§7 상자(" + crateBox.getCrate_id() + ")를 찾을 수 없어 보상ID를 조회하지 못했습니다.");
            }
        }
        if (blockCrate != null) {
            sender.sendMessage("§7 사용 가능한 보상ID: §f" + String.join("§7, §f", blockCrate.getRewards().keySet()));
        }
    }

    public static ItemDataDto findLegacyItem(BoxDataDto boxData, String rewardId) {
        for (ItemDataDto item : boxData.getAvailableItems()) {
            if (item.getItem_id().equalsIgnoreCase(rewardId)
                    || (item.getType() + ":" + item.getItem_id()).equalsIgnoreCase(rewardId)) {
                return item;
            }
        }
        return null;
    }

}
