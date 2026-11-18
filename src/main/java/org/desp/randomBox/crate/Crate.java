package org.desp.randomBox.crate;

import lombok.Getter;
import lombok.Setter;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RandomBox 자체 상자. EC 상자 설정을 그대로 옮겨 담는다.
 * Mongo(RandomBox.CrateData) 문서 하나가 상자 하나다.
 */
@Getter
@Setter
public class Crate {

    private String id;
    /** MiniMessage 형식 이름. */
    private String name;

    /** 블록 상자를 열 때 손에 들어야 하는 MMOItems 열쇠 ID. 비어 있으면 열쇠 없이 열린다. */
    private String keyItemId;
    /** 열쇠가 없을 때 안내에 쓰는 열쇠 이름. */
    private String keyName;
    /** 이 퀘스트 데이터가 있어야 블록 상자를 열 수 있다. 0 이하면 검사하지 않는다. */
    private int requiredQuest;

    /** "x,y,z,world" 형식 블록 위치 목록. */
    private List<String> positions = new ArrayList<>();

    private boolean hologramEnabled = true;
    private String hologramTemplate = "default";
    private double hologramYOffset;

    /** 블록 주변 파티클 모양(HELIX, BEACON, NONE 등). */
    private String effectModel = "NONE";
    private String effectParticle = "CLOUD";

    private boolean animationEnabled;
    private String animationId = "mystery";

    /** default 는 확률 표시, nochance 는 등급만 표시. */
    private String previewId = "default";

    private boolean milestonesRepeatable = true;
    private List<CrateMilestone> milestones = new ArrayList<>();

    private Map<String, CrateReward> rewards = new LinkedHashMap<>();

    public CrateReward getReward(String rewardId) {
        return rewardId == null ? null : rewards.get(rewardId);
    }

    public boolean hasMilestones() {
        return !milestones.isEmpty();
    }

    public int getMaxMilestone() {
        int max = 0;
        for (CrateMilestone milestone : milestones) {
            max = Math.max(max, milestone.getOpenings());
        }
        return max;
    }

    public boolean requiresKey() {
        return keyItemId != null && !keyItemId.isEmpty();
    }

    // ------------------------------------------------------------ 확률

    /** EC와 같은 방식으로 뽑는다. 먼저 등급을 등급 가중치로 고르고, 그 등급 안에서 보상 가중치로 고른다. */
    public CrateReward roll(Map<String, CrateRarity> rarities) {
        Map<String, Double> rarityWeights = rarityWeights(rarities);
        String rarity = pick(rarityWeights);
        if (rarity == null) {
            return null;
        }
        Map<CrateReward, Double> rewardWeights = new LinkedHashMap<>();
        for (CrateReward reward : rewards.values()) {
            if (reward.getRarity().equalsIgnoreCase(rarity) && reward.getWeight() > 0) {
                rewardWeights.put(reward, reward.getWeight());
            }
        }
        return pick(rewardWeights);
    }

