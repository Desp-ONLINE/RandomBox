package org.desp.randomBox.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.desp.randomBox.boxUtils.DateUtils;

import java.util.ArrayList;
import java.util.List;

// 관리자 전용: 특정 유저가 다음에 여는 특정 박스의 보상을 강제로 지정한다.
// 규칙:
//  - 대상 유저에게는 어떤 표시도 하지 않는다. 지급 메시지/브로드캐스트/뽑기 로그 모두 일반 뽑기와 동일하다.
//  - 1회 소모형: 강제 보상이 적용되면 설정은 자동 삭제된다. (consume 이 원자적으로 조회+삭제)
//  - Mongo 에 저장하므로 서버 재시작 및 타 서버(공유 DB)에서도 유지된다.
public class ForcedRewardRepository {

    private static ForcedRewardRepository instance;
    private final MongoCollection<Document> forcedRewardData;

    private ForcedRewardRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.forcedRewardData = database.getDatabase().getCollection("ForcedRewardData");
        this.forcedRewardData.createIndex(Indexes.ascending("user_id_lower", "box_id"));
    }

    public static ForcedRewardRepository getInstance() {
        if (instance == null) {
            instance = new ForcedRewardRepository();
        }
        return instance;
    }

    private Bson keyFilter(String userId, String boxId) {
        return Filters.and(
                Filters.eq("user_id_lower", userId.toLowerCase()),
                Filters.eq("box_id", boxId)
        );
    }

    public void set(String userId, String boxId, String rewardId, String setBy) {
        Document document = new Document()
                .append("user_id", userId)
                .append("user_id_lower", userId.toLowerCase())
                .append("box_id", boxId)
                .append("reward_id", rewardId)
                .append("set_by", setBy)
                .append("set_date", DateUtils.getCurrentTime());
        forcedRewardData.replaceOne(keyFilter(userId, boxId), document, new ReplaceOptions().upsert(true));
    }

    public boolean remove(String userId, String boxId) {
        return forcedRewardData.deleteOne(keyFilter(userId, boxId)).getDeletedCount() > 0;
    }

    public List<Document> list() {
        List<Document> result = new ArrayList<>();
        forcedRewardData.find().into(result);
        return result;
    }

    // 해당 유저+박스의 강제 보상 설정을 원자적으로 가져오면서 삭제한다. 없으면 null.
    // DB 오류 시에도 null 을 반환해 일반 뽑기로 진행되도록 한다. (뽑기 흐름을 절대 막지 않는다)
    public Document consume(String userId, String boxId) {
        try {
            return forcedRewardData.findOneAndDelete(keyFilter(userId, boxId));
        } catch (Exception e) {
            return null;
        }
    }

    // 천장 시스템 등으로 해당 뽑기가 취소(미지급)된 경우 설정을 되돌려 다음 뽑기에 다시 적용되게 한다.
    public void restore(Document document) {
        try {
            forcedRewardData.insertOne(document);
        } catch (Exception ignored) {
        }
    }
}
