package org.desp.randomBox.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

/**
 * EtCetera 특수무기 보관함(/특수무기)에 획득권 개수를 적립한다.
 * EtCetera 의 SpecialWeaponTicketRepository 와 같은 컬렉션(EtCetera.SpecialWeaponTicket)과 문서 형식을 쓴다.
 * 문서 형식 : { uuid, nickname, tickets: { "<무기 이름>": 개수 } }
 */
public class SpecialWeaponStorageRepository {

    private static SpecialWeaponStorageRepository instance;
    private final MongoCollection<Document> collection;

    private SpecialWeaponStorageRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.collection = database.getMongoClient().getDatabase("EtCetera").getCollection("SpecialWeaponTicket");
    }

    public static SpecialWeaponStorageRepository getInstance() {
        if (instance == null) {
            instance = new SpecialWeaponStorageRepository();
        }
        return instance;
    }

    public void add(String uuid, String nickname, String weaponName, int amount) {
        if (amount <= 0) return;
        collection.updateOne(
                new Document("uuid", uuid),
                new Document("$inc", new Document("tickets." + weaponName, amount))
                        .append("$set", new Document("nickname", nickname)),
                new UpdateOptions().upsert(true));
    }
}