    /** 보상이 실제로 걸려 있는 등급만 후보가 된다. */
    private Map<String, Double> rarityWeights(Map<String, CrateRarity> rarities) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (CrateReward reward : rewards.values()) {
            if (reward.getWeight() <= 0) {
                continue;
            }
            String key = reward.getRarity().toLowerCase();
            CrateRarity rarity = rarities.get(key);
            double weight = rarity == null ? 1.0 : rarity.getWeight();
            if (weight > 0) {
                weights.put(key, weight);
            }
        }
        return weights;
    }

    /** 이 보상이 나올 확률(%). */
    public double rewardChance(CrateReward reward, Map<String, CrateRarity> rarities) {
        if (reward.getWeight() <= 0) {
            return 0;
        }
        double rarityChance = rarityChance(reward.getRarity(), rarities) / 100.0;
        double sum = 0;
        for (CrateReward other : rewards.values()) {
            if (other.getRarity().equalsIgnoreCase(reward.getRarity()) && other.getWeight() > 0) {
                sum += other.getWeight();
            }
        }
        if (sum <= 0) {
            return 0;
        }
        return rarityChance * (reward.getWeight() / sum) * 100.0;
    }

    /** 이 등급이 뽑힐 확률(%). */
    public double rarityChance(String rarityId, Map<String, CrateRarity> rarities) {
        Map<String, Double> weights = rarityWeights(rarities);
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        Double weight = weights.get(rarityId.toLowerCase());
        if (weight == null || total <= 0) {
            return 0;
        }
        return weight / total * 100.0;
    }

    private static <T> T pick(Map<T, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return null;
        }
        double value = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0;
        T last = null;
        for (Map.Entry<T, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            last = entry.getKey();
            if (value < cumulative) {
                return entry.getKey();
            }
        }
        return last;
    }

    // ------------------------------------------------------------ Mongo

    public Document toDocument() {
        List<Document> rewardDocs = new ArrayList<>();
        for (CrateReward reward : rewards.values()) {
            rewardDocs.add(reward.toDocument());
        }
        List<Document> milestoneDocs = new ArrayList<>();
        for (CrateMilestone milestone : milestones) {
            milestoneDocs.add(milestone.toDocument());
        }
        return new Document("crate_id", id)
                .append("name", name)
                .append("key_item", keyItemId)
                .append("key_name", keyName)
                .append("required_quest", requiredQuest)
                .append("positions", positions)
                .append("hologram", new Document("enabled", hologramEnabled)
                        .append("template", hologramTemplate)
                        .append("y_offset", hologramYOffset))
                .append("effect", new Document("model", effectModel).append("particle", effectParticle))
                .append("animation", new Document("enabled", animationEnabled).append("id", animationId))
                .append("preview", previewId)
                .append("milestones", new Document("repeatable", milestonesRepeatable).append("list", milestoneDocs))
                .append("rewards", rewardDocs);
    }

    @SuppressWarnings("unchecked")
    public static Crate fromDocument(Document document) {
        Crate crate = new Crate();
        crate.setId(document.getString("crate_id"));
        crate.setName(document.getString("name") == null ? crate.getId() : document.getString("name"));
        crate.setKeyItemId(document.getString("key_item"));
        crate.setKeyName(document.getString("key_name"));
        Number quest = document.get("required_quest", Number.class);
        crate.setRequiredQuest(quest == null ? 0 : quest.intValue());

        List<String> positions = (List<String>) document.get("positions");
        crate.setPositions(positions == null ? new ArrayList<>() : new ArrayList<>(positions));

        Document hologram = document.get("hologram", Document.class);
        if (hologram != null) {
            crate.setHologramEnabled(!Boolean.FALSE.equals(hologram.getBoolean("enabled")));
            crate.setHologramTemplate(hologram.getString("template") == null ? "default" : hologram.getString("template"));
            Number offset = hologram.get("y_offset", Number.class);
            crate.setHologramYOffset(offset == null ? 0 : offset.doubleValue());
        }

        Document effect = document.get("effect", Document.class);
        if (effect != null) {
            crate.setEffectModel(effect.getString("model") == null ? "NONE" : effect.getString("model"));
            crate.setEffectParticle(effect.getString("particle") == null ? "CLOUD" : effect.getString("particle"));
        }

        Document animation = document.get("animation", Document.class);
        if (animation != null) {
            crate.setAnimationEnabled(Boolean.TRUE.equals(animation.getBoolean("enabled")));
            crate.setAnimationId(animation.getString("id") == null ? "mystery" : animation.getString("id"));
        }

        crate.setPreviewId(document.getString("preview") == null ? "default" : document.getString("preview"));

        Document milestones = document.get("milestones", Document.class);
        if (milestones != null) {
            crate.setMilestonesRepeatable(!Boolean.FALSE.equals(milestones.getBoolean("repeatable")));
            List<Document> list = (List<Document>) milestones.get("list");
            if (list != null) {
                for (Document milestoneDoc : list) {
                    crate.getMilestones().add(CrateMilestone.fromDocument(milestoneDoc));
                }
            }
        }

        List<Document> rewardDocs = (List<Document>) document.get("rewards");
        if (rewardDocs != null) {
            for (Document rewardDoc : rewardDocs) {
                CrateReward reward = CrateReward.fromDocument(rewardDoc);
                crate.getRewards().put(reward.getId(), reward);
            }
        }
        return crate;
    }
}
