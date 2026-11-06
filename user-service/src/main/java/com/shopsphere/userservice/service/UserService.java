package com.shopsphere.userservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.UserNotFoundException;
import com.shopsphere.userservice.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(long id) {
        return users.findById(id).map(UserResponse::from).orElseThrow(UserNotFoundException::new);
    }

    @Transactional
    public UserResponse updateName(long id, String name) {
        User user = users.findById(id).orElseThrow(UserNotFoundException::new);
        user.setName(name.trim());
        return UserResponse.from(user);
    }
}
