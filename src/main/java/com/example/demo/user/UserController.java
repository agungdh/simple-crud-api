package com.example.demo.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @GetMapping
    public CursorResponse<UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID after,
            @RequestParam(required = false) Integer limit
    ) {
        return userService.list(search, after, limit);
    }

    @GetMapping("/{uuid}")
    public UserResponse get(@PathVariable UUID uuid) {
        return userService.getByUuid(uuid);
    }

    @PutMapping("/{uuid}")
    public UserResponse update(
            @PathVariable UUID uuid,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.update(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        userService.delete(uuid);
    }
}
