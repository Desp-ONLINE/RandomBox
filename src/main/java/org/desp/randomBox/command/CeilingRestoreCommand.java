package org.desp.randomBox.command;

import com.binggre.binggreapi.utils.ColorManager;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.database.CeilingRepository;
import org.desp.randomBox.database.ItemDrawRepository;
import org.desp.randomBox.database.PlayerCeilingRepository;
import org.desp.randomBox.dto.CeilingDataDto;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ItemDrawLog 를 시간순으로 재생(replay)해 "기타_신화고서" 박스 천장의 누적량을 소급 복구한다.
// 규칙:
//  - 특정 닉네임이 아니라 전체 유저를 대상으로 한다.
//  - 로그 조회/연산은 비동기로 수행하고, DB 반영 후 결과만 메인 스레드에서 보고한다.
//  - 재생은 플레이어별(정확히는 (uuid, 박스) 단위)로 수행한다.
//  - drawItem 에 "신화" 가 포함된 뽑기(=신화 획득)가 있으면 그 시점에 진척도를 0으로 초기화한다.
//    → 마지막 신화 뽑기 이후의 뽑기 수가 곧 복구 누적량이다. (goalString 은 복구와 무관하므로 보지 않는다.)
//  - cap 순환/보상은 복구에서 하지 않는다. 누적량(raw)만 그대로 저장하면, 접속 시 grantPendingCeilingRewards 가
//    반복(volatile) 천장은 보상 지급 + cap 정리를 처리하고, 반복 안되는(non-volatile) 천장은 건드리지 않아 그대로 유지된다.
//  - PlayerCeiling 문서가 없는 유저는 새로 만들어(upsert) 넣는다.
// 인자 없이 실행하면 미리보기, "/천장복구 진행" 으로 실제 반영한다.
public class CeilingRestoreCommand implements CommandExecutor {

    private static final String KEYWORD = "기타_신화고서";
    private static final String MYTHIC_KEYWORD = "신화";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean confirm = args.length >= 1 && args[0].equalsIgnoreCase("진행");

        // 리포지토리는 메인 스레드에서 미리 초기화해 둔다. (비동기 첫 호출로 인한 동시 초기화 방지)
        ItemDrawRepository itemDrawRepository = ItemDrawRepository.getInstance();
        PlayerCeilingRepository playerCeilingRepository = PlayerCeilingRepository.getInstance();
        CeilingRepository ceilingRepository = CeilingRepository.getInstance();

