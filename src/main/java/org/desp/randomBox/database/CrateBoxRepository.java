package org.desp.randomBox.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.desp.randomBox.dto.CrateBoxDto;

import java.util.HashMap;
import java.util.Map;

public class CrateBoxRepository {

    private static CrateBoxRepository instance;
    private final MongoCollection<Document> crateBoxData;
    public static Map<String, CrateBoxDto> crateBoxMap = new HashMap<>();

    public CrateBoxRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.crateBoxData = database.getDatabase().getCollection("CrateBoxData");
        loadCrateBoxData();
    }

    public static CrateBoxRepository getInstance() {
        if (instance == null) {
            instance = new CrateBoxRepository();
        }
        return instance;
    }

    public void loadCrateBoxData() {
        FindIterable<Document> documents = crateBoxData.find();
        for (Document document : documents) {
            String box_id = document.getString("box_id");
            String crate_id = document.getString("crate_id");

            CrateBoxDto dto = CrateBoxDto.builder()
                    .box_id(box_id)
                    .crate_id(crate_id)
                    .build();

            crateBoxMap.put(box_id, dto);
        }
    }

    public Map<String, CrateBoxDto> getCrateBoxData() {
        return crateBoxMap;
    }

    public CrateBoxDto get(String boxId) {
        return crateBoxMap.get(boxId);
    }
}
