package com.aidocqa.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthConfigDto {
    private String googleClientId;
    private String githubClientId;
    private boolean googleConfigured;
    private boolean githubConfigured;
}
