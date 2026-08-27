package com.aidocqa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRenameRequestDto {

    @NotBlank(message = "Document filename cannot be blank")
    private String newFileName;
}
