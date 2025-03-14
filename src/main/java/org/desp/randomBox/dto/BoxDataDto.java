package org.desp.randomBox.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class BoxDataDto {
    private String box_id;
    private List<ItemDataDto> availableItems;
}
