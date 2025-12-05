package org.desp.randomBox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DetailedCeilingDto {
    private String ceilingID;
    private Integer amount;
}
