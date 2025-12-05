package org.desp.randomBox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@Builder
public class BoxDataDto {
    private String box_id;
    private List<ItemDataDto> availableItems;
}
