package org.desp.randomBox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class CrateBoxDto {
    private String box_id;
    private String crate_id;
}
