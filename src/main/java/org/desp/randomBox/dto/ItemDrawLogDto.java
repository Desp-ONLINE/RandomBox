package org.desp.randomBox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class ItemDrawLogDto {
    private String user_id;
    private String uuid;
    private String usedItem;
    private String drawItem;
    private String drawDate;
}
