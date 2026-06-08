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
            List<String> goalString = document.getList("goalString", String.class);
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

    // volatile 천장에서 누적량이 목표 이상이면, 지급해야 할 보상 횟수(목표 단위)를 반환. 그 외엔 0.
    public int getPendingRewardTimes(String ceilingID, int playerAmount) {
        CeilingDataDto ceilingData = getCeilingData(ceilingID);
        if (ceilingData == null || !ceilingData.isVolatile()) {
            return 0;
        }
        Integer goal = ceilingData.getAmount();
        if (goal == null || goal <= 0 || playerAmount < goal) {
            return 0;
        }
        return playerAmount / goal;
    }

    // 보상 횟수만큼 곱한 천장 보상 아이템 목록을 생성. (미리보기/지급 공용)
    public List<ItemStack> buildRewardItems(String ceilingID, int times) {
        List<ItemStack> items = new ArrayList<>();
        CeilingDataDto ceilingData = getCeilingData(ceilingID);
        if (ceilingData == null || ceilingData.getRewardData() == null || times <= 0) {
            return items;
        }
        for (String rewardDatum : ceilingData.getRewardData()) {
            String[] split = rewardDatum.split(":");
            ItemStack item = MMOItems.plugin.getItem(split[0], split[1]);
            if (item == null) {
                continue;
            }
            item.setAmount(Integer.parseInt(split[2]) * times);
            items.add(item);
        }
        return items;
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

    // 누적된 천장 보상을 한 번에 times 회분 지급한다. (접속 시 소급 지급 / 미리보기와 동일한 계산)
    public void giveRewards(Player player, String ceilingID, int times) {
        if (times <= 0) {
            return;
        }
        CeilingDataDto ceilingData = getCeilingData(ceilingID);
        if (ceilingData == null) {
            return;
        }
        String name = ceilingData.getName();

        List<ItemStack> mailItems = buildRewardItems(ceilingID, times);
        if (mailItems.isEmpty()) {
            return;
        }

        Mail mail = MMOMail.getInstance().getMailAPI().createMail("시스템", name + " 천장 시스템 보상입니다.", 0, mailItems);
        MMOMail.getInstance().getMailAPI().sendMail(player.getName(), mail);

        player.playSound(player, "uisounds:hugewin", 1, 1);
        player.sendMessage(ColorManager.format("#54daf4§n                                                                                                               §f"));
        player.sendMessage(ColorManager.format(""));
        player.sendMessage(ColorManager.format("#54daf4 "+name+" 천장 시스템의 누적 보상 §e"+times+"회분§f이 지급되었습니다. §7§o(/메일함 또는 /ㅁ)"));
        player.sendMessage("§f");
        for (ItemStack mailItem : mailItems) {
            String displayName = (mailItem.getItemMeta() != null && mailItem.getItemMeta().hasDisplayName())
                    ? mailItem.getItemMeta().getDisplayName()
                    : mailItem.getType().name();
            player.sendMessage("   §e- "+displayName+" §fx"+mailItem.getAmount());
        }
        player.sendMessage(ColorManager.format("#54daf4§n                                                                                                               §f"));
    }



}
