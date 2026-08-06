package com.aidocqa.controller;

import com.aidocqa.dto.ApiResponseDto;
import com.aidocqa.dto.AuthenticationResponseDto;
import com.aidocqa.dto.LoginRequestDto;
import com.aidocqa.dto.RegisterRequestDto;
import com.aidocqa.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user registration and login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Create a new account and receive a JWT token")
    public ResponseEntity<ApiResponseDto<AuthenticationResponseDto>> register(
            @Valid @RequestBody RegisterRequestDto request) {

        AuthenticationResponseDto response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success("User registered successfully", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with email and password to receive a JWT token")
    public ResponseEntity<ApiResponseDto<AuthenticationResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request) {

        AuthenticationResponseDto response = authService.login(request);
        return ResponseEntity
                .ok(ApiResponseDto.success("Login successful", response));
    }
}
