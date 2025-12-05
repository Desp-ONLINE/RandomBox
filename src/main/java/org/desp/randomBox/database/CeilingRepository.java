package org.desp.randomBox.database;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.desp.randomBox.dto.CeilingDataDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CeilingRepository {

    private static CeilingRepository instance;
    private final MongoCollection<Document> ceilingDataCollection;
    public static Map<String, CeilingDataDto> ceilingDataDtoMap = new HashMap<>();

    public CeilingRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.ceilingDataCollection = database.getDatabase().getCollection("CeilingData");
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
            List<String> rewardData = document.getList("rewardData", String.class);

            CeilingDataDto ceilingDataDto = CeilingDataDto.builder().ceilingID(ceilingID).amount(amount).isVolatile(isVolatile).rewardData(rewardData).build();

            ceilingDataDtoMap.put(ceilingID, ceilingDataDto);
        }

    }

    public CeilingDataDto getCeilingData(String ceilingID){
        return ceilingDataDtoMap.get(ceilingID);
    }



}
