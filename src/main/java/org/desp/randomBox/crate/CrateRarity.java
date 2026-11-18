package org.desp.randomBox.crate;

import lombok.Getter;

/** 보상 등급. EC config.yml 의 Rewards.Rarities 와 같은 값이다. */
@Getter
public class CrateRarity {

    private final String id;
    private final String name;
    private final double weight;

    public CrateRarity(String id, String name, double weight) {
        this.id = id;
        this.name = name;
        this.weight = weight;
    }
}
