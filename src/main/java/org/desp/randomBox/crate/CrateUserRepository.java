package org.desp.randomBox.crate;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.desp.randomBox.RandomBox;
import org.desp.randomBox.database.DatabaseRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 유저별 상자 기록(누적 오픈 수, 마일스톤 진행도) 저장소.
 * 컬렉션 RandomBox.CrateUserData : { uuid, user_id, crates: { "<상자ID>": { openings, milestone } } }
 * 접속할 때 비동기로 불러오고, 값이 바뀔 때마다 해당 상자 필드만 비동기로 저장한다.
 */
public class CrateUserRepository {

    private static CrateUserRepository instance;
    private final MongoCollection<Document> collection;
    private final Map<UUID, Map<String, CrateProgress>> cache = new ConcurrentHashMap<>();

    public static final class CrateProgress {
        public int openings;
        public int milestone;

        public CrateProgress(int openings, int milestone) {
            this.openings = openings;
            this.milestone = milestone;
        }
    }

    private CrateUserRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.collection = database.getDatabase().getCollection("CrateUserData");
    }

    public static CrateUserRepository getInstance() {
        if (instance == null) {
            instance = new CrateUserRepository();
        }
        return instance;
    }

    public boolean isLoaded(Player player) {
        return cache.containsKey(player.getUniqueId());
    }

    public void loadAsync(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(RandomBox.getInstance(), () -> {
            Map<String, CrateProgress> data = read(uuid);
            if (Bukkit.getPlayer(uuid) != null) {
                cache.put(uuid, data);
            }
        });
    }

    /** DB에서 바로 읽는다. 비동기 스레드에서만 호출한다. */
    public Map<String, CrateProgress> read(UUID uuid) {
        Map<String, CrateProgress> data = new ConcurrentHashMap<>();
        Document document = collection.find(Filters.eq("uuid", uuid.toString())).first();
        if (document != null) {
            Document crates = document.get("crates", Document.class);
            if (crates != null) {
                for (String crateId : crates.keySet()) {
                    Document progress = crates.get(crateId, Document.class);
                    if (progress == null) {
                        continue;
                    }
                    data.put(crateId, new CrateProgress(intOf(progress, "openings"), intOf(progress, "milestone")));
                }
            }
        }
        return data;
    }

    public void unload(Player player) {
        cache.remove(player.getUniqueId());
    }

    /**
     * 캐시된 기록을 돌려준다. 아직 불러오지 않았으면 저장되지 않는 빈 값을 돌려준다.
     * (불러오기 전에 빈 기록이 캐시에 들어가 실제 기록을 덮어쓰는 일을 막는다)
     */
    public CrateProgress get(Player player, String crateId) {
        Map<String, CrateProgress> data = cache.get(player.getUniqueId());
        if (data == null) {
            return new CrateProgress(0, 0);
        }
        return data.computeIfAbsent(crateId, k -> new CrateProgress(0, 0));
    }

    public void saveAsync(Player player, String crateId) {
        if (!isLoaded(player)) {
            return;
        }
        CrateProgress progress = get(player, crateId);
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        int openings = progress.openings;
        int milestone = progress.milestone;
        CrateOpenService.runAsync(() ->
                collection.updateOne(Filters.eq("uuid", uuid),
                        new Document("$set", new Document("user_id", name)
                                .append("crates." + crateId + ".openings", openings)
                                .append("crates." + crateId + ".milestone", milestone)),
                        new UpdateOptions().upsert(true)));
    }

    /** EC 기록 이전용 일괄 저장. 비동기 스레드에서만 호출한다. */
    public int bulkImport(List<ImportRow> rows) {
        List<WriteModel<Document>> models = new ArrayList<>();
        for (ImportRow row : rows) {
            Document set = new Document("user_id", row.userName);
            for (Map.Entry<String, CrateProgress> entry : row.crates.entrySet()) {
                set.append("crates." + entry.getKey() + ".openings", entry.getValue().openings)
                        .append("crates." + entry.getKey() + ".milestone", entry.getValue().milestone);
            }
            models.add(new UpdateOneModel<>(Filters.eq("uuid", row.uuid), new Document("$set", set), new UpdateOptions().upsert(true)));
        }
        int written = 0;
        for (int i = 0; i < models.size(); i += 500) {
            collection.bulkWrite(models.subList(i, Math.min(models.size(), i + 500)));
            written += Math.min(models.size(), i + 500) - i;
        }
        return written;
    }

    public void replaceCache(UUID uuid, Map<String, CrateProgress> data) {
        if (Bukkit.getPlayer(uuid) != null) {
            cache.put(uuid, data);
        }
    }

    public static final class ImportRow {
        final String uuid;
        final String userName;
        final Map<String, CrateProgress> crates;

        public ImportRow(String uuid, String userName, Map<String, CrateProgress> crates) {
            this.uuid = uuid;
            this.userName = userName;
            this.crates = crates;
        }
    }

    private static int intOf(Document document, String key) {
        Number number = document.get(key, Number.class);
        return number == null ? 0 : number.intValue();
    }
}
