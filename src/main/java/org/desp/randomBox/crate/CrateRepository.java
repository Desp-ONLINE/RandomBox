package org.desp.randomBox.crate;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.desp.randomBox.database.DatabaseRegister;

import java.util.ArrayList;
import java.util.List;

/** 상자 설정 저장소. 컬렉션 RandomBox.CrateData 에 상자 하나당 문서 하나를 둔다. */
public class CrateRepository {

    private static CrateRepository instance;
    private final MongoCollection<Document> collection;

    private CrateRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.collection = database.getDatabase().getCollection("CrateData");
    }

    public static CrateRepository getInstance() {
        if (instance == null) {
            instance = new CrateRepository();
        }
        return instance;
    }

    public List<Crate> loadAll() {
        List<Crate> crates = new ArrayList<>();
        for (Document document : collection.find()) {
            if (document.getString("crate_id") == null) {
                continue;
            }
            crates.add(Crate.fromDocument(document));
        }
        return crates;
    }

    public void save(Crate crate) {
        collection.replaceOne(Filters.eq("crate_id", crate.getId()), crate.toDocument(), new ReplaceOptions().upsert(true));
    }

    public void savePositions(Crate crate) {
        collection.updateOne(Filters.eq("crate_id", crate.getId()),
                new Document("$set", new Document("positions", crate.getPositions())));
    }
}
