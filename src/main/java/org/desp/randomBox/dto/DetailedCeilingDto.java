package org.desp.randomBox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class DetailedCeilingDto {
    private String ceilingID;
    private Integer amount;
}
