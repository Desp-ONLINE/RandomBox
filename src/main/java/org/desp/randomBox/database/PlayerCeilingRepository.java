package org.desp.randomBox.database;

import com.binggre.mmomail.MMOMail;
import com.binggre.mmomail.objects.Mail;
import com.mongodb.client.MongoCollection;
import net.Indyuce.mmoitems.MMOItems;
import org.bson.Document;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.dto.BoxDataDto;
import org.desp.randomBox.dto.CeilingDataDto;
import org.desp.randomBox.dto.DetailedCeilingDto;
import org.desp.randomBox.dto.PlayerCeilingDto;

import java.util.*;


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
                playerCeilingData.get(key).setAmount(playerCeilingData.get(key).getAmount() + amount);
                current = playerCeilingData.get(key).getAmount() + amount;

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
            volatileAmount(player, ceilingID, ceilingData.isVolatile());
        }
    }

    public void volatileAmount(Player player, String ceilingID, boolean isVolatile) {
        if (!isVolatile) {
            return;
        }
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

    }

    public void giveRewards(Player player, String ceilingID) {
        CeilingDataDto ceilingData = CeilingRepository.getInstance().getCeilingData(ceilingID);

        List<String> rewardData = ceilingData.getRewardData();

        List<ItemStack> mailItems = new ArrayList<>();

        for (String rewardDatum : rewardData) {
            String[] split = rewardDatum.split(":");
            String type = split[0];
            String mmoitemID = split[1];
            Integer amount = Integer.valueOf(split[2]);

            ItemStack item = MMOItems.plugin.getItem(type, mmoitemID);
            item.setAmount(amount);

            mailItems.add(item);
        }

        Mail mail = MMOMail.getInstance().getMailAPI().createMail("시스템", ceilingID + " 천장 시스템 보상입니다.", 0, mailItems);
        MMOMail.getInstance().getMailAPI().sendMail(player.getName(), mail);
    }


}