        sender.sendMessage("§e[천장복구] 로그를 비동기로 분석 중입니다...");

        Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () -> {
            List<Document> logs = itemDrawRepository.findDrawLogsSorted(KEYWORD);

            // (uuid, usedItem) 단위로 시간순 그룹화 (logs 는 이미 drawDate 오름차순)
            Map<String, List<Document>> groups = new LinkedHashMap<>();
            for (Document log : logs) {
                String uuid = log.getString("uuid");
                String usedItem = log.getString("usedItem");
                if (uuid == null || usedItem == null) {
                    continue;
                }
                groups.computeIfAbsent(uuid + "|" + usedItem, k -> new ArrayList<>()).add(log);
            }

            // 각 그룹을 재생해 복구할 누적량을 계산한다.
            List<ResultRow> rows = new ArrayList<>();
            for (List<Document> draws : groups.values()) {
                Document first = draws.get(0);
                String uuid = first.getString("uuid");
                String usedItem = first.getString("usedItem");
                // 닉네임은 변경 가능하므로 가장 최근(마지막) 뽑기 기록의 user_id 를 사용한다.
                String nickname = draws.get(draws.size() - 1).getString("user_id");

                int amount = replay(draws);

                CeilingDataDto ceilingData = ceilingRepository.getCeilingData(usedItem);
                String ceilingName = ceilingData != null ? ceilingData.getName() : usedItem;
                rows.add(new ResultRow(uuid, usedItem, nickname, ceilingName, amount));
            }

            // 진행 모드면 DB 에 반영하면서 변경 전 값/신규 생성 여부를 함께 수집한다.
            if (confirm) {
                for (ResultRow row : rows) {
                    PlayerCeilingRepository.CeilingRestoreResult result =
                            playerCeilingRepository.setCeilingAmountByUuid(row.uuid, row.nickname, row.usedItem, row.amount);
                    row.result = result;
                }
            }

            // 결과 보고 + 접속자 캐시 갱신은 메인 스레드에서 수행한다.
            Bukkit.getScheduler().runTask(RandomBox.getInstance(), () -> report(sender, playerCeilingRepository, rows, confirm));
        });

        return true;
    }

    // 한 (플레이어, 박스) 의 뽑기 기록을 시간순으로 재생해 최종 천장 누적량을 계산한다.
    // 신화 뽑기에서 0으로 초기화하고, 그 외 뽑기는 +1 누적. cap 순환은 하지 않는다. (접속 시 시스템이 처리)
    private int replay(List<Document> draws) {
        int amount = 0;
        for (Document draw : draws) {
            String drawItem = draw.getString("drawItem");
            if (drawItem != null && drawItem.contains(MYTHIC_KEYWORD)) {
                amount = 0; // 신화 획득 → 진척도 초기화. 이후 뽑기만 다시 누적된다.
            } else {
                amount += 1;
            }
        }
        return amount;
    }

    private void report(CommandSender sender, PlayerCeilingRepository playerCeilingRepository, List<ResultRow> rows, boolean confirm) {
        sender.sendMessage(ColorManager.format("#54daf4§n                                                                                                      §f"));

        if (rows.isEmpty()) {
            sender.sendMessage("§e[천장복구] '" + KEYWORD + "' 관련 뽑기 기록이 없습니다.");
            sender.sendMessage(ColorManager.format("#54daf4§n                                                                                                      §f"));
            return;
        }

        sender.sendMessage(ColorManager.format("#54daf4 [천장복구] §f대상 §e" + rows.size() + "§f명" + (confirm ? " §a(적용 완료)" : " §7(미리보기)")));
        sender.sendMessage("§f");

        int applied = 0;
        int created = 0;
        for (ResultRow row : rows) {
            if (!confirm) {
                sender.sendMessage("§7- " + row.nickname + " §f: " + row.ceilingName + " §e" + row.amount + "§f회");
                continue;
            }

            applied++;
            // 접속 중이면 캐시도 DB 기준으로 다시 불러와 동기화한다. (로그아웃 시 덮어쓰기 방지)
            try {
                Player online = Bukkit.getPlayer(UUID.fromString(row.uuid));
                if (online != null && online.isOnline()) {
                    playerCeilingRepository.loadPlayerData(online);
                }
            } catch (IllegalArgumentException ignored) {
            }

            String tag = (row.result != null && row.result.createdDocument) ? " §b[신규]" : "";
            int previous = row.result != null ? row.result.previous : 0;
            if (row.result != null && row.result.createdDocument) {
                created++;
            }
            sender.sendMessage("§a- " + row.nickname + " §f: " + row.ceilingName + " §7" + previous + "회 §f→ §e" + row.amount + "§f회" + tag);
        }

        if (confirm) {
            sender.sendMessage("§f");
            sender.sendMessage("§a[천장복구] 완료 — 적용 §e" + applied + "§a명 (신규 문서 §e" + created + "§a명)");
        } else {
            sender.sendMessage("§f");
            sender.sendMessage("§c실제로 반영하려면 §e/천장복구 진행 §c을 입력하세요.");
        }

        sender.sendMessage(ColorManager.format("#54daf4§n                                                                                                      §f"));
    }

    private static class ResultRow {
        final String uuid;
        final String usedItem;
        final String nickname;
        final String ceilingName;
        final int amount;
        PlayerCeilingRepository.CeilingRestoreResult result; // 진행 모드에서 채워짐.

        ResultRow(String uuid, String usedItem, String nickname, String ceilingName, int amount) {
            this.uuid = uuid;
            this.usedItem = usedItem;
            this.nickname = nickname;
            this.ceilingName = ceilingName;
            this.amount = amount;
        }
    }
}
