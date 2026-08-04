package com.aidocqa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDto {

    @NotNull(message = "Document ID is required")
    private Long documentId;

    @NotBlank(message = "Question cannot be blank")
    private String question;
}
