package com.aidocqa.service;

import com.aidocqa.dto.AuthenticationResponseDto;
import com.aidocqa.dto.LoginRequestDto;
import com.aidocqa.dto.OAuthConfigDto;
import com.aidocqa.dto.OAuthLoginRequestDto;
import com.aidocqa.dto.RegisterRequestDto;
import com.aidocqa.entity.User;
import com.aidocqa.exception.ResourceNotFoundException;
import com.aidocqa.exception.UserAlreadyExistsException;
import com.aidocqa.repository.UserRepository;
import com.aidocqa.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${google.client.id:}")
    private String googleClientId;

    @Value("${google.client.secret:}")
    private String googleClientSecret;

    @Value("${github.client.id:}")
    private String githubClientId;

    @Value("${github.client.secret:}")
    private String githubClientSecret;

    public AuthenticationResponseDto register(RegisterRequestDto request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String normalizedFullName = request.getFullName() != null ? request.getFullName().trim() : "";

        // Check if email already exists
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException("An account with this email already exists. Please sign in instead.");
        }

        User user = User.builder()
                .fullName(normalizedFullName)
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .provider("LOCAL")
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", user.getEmail());

        String jwtToken = jwtService.generateToken(user);

        return AuthenticationResponseDto.builder()
                .token(jwtToken)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }

    public AuthenticationResponseDto login(LoginRequestDto request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            normalizedEmail,
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for email: {}", normalizedEmail);
            throw new BadCredentialsException("Invalid email or password. Please verify your credentials and try again.");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email. Please register first."));

        log.info("User logged in successfully: {}", user.getEmail());

        String jwtToken = jwtService.generateToken(user);

        return AuthenticationResponseDto.builder()
                .token(jwtToken)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }

    public OAuthConfigDto getOAuthConfig() {
        return OAuthConfigDto.builder()
                .googleClientId(googleClientId)
                .githubClientId(githubClientId)
                .googleConfigured(googleClientId != null && !googleClientId.isBlank() && googleClientSecret != null && !googleClientSecret.isBlank())
                .githubConfigured(githubClientId != null && !githubClientId.isBlank() && githubClientSecret != null && !githubClientSecret.isBlank())
                .build();
    }

    public AuthenticationResponseDto oauthLogin(String providerName, OAuthLoginRequestDto request) {
        String normalizedProvider = providerName.toLowerCase();

        if ("google".equals(normalizedProvider)) {
            return handleGoogleOAuth(request);
        } else if ("github".equals(normalizedProvider)) {
            return handleGitHubOAuth(request);
        } else {
            throw new IllegalArgumentException("Unsupported OAuth provider: " + providerName);
        }
    }

    private AuthenticationResponseDto handleGoogleOAuth(OAuthLoginRequestDto request) {
        if (googleClientId == null || googleClientId.isBlank() || googleClientSecret == null || googleClientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth is not configured. Please set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables.");
        }

        try {
            // Exchange code for Google Access Token
            String tokenUrl = "https://oauth2.googleapis.com/token";
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", request.getCode());
            body.add("client_id", googleClientId);
            body.add("client_secret", googleClientSecret);
            body.add("redirect_uri", request.getRedirectUri());
            body.add("grant_type", "authorization_code");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> tokenResponse = restTemplate.postForEntity(tokenUrl, httpEntity, String.class);

            JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());
            String accessToken = tokenJson.path("access_token").asText();

            if (accessToken == null || accessToken.isBlank()) {
                throw new RuntimeException("Failed to obtain Google access token");
            }

            // Get Google User Info
            String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(accessToken);

            ResponseEntity<String> userResponse = restTemplate.exchange(
                    userInfoUrl, HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);

            JsonNode userJson = objectMapper.readTree(userResponse.getBody());
            String email = userJson.path("email").asText();
            String name = userJson.path("name").asText(email.split("@")[0]);
            String sub = userJson.path("sub").asText();

            if (email == null || email.isBlank()) {
                throw new RuntimeException("Google user email not provided");
            }

            return findOrCreateOAuthUser(email, name, "GOOGLE", sub);
        } catch (Exception e) {
            log.error("Google OAuth login failed: {}", e.getMessage(), e);
            throw new RuntimeException("Google OAuth authentication failed: " + e.getMessage());
        }
    }

    private AuthenticationResponseDto handleGitHubOAuth(OAuthLoginRequestDto request) {
        if (githubClientId == null || githubClientId.isBlank() || githubClientSecret == null || githubClientSecret.isBlank()) {
            throw new IllegalStateException("GitHub OAuth is not configured. Please set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET environment variables.");
        }

        try {
            // Exchange code for GitHub Access Token
            String tokenUrl = "https://github.com/login/oauth/access_token";
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", githubClientId);
            body.add("client_secret", githubClientSecret);
            body.add("code", request.getCode());
            body.add("redirect_uri", request.getRedirectUri());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json");

            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> tokenResponse = restTemplate.postForEntity(tokenUrl, httpEntity, String.class);

            JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());
            String accessToken = tokenJson.path("access_token").asText();

            if (accessToken == null || accessToken.isBlank()) {
                throw new RuntimeException("Failed to obtain GitHub access token");
            }

            // Get GitHub User Info
            String userUrl = "https://api.github.com/user";
            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(accessToken);

            ResponseEntity<String> userResponse = restTemplate.exchange(
                    userUrl, HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);

            JsonNode userJson = objectMapper.readTree(userResponse.getBody());
            String providerId = userJson.path("id").asText();
            String name = userJson.path("name").asText(userJson.path("login").asText());
            String email = userJson.path("email").asText();

            // If GitHub email is private, fetch primary verified email
            if (email == null || email.isBlank() || "null".equals(email)) {
                String emailsUrl = "https://api.github.com/user/emails";
                ResponseEntity<String> emailsResponse = restTemplate.exchange(
                        emailsUrl, HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);

                JsonNode emailsJson = objectMapper.readTree(emailsResponse.getBody());
                if (emailsJson.isArray()) {
                    for (JsonNode emailNode : emailsJson) {
                        if (emailNode.path("primary").asBoolean() && emailNode.path("verified").asBoolean()) {
                            email = emailNode.path("email").asText();
                            break;
                        }
                    }
                    if ((email == null || email.isBlank() || "null".equals(email)) && emailsJson.size() > 0) {
                        email = emailsJson.get(0).path("email").asText();
                    }
                }
            }

            if (email == null || email.isBlank() || "null".equals(email)) {
                email = userJson.path("login").asText() + "@github.user";
            }

            return findOrCreateOAuthUser(email, name, "GITHUB", providerId);
        } catch (Exception e) {
            log.error("GitHub OAuth login failed: {}", e.getMessage(), e);
            throw new RuntimeException("GitHub OAuth authentication failed: " + e.getMessage());
        }
    }

    private AuthenticationResponseDto findOrCreateOAuthUser(String email, String name, String provider, String providerId) {
        Optional<User> existingUser = userRepository.findByEmail(email);
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            if (user.getProvider() == null || "LOCAL".equals(user.getProvider())) {
                user.setProvider(provider);
                user.setProviderId(providerId);
                userRepository.save(user);
            }
            log.info("OAuth login for existing user: {} ({})", email, provider);
        } else {
            user = User.builder()
                    .email(email)
                    .fullName(name != null && !name.isBlank() ? name : email.split("@")[0])
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role("USER")
                    .provider(provider)
                    .providerId(providerId)
                    .build();

            userRepository.save(user);
            log.info("Created new user via OAuth: {} ({})", email, provider);
        }

        String jwtToken = jwtService.generateToken(user);

        return AuthenticationResponseDto.builder()
                .token(jwtToken)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }
}
