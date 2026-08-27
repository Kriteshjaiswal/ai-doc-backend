package com.aidocqa.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentBookmarkDto {

    private String id;
    private Integer page;
    private String label;
    private String snippet;
    private LocalDateTime createdAt;
}
