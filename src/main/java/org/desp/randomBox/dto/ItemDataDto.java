package org.desp.randomBox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class ItemDataDto {
    private String item_id;
    private String type;
    private int amount;
    private int chance;
    private boolean notice;
}
