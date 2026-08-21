package com.aidocqa.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthLoginRequestDto {
    private String code;
    private String redirectUri;
    private String idToken;
}
