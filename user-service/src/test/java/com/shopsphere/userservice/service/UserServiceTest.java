package com.shopsphere.userservice.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.UserNotFoundException;
import com.shopsphere.userservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository users;

    @InjectMocks
    private UserService userService;

    private User existingUser;

    @BeforeEach
    void setUp() {

        existingUser = new User();

        existingUser.setId(1L);
        existingUser.setName("Rahul Dwivedi");
        existingUser.setEmail("rahul@example.com");
        existingUser.setPassword("encodedPassword");
        existingUser.setRole(Role.USER);
    }

    // ---------- getById() tests ----------

    @Test
    void getById_ShouldReturnUser_WhenUserExists() {

        when(users.findById(1L))
                .thenReturn(Optional.of(existingUser));

        UserResponse response = userService.getById(1L);

        assertNotNull(response);

        verify(users).findById(1L);
    }

    @Test
    void getById_ShouldThrowException_WhenUserDoesNotExist() {

        when(users.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getById(1L)
        );

        verify(users).findById(1L);
    }

    // ---------- updateName() tests ----------

    @Test
    void updateName_ShouldUpdateName_WhenUserExists() {

        when(users.findById(1L))
                .thenReturn(Optional.of(existingUser));

        UserResponse response =
                userService.updateName(1L, "Updated Rahul");

        assertNotNull(response);

        org.junit.jupiter.api.Assertions.assertEquals(
                "Updated Rahul",
                existingUser.getName()
        );

        verify(users).findById(1L);
    }

    @Test
    void updateName_ShouldTrimName_WhenNameContainsSpaces() {

        when(users.findById(1L))
                .thenReturn(Optional.of(existingUser));

        userService.updateName(1L, "  Rahul Updated  ");

        org.junit.jupiter.api.Assertions.assertEquals(
                "Rahul Updated",
                existingUser.getName()
        );
    }

    @Test
    void updateName_ShouldThrowException_WhenUserDoesNotExist() {

        when(users.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.updateName(1L, "Updated Rahul")
        );

        verify(users).findById(1L);
    }
}
