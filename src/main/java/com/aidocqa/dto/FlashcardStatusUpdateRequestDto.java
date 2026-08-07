package com.aidocqa.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardStatusUpdateRequestDto {

    private String status; // mastered, need_revision, unseen
    private Boolean isFavorite;
}
