package org.desp.randomBox.database;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bukkit.entity.Player;
import org.desp.randomBox.dto.CeilingDataDto;
import org.desp.randomBox.dto.DetailedCeilingDto;
import org.desp.randomBox.dto.PlayerCeilingDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class PlayerCeilingRepository {

    private static PlayerCeilingRepository instance;
    private final MongoCollection<Document> playerCeilingCollection;
    public static Map<Player, PlayerCeilingDto> playerCeilingDtoMap = new HashMap<>();

    public PlayerCeilingRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.playerCeilingCollection = database.getDatabase().getCollection("PlayerCeiling");
    }

    public static PlayerCeilingRepository getInstance() {
        if (instance == null) {
            instance = new PlayerCeilingRepository();
        }
        return instance;
    }

    public Document getPlayerDocument(Player player) {
        Document first = playerCeilingCollection.find(new Document("uuid", player.getUniqueId().toString())).first();

        if (first == null) {
            first = insertDefaultDocument(player);
        }
        return first;
    }

    public PlayerCeilingDto getPlayerCacheData(Player player) {
        return playerCeilingDtoMap.get(player);
    }

    // 닉네임으로 DB에서 천장 누적량을 조회 (오프라인 플레이어 포함). 데이터가 없으면 null 반환.
    public Map<String, Integer> getCeilingAmountsByNickname(String nickname) {
        Document playerDocument = playerCeilingCollection.find(new Document("nickname", nickname)).first();
        if (playerDocument == null) {
            return null;
        }

        Map<String, Integer> amounts = new LinkedHashMap<>();
        List<Document> ceilingData = playerDocument.getList("ceilingData", Document.class);
        if (ceilingData != null) {
            for (Document ceilingDatum : ceilingData) {
                Integer amount = ceilingDatum.getInteger("amount");
                amounts.put(ceilingDatum.getString("ceilingID"), amount == null ? 0 : amount);
            }
        }
        return amounts;
    }

    public void loadPlayerData(Player player) {

        Document playerDocument = getPlayerDocument(player);

        List<Document> ceilingData = playerDocument.getList("ceilingData", Document.class);
        String uuid = playerDocument.getString("uuid");
        String nickname = playerDocument.getString("nickname");

        HashMap<String, DetailedCeilingDto> detailedCeilingDtos = new HashMap<>();

        for (Document ceilingDatum : ceilingData) {
            String ceilingID = ceilingDatum.getString("ceilingID");
            Integer amount = ceilingDatum.getInteger("amount");
            DetailedCeilingDto detailedCeilingDto = DetailedCeilingDto.builder().ceilingID(ceilingID).amount(amount).build();

            detailedCeilingDtos.put(ceilingID, detailedCeilingDto);

        }

        PlayerCeilingDto playerCeilingDto = PlayerCeilingDto.builder().uuid(uuid).nickname(nickname).ceilingData(detailedCeilingDtos).build();

        playerCeilingDtoMap.put(player, playerCeilingDto);

    }

    // 접속 시, volatile 천장 중 누적량이 목표 이상인 항목에 대해 보상을 소급 지급하고 누적량을 잔여값으로 차감한다.
    // (loadPlayerData 이후 호출해야 캐시가 채워져 있다.)
    public void grantPendingCeilingRewards(Player player) {
        PlayerCeilingDto playerCeilingDto = playerCeilingDtoMap.get(player);
        if (playerCeilingDto == null) {
            return;
        }

        CeilingRepository ceilingRepository = CeilingRepository.getInstance();
        HashMap<String, DetailedCeilingDto> playerCeilingData = playerCeilingDto.getCeilingData();

        boolean changed = false;
        for (Map.Entry<String, DetailedCeilingDto> entry : playerCeilingData.entrySet()) {
            String ceilingID = entry.getKey();
            DetailedCeilingDto detail = entry.getValue();
            int playerAmount = detail.getAmount() == null ? 0 : detail.getAmount();

            int times = ceilingRepository.getPendingRewardTimes(ceilingID, playerAmount);
            if (times <= 0) {
                continue;
            }

            int goal = ceilingRepository.getCeilingData(ceilingID).getAmount();
            int remaining = playerAmount % goal;

            ceilingRepository.giveRewards(player, ceilingID, times);
            detail.setAmount(remaining);
            changed = true;
        }

        // 차감 결과를 즉시 저장해 중복 지급을 방지한다. (다음 접속 시 누적량 < 목표 → times == 0)
        if (changed) {
            savePlayerData(player);
        }
    }

    public Document insertDefaultDocument(Player player) {

        Document document = new Document("uuid", player.getUniqueId().toString())
                .append("nickname", player.getName())
                .append("ceilingData", new ArrayList<>());
        playerCeilingCollection.insertOne(document);
        return document;
    }

    public void savePlayerData(Player player) {
        Document playerDocument = getPlayerDocument(player);

        PlayerCeilingDto playerCacheData = getPlayerCacheData(player);

        HashMap<String, DetailedCeilingDto> ceilingData = playerCacheData.getCeilingData();

        List<Document> ceilingDataList = new ArrayList<>();
        for (String key : ceilingData.keySet()) {
            Document document = new Document()
                    .append("ceilingID", key)
                    .append("amount", ceilingData.get(key).getAmount());
            ceilingDataList.add(document);

        }

        playerDocument.replace("ceilingData", ceilingDataList);

        playerCeilingCollection.replaceOne(new Document("uuid", player.getUniqueId().toString()), playerDocument);
    }

    // 오프라인 플레이어 포함, uuid 로 DB 의 천장 누적량을 절대값 amount 로 설정한다. (로그 재생 기반 소급 복구용)
    // 플레이어 문서가 없으면 nickname 으로 새 문서를 만들고(upsert), 해당 ceilingID 항목이 없으면 새로 추가한다.
    // 반환: { previous(변경 전 누적량, 없었으면 0), createdDocument(문서를 새로 만들었으면 true) }
    public CeilingRestoreResult setCeilingAmountByUuid(String uuid, String nickname, String ceilingID, int amount) {
        Document playerDocument = playerCeilingCollection.find(new Document("uuid", uuid)).first();
        boolean createdDocument = false;
        if (playerDocument == null) {
            playerDocument = new Document("uuid", uuid)
                    .append("nickname", nickname)
                    .append("ceilingData", new ArrayList<Document>());
            createdDocument = true;
        }

        List<Document> ceilingData = playerDocument.getList("ceilingData", Document.class);
        if (ceilingData == null) {
            ceilingData = new ArrayList<>();
        }

        int previous = 0;
        boolean found = false;
        for (Document ceilingDatum : ceilingData) {
            if (ceilingID.equals(ceilingDatum.getString("ceilingID"))) {
                Integer current = ceilingDatum.getInteger("amount");
                previous = current == null ? 0 : current;
                ceilingDatum.put("amount", amount);
                found = true;
                break;
            }
        }
        if (!found) {
            ceilingData.add(new Document("ceilingID", ceilingID).append("amount", amount));
        }

        playerDocument.put("ceilingData", ceilingData);

        if (createdDocument) {
            playerCeilingCollection.insertOne(playerDocument);
        } else {
            playerCeilingCollection.replaceOne(new Document("uuid", uuid), playerDocument);
        }
        return new CeilingRestoreResult(previous, createdDocument);
    }

    public static class CeilingRestoreResult {
        public final int previous;
        public final boolean createdDocument;

        public CeilingRestoreResult(int previous, boolean createdDocument) {
            this.previous = previous;
            this.createdDocument = createdDocument;
        }
    }

    public boolean increaseCeilingAmount(Player player, String ceilingID, Integer amount) {
        Integer current = 0;
        boolean isAlreayExists = false;
        PlayerCeilingDto playerCeilingDto = playerCeilingDtoMap.get(player);
        HashMap<String, DetailedCeilingDto> playerCeilingData = playerCeilingDto.getCeilingData();
        for (String key : playerCeilingData.keySet()) {
            if (key.equals(ceilingID)) {
                current = playerCeilingData.get(key).getAmount() + amount;
                playerCeilingData.get(key).setAmount(current);

                playerCeilingDto.setCeilingData(playerCeilingData);
                playerCeilingDtoMap.put(player, playerCeilingDto);
                isAlreayExists = true;
                break;
            }
        }

        if(!isAlreayExists){
            current = amount;
            DetailedCeilingDto detailedCeilingDto = DetailedCeilingDto.builder().ceilingID(ceilingID).amount(current).build();
            playerCeilingData.put(ceilingID, detailedCeilingDto);

            playerCeilingDto.setCeilingData(playerCeilingData);

            playerCeilingDtoMap.put(player, playerCeilingDto);
        }

        CeilingDataDto ceilingData = CeilingRepository.getInstance().getCeilingData(ceilingID);

        if (current.equals(ceilingData.getAmount())) {
            if(ceilingData.isVolatile()){
                volatileAmount(player, ceilingID, true);
            } else {
                CeilingRepository.getInstance().giveRewards(player, ceilingID);
            }
            return true;
        }
        return false;
    }

    // 코드 구조 잘못됨. 이건 그냥 휘발하겠다로 봐야함.
    public void volatileAmount(Player player, String ceilingID, boolean giveRewards) {

        PlayerCeilingDto playerCeilingDto = playerCeilingDtoMap.get(player);
        HashMap<String, DetailedCeilingDto> playerCeilingData = playerCeilingDto.getCeilingData();



        for (String key : playerCeilingData.keySet()) {
            if (key.equals(ceilingID)) {
                DetailedCeilingDto detailedCeilingDto = playerCeilingData.get(key);
                detailedCeilingDto.setAmount(0);
                playerCeilingData.put(key, detailedCeilingDto);
                playerCeilingDto.setCeilingData(playerCeilingData);
                playerCeilingDtoMap.put(player, playerCeilingDto);
                break;
            }
        }


        if(giveRewards){
            CeilingRepository.getInstance().giveRewards(player, ceilingID);
            return;
        }

    }




}
