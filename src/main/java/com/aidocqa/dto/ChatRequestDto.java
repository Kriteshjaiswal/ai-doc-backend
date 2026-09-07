package com.aidocqa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDto {

    private Long documentId;

    @NotBlank(message = "Question cannot be blank")
    private String question;

    /**
     * Optional response depth override: "LOW", "MEDIUM", or "HIGH"
     */
    private String responseDepth;
}
