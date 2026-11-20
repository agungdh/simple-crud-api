package com.example.demo.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 20;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username sudah digunakan");
        }

        Instant now = Instant.now();
        User user = User.builder()
                .uuid(UUID.randomUUID())
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .createdAt(now)
                .updatedAt(now)
                .build();

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public CursorResponse<UserResponse> list(String search, UUID after, Integer limitParam) {
        int limit = resolveLimit(limitParam);

        List<User> rows;
        if (search != null && !search.isBlank()) {
            if (after != null) {
                Long cursorId = resolveCursorId(after);
                rows = userRepository.searchPageAfterCursor(search.trim(), cursorId, limit + 1);
            } else {
                rows = userRepository.searchFirstPage(search.trim(), limit + 1);
            }
        } else {
            if (after != null) {
                Long cursorId = resolveCursorId(after);
                rows = userRepository.findPageAfterCursor(cursorId, limit + 1);
            } else {
                rows = userRepository.findFirstPage(limit + 1);
            }
        }

        boolean hasNext = rows.size() > limit;
        List<User> pageData = hasNext ? rows.subList(0, limit) : rows;
        UUID nextCursor = hasNext ? pageData.getLast().getUuid() : null;

        return new CursorResponse<>(
                userMapper.toResponseList(pageData),
                new CursorResponse.PaginationMeta(limit, hasNext, nextCursor)
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getByUuid(UUID uuid) {
        return userMapper.toResponse(findActiveByUuid(uuid));
    }

    @Transactional
    public UserResponse update(UUID uuid, UpdateUserRequest request) {
        User user = findActiveByUuid(uuid);

        if (!user.getUsername().equals(request.username())
                && userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username sudah digunakan");
        }

        user.setUsername(request.username());
        user.setName(request.name());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        user.setUpdatedAt(Instant.now());

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void delete(UUID uuid) {
        User user = findActiveByUuid(uuid);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private User findActiveByUuid(UUID uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User tidak ditemukan"));
    }

    private Long resolveCursorId(UUID cursorUuid) {
        return userRepository.findIdByUuid(cursorUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor tidak valid atau tidak ditemukan"));
    }

    private int resolveLimit(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }
}
