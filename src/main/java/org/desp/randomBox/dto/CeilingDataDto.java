package org.desp.randomBox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@Builder
public class CeilingDataDto {
    private String ceilingID;
    private Integer amount;
    private boolean isVolatile; // true면 천장 획득해도 천장 수 감소 안해서 더 이상 획득 불가.
    private List<String> rewardData;
}
