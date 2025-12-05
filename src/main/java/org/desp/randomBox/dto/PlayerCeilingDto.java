package org.desp.randomBox.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@Builder
public class PlayerCeilingDto {
    private String nickname;
    private String uuid;
    private HashMap<String, DetailedCeilingDto> ceilingData;
}
