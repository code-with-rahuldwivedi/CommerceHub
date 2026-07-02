package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.LoginRequest;
import com.shopsphere.userservice.dto.RegisterRequest;

public interface UserService {

    String registerUser(RegisterRequest request);
    String login(LoginRequest request);
}