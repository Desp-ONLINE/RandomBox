package org.desp.randomBox.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.desp.randomBox.dto.BoxDataDto;
import org.desp.randomBox.dto.ItemDataDto;


public class BoxDataRepository {

    private static BoxDataRepository instance;
    private final MongoCollection<Document> boxData;
    public static Map<String, BoxDataDto> boxDataMap = new HashMap<>();

    public BoxDataRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.boxData = database.getDatabase().getCollection("BoxData");
        loadBoxData();
    }

    public static BoxDataRepository getInstance() {
        if (instance == null) {
            instance = new BoxDataRepository();
        }
        return instance;
    }

    public void loadBoxData() {
        FindIterable<Document> documents = boxData.find();
        for (Document document : documents) {
            String box_id = document.getString("box_id");
            List<ItemDataDto> availableItemList = new ArrayList<>();

            List<Document> availableItems = (List<Document>) document.get("availableItems");
            for (Document item : availableItems) {
                String[] split = item.getString("item_id").split(":");

                ItemDataDto itemDataDto = ItemDataDto.builder()
                        .item_id(split[1])
                        .type(split[0])
                        .amount(item.getInteger("amount"))
                        .chance(item.getInteger("chance"))
                        .notice(item.getBoolean("notice"))
                        .build();
                availableItemList.add(itemDataDto);
            }
            BoxDataDto boxDataDto = BoxDataDto.builder()
                    .box_id(box_id)
                    .availableItems(availableItemList)
                    .build();

            boxDataMap.put(box_id, boxDataDto);
        }
    }

    public Map<String, BoxDataDto> getBoxData() {
        return boxDataMap;
    }
}
