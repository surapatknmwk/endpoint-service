package com.core.constanst;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Clients {
    MASTER_DATA("","","","");
    private final String URL;
    private final String METHOD;
    private final String group;
    private final String desc;
}
