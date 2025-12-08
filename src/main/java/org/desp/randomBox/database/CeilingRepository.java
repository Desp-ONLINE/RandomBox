package org.desp.randomBox.database;

import com.binggre.binggreapi.utils.ColorManager;
import com.binggre.mmomail.MMOMail;
import com.binggre.mmomail.objects.Mail;
import com.mongodb.client.MongoCollection;
import net.Indyuce.mmoitems.MMOItems;
import org.bson.Document;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.desp.randomBox.dto.CeilingDataDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CeilingRepository {

    private static CeilingRepository instance;
    private final MongoCollection<Document> ceilingDataCollection;
    private final MongoCollection<Document> ceilingKeysCollection;
    public static Map<String, CeilingDataDto> ceilingDataDtoMap = new HashMap<>();
    public static Map<String, List> ceilingKeyCache = new HashMap<>();

    public CeilingRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.ceilingDataCollection = database.getDatabase().getCollection("CeilingData");
        this.ceilingKeysCollection = database.getDatabase().getCollection("CeilingKeys");
        loadCeilingData();
    }

    public static CeilingRepository getInstance() {
        if (instance == null) {
            instance = new CeilingRepository();
        }
        return instance;
    }

    public void loadCeilingData(){
        for (Document document : ceilingDataCollection.find()) {
            String ceilingID = document.getString("ceilingID");
            Integer amount = document.getInteger("amount");
            Boolean isVolatile = document.getBoolean("isVolatile");
            String type = document.getString("type");
            String name = document.getString("name");
            String goalString = document.getString("goalString");
            List<String> rewardData = document.getList("rewardData", String.class);

            CeilingDataDto ceilingDataDto = CeilingDataDto.builder().name(name).ceilingID(ceilingID).amount(amount).isVolatile(isVolatile).rewardData(rewardData).goalString(goalString).type(type).build();

            ceilingDataDtoMap.put(ceilingID, ceilingDataDto);
        }

        for (Document document : ceilingKeysCollection.find()) {
            String type = document.getString("type");
            List<String> keys = document.getList("keys", String.class);

            ceilingKeyCache.put(type, keys);
        }

    }

    public List<String> getRandomboxKeys(){
        return ceilingKeyCache.get("randombox");

    }

    public boolean isContainedKeys(String type, String key){
        return ceilingKeyCache.get(type).contains(key);
    }



    public CeilingDataDto getCeilingData(String ceilingID){
        return ceilingDataDtoMap.get(ceilingID);
    }

    public void giveRewards(Player player, String ceilingID) {
        CeilingDataDto ceilingData = CeilingRepository.getInstance().getCeilingData(ceilingID);

        List<String> rewardData = ceilingData.getRewardData();
        String name = ceilingData.getName();


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

        Mail mail = MMOMail.getInstance().getMailAPI().createMail("시스템", name + " 천장 시스템 보상입니다.", 0, mailItems);
        MMOMail.getInstance().getMailAPI().sendMail(player.getName(), mail);


        player.playSound(player, "uisounds:hugewin", 1, 1);
        player.sendMessage(ColorManager.format("#54daf4§n                                                                                                               §f"));
        player.sendMessage(ColorManager.format(""));
        player.sendMessage(ColorManager.format("#54daf4 "+name+" 천장 시스템의 목표가 달성되어 보상이 지급되었습니다. §7§o(/메일함 또는 /ㅁ)"));
        player.sendMessage("§f");
        for (ItemStack mailItem : mailItems) {
            player.sendMessage("   §e- "+mailItem.getItemMeta().getDisplayName()+" §fx"+mailItem.getAmount());
        }
        player.sendMessage(ColorManager.format("#54daf4§n                                                                                                               §f"));

    }



}
