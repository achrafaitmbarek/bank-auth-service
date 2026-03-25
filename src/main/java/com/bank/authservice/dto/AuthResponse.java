package com.bank.authservice.dto;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class AuthResponse {

    private String message;
    private String email;
    private String keycloakId;

    private String accessToken;

    private String refreshToken;

    private Integer expiresIn;

}