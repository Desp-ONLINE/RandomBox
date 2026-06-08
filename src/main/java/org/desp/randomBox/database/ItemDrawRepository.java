package org.desp.randomBox.database;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.desp.randomBox.dto.ItemDrawLogDto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

    // 사용 아이템 이름에 keyword 가 포함된 모든 뽑기 로그를 뽑은 시각(drawDate) 오름차순으로 반환한다.
    // 천장 소급 복구 시 시간 순서대로 재생(replay)하기 위함이므로, 신화/목표 뽑기 기록도 포함해 그대로 가져온다.
    // (drawDate 는 "yyyy-MM-dd HH:mm:ss" 형식이라 문자열 정렬로도 시간순이 보장된다.)
    public List<Document> findDrawLogsSorted(String keyword) {
        Document filter = new Document("usedItem", new Document("$regex", Pattern.quote(keyword)));

        List<Document> logs = new ArrayList<>();
        for (Document doc : itemDrawLog.find(filter).sort(new Document("drawDate", 1))) {
            logs.add(doc);
        }
        return logs;
    }

}
