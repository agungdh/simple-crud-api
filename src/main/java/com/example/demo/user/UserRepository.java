package com.example.demo.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUuid(UUID uuid);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.id < :cursorId ORDER BY u.id DESC LIMIT :limit")
    List<User> findPageAfterCursor(@Param("cursorId") Long cursorId, @Param("limit") int limit);

    @Query("SELECT u FROM User u ORDER BY u.id DESC LIMIT :limit")
    List<User> findFirstPage(@Param("limit") int limit);

    @Query("SELECT u FROM User u WHERE u.searchText ILIKE CONCAT('%', :search, '%') AND u.id < :cursorId ORDER BY u.id DESC LIMIT :limit")
    List<User> searchPageAfterCursor(@Param("search") String search, @Param("cursorId") Long cursorId, @Param("limit") int limit);

    @Query("SELECT u FROM User u WHERE u.searchText ILIKE CONCAT('%', :search, '%') ORDER BY u.id DESC LIMIT :limit")
    List<User> searchFirstPage(@Param("search") String search, @Param("limit") int limit);

    @Query("SELECT u.id FROM User u WHERE u.uuid = :uuid")
    Optional<Long> findIdByUuid(@Param("uuid") UUID uuid);
}
