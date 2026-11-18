package org.desp.randomBox.command;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.crate.Crate;
import org.desp.randomBox.crate.CrateHologramManager;
import org.desp.randomBox.crate.CrateManager;
import org.desp.randomBox.crate.CrateRepository;
import org.desp.randomBox.crate.EcMigration;
import org.desp.randomBox.crate.gui.CratePreviewGui;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 전용 상자 명령어.
 *  /보물상자 이전 [상자만]          EC 상자 설정과 유저 마일스톤을 옮긴다
 *  /보물상자 리로드                 crates.yml, 애니메이션, DB 상자 설정을 다시 읽는다
 *  /보물상자 목록                   상자 목록
 *  /보물상자 위치추가 <상자ID>      바라보는 블록을 상자로 지정한다
 *  /보물상자 위치제거               바라보는 블록의 상자 지정을 해제한다
 *  /보물상자 미리보기 <상자ID>      미리보기 GUI를 연다
 *  /보물상자 애니메이션 <상자ID> <켜기|끄기> [애니메이션ID]
 */
public class CrateAdminCommand implements CommandExecutor, TabCompleter {

    private static final String UNKNOWN_COMMAND = "Unknown command. Type \"/help\" for help.";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(UNKNOWN_COMMAND);
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        CrateManager manager = CrateManager.getInstance();
        switch (args[0]) {
            case "이전" -> EcMigration.run(sender, !(args.length > 1 && args[1].equals("상자만")));
            case "리로드" -> {
                manager.reload();
                CrateHologramManager.getInstance().refreshAll();
                sender.sendMessage("§a[상자] 상자 " + manager.getCrates().size() + "개를 다시 불러왔습니다.");
            }
            case "목록" -> {
                sender.sendMessage("§e[상자] 상자 " + manager.getCrates().size() + "개");
                for (Crate crate : manager.getCrates()) {
                    sender.sendMessage("§7- §f" + crate.getId() + " §7보상 " + crate.getRewards().size()
                            + "개, 위치 " + crate.getPositions().size() + "곳, 열쇠 " + (crate.requiresKey() ? crate.getKeyItemId() : "없음")
                            + ", 애니메이션 " + (crate.isAnimationEnabled() ? crate.getAnimationId() : "꺼짐"));
                }
                if (CrateManager.isExcellentCratesActive()) {
                    sender.sendMessage("§c[상자] ExcellentCrates 가 켜져 있어 RandomBox 상자는 지금 열리지 않습니다.");
                }
            }
            case "위치추가" -> {
                Player player = requirePlayer(sender);
                Crate crate = args.length > 1 ? manager.getCrate(args[1]) : null;
                if (player == null) return true;
                if (crate == null) {
                    sender.sendMessage("§c[상자] 상자ID를 확인해 주세요. /보물상자 목록 으로 볼 수 있습니다.");
                    return true;
                }
                Block block = player.getTargetBlockExact(6);
                if (block == null || block.getType().isAir()) {
                    sender.sendMessage("§c[상자] 상자로 지정할 블록을 바라본 상태에서 입력해 주세요.");
                    return true;
                }
                Crate existing = manager.getCrateAt(block);
                if (existing != null) {
                    sender.sendMessage("§c[상자] 이 블록은 이미 " + existing.getId() + " 상자로 지정되어 있습니다.");
                    return true;
                }
                crate.getPositions().add(CrateManager.positionOf(block));
                savePositions(sender, crate, "§a[상자] 바라보는 블록을 " + crate.getId() + " 상자로 지정했습니다.");
            }
            case "위치제거" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                Block block = player.getTargetBlockExact(6);
                Crate crate = block == null ? null : manager.getCrateAt(block);
                if (crate == null) {
                    sender.sendMessage("§c[상자] 바라보는 블록은 상자로 지정되어 있지 않습니다.");
                    return true;
                }
                crate.getPositions().remove(CrateManager.positionOf(block));
                savePositions(sender, crate, "§a[상자] 바라보는 블록의 " + crate.getId() + " 상자 지정을 해제했습니다.");
            }
            case "미리보기" -> {
                Player player = requirePlayer(sender);
                Crate crate = args.length > 1 ? manager.getCrate(args[1]) : null;
                if (player == null) return true;
                if (crate == null) {
                    sender.sendMessage("§c[상자] 상자ID를 확인해 주세요.");
                    return true;
                }
                CratePreviewGui.open(player, crate, 0);
            }
            case "애니메이션" -> {
                Crate crate = args.length > 2 ? manager.getCrate(args[1]) : null;
                if (crate == null || !(args[2].equals("켜기") || args[2].equals("끄기"))) {
                    sender.sendMessage("§c[상자] /보물상자 애니메이션 <상자ID> <켜기|끄기> [애니메이션ID]");
                    sender.sendMessage("§7 사용 가능한 애니메이션: §f" + String.join("§7, §f", manager.getOpenings().keySet()));
                    return true;
                }
                if (args.length > 3) {
                    if (!manager.getOpenings().containsKey(args[3])) {
                        sender.sendMessage("§c[상자] 없는 애니메이션입니다. 사용 가능: " + String.join(", ", manager.getOpenings().keySet()));
                        return true;
                    }
                    crate.setAnimationId(args[3]);
                }
                crate.setAnimationEnabled(args[2].equals("켜기"));
                Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () -> CrateRepository.getInstance().save(crate));
                sender.sendMessage("§a[상자] " + crate.getId() + " 상자의 애니메이션을 "
                        + (crate.isAnimationEnabled() ? crate.getAnimationId() + "(으)로 켰습니다." : "껐습니다."));
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void savePositions(CommandSender sender, Crate crate, String message) {
        CrateManager.getInstance().rebuildBlockIndex();
        CrateHologramManager.getInstance().refreshAll();
        Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () -> CrateRepository.getInstance().savePositions(crate));
        sender.sendMessage(message);
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("§c[상자] 게임 안에서만 사용할 수 있습니다.");
        return null;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e[상자] 사용법");
        sender.sendMessage("§7 /보물상자 이전 [상자만] §f EC 상자 설정과 유저 마일스톤을 옮깁니다.");
        sender.sendMessage("§7 /보물상자 리로드 §f 설정과 상자 데이터를 다시 불러옵니다.");
        sender.sendMessage("§7 /보물상자 목록 §f 상자 목록을 봅니다.");
        sender.sendMessage("§7 /보물상자 위치추가 <상자ID> §f 바라보는 블록을 상자로 지정합니다.");
        sender.sendMessage("§7 /보물상자 위치제거 §f 바라보는 블록의 상자 지정을 해제합니다.");
        sender.sendMessage("§7 /보물상자 미리보기 <상자ID> §f 미리보기 화면을 엽니다.");
        sender.sendMessage("§7 /보물상자 애니메이션 <상자ID> <켜기|끄기> [애니메이션ID] §f 오픈 애니메이션을 설정합니다.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.isOp()) {
            return result;
        }
        if (args.length == 1) {
            for (String sub : new String[]{"이전", "리로드", "목록", "위치추가", "위치제거", "미리보기", "애니메이션"}) {
                if (sub.startsWith(args[0])) result.add(sub);
            }
        } else if (args.length == 2 && (args[0].equals("위치추가") || args[0].equals("미리보기") || args[0].equals("애니메이션"))) {
            for (Crate crate : CrateManager.getInstance().getCrates()) {
                if (crate.getId().startsWith(args[1])) result.add(crate.getId());
            }
        } else if (args.length == 2 && args[0].equals("이전")) {
            result.add("상자만");
        } else if (args.length == 3 && args[0].equals("애니메이션")) {
            result.add("켜기");
            result.add("끄기");
        } else if (args.length == 4 && args[0].equals("애니메이션")) {
            result.addAll(CrateManager.getInstance().getOpenings().keySet());
        }
        return result;
    }
}
