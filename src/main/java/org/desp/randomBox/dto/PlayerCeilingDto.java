package org.desp.randomBox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@Getter @Setter
@Builder
public class PlayerCeilingDto {
    private String nickname;
    private String uuid;
    private HashMap<String, DetailedCeilingDto> ceilingData;
}
