package org.desp.randomBox.database;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bukkit.entity.Player;
import org.desp.randomBox.dto.CeilingDataDto;
import org.desp.randomBox.dto.DetailedCeilingDto;
import org.desp.randomBox.dto.PlayerCeilingDto;

import java.util.ArrayList;
import java.util.HashMap;
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

    public void increaseCeilingAmount(Player player, String ceilingID, Integer amount) {
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
            DetailedCeilingDto detailedCeilingDto = DetailedCeilingDto.builder().ceilingID(ceilingID).amount(amount).build();
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
        }
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
