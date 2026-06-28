package com.search.dto.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterApiResponse<T> {

    private boolean success;
    private int status;
    private String message;
    private T data;
}
