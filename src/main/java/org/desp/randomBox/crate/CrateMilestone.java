package org.desp.randomBox.crate;

import lombok.Getter;
import org.bson.Document;

/** 누적 오픈 보상. openings 번째로 열었을 때 reward_id 보상을 한 번 더 받는다. */
@Getter
public class CrateMilestone {

    private final int openings;
    private final String rewardId;

    public CrateMilestone(int openings, String rewardId) {
        this.openings = openings;
        this.rewardId = rewardId;
    }

    public Document toDocument() {
        return new Document("openings", openings).append("reward_id", rewardId);
    }

    public static CrateMilestone fromDocument(Document document) {
        Number openings = document.get("openings", Number.class);
        return new CrateMilestone(openings == null ? 0 : openings.intValue(), document.getString("reward_id"));
    }
}
