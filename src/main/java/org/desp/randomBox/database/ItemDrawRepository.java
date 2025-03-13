package org.desp.randomBox.database;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.desp.randomBox.dto.ItemDrawLogDto;

public class ItemDrawRepository {

    private static ItemDrawRepository instance;
    private final MongoCollection<Document> itemDrawLog;

    public ItemDrawRepository() {
        DatabaseRegister database = new DatabaseRegister();
        itemDrawLog = database.getDatabase().getCollection("ItemDrawLog");
    }

    public static ItemDrawRepository getInstance() {
        if (instance == null) {
            instance = new ItemDrawRepository();
        }
        return instance;
    }

    public void insertDrawLog(ItemDrawLogDto dto, String currentTime) {
        Document document = new Document()
                .append("user_id", dto.getUser_id())
                .append("uuid", dto.getUuid())
                .append("usedItem", dto.getUsedItem())
                .append("drawItem", dto.getDrawItem())
                .append("drawDate", currentTime);

        itemDrawLog.insertOne(document);
    }



}
