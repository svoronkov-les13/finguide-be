package les13.finguide.backend.users;

import les13.finguide.backend.auth.CurrentUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserProfileRepository {
    private static final BigDecimal DEFAULT_INITIAL_BALANCE = BigDecimal.ZERO;

    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfile> findByKeycloakSubject(String subject) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select * from user_profiles where keycloak_subject = ?",
                    this::mapProfile,
                    subject
            ));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public UserProfile findOrCreateFrom(CurrentUser user) {
        return findByKeycloakSubject(user.keycloakSubject()).orElseGet(() -> createFrom(user));
    }

    private UserProfile createFrom(CurrentUser user) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String email = normalizeEmail(user.email(), user.keycloakSubject());
        String name = normalizeName(user.name(), email, user.keycloakSubject());
        jdbcTemplate.update(
                "insert into user_profiles (id, keycloak_subject, email, name, phone, avatar_url, age, gender, initial_balance, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                user.keycloakSubject(),
                email,
                name,
                null,
                null,
                null,
                null,
                DEFAULT_INITIAL_BALANCE,
                offset(now),
                offset(now)
        );
        return findByKeycloakSubject(user.keycloakSubject()).orElseThrow();
    }

    private static String normalizeEmail(String email, String subject) {
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        String safeSubject = subject == null || subject.isBlank() ? "keycloak-user" : subject.replaceAll("[^A-Za-z0-9._-]", "-");
        return safeSubject + "@keycloak.local";
    }

    private static String normalizeName(String name, String email, String subject) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length());
        }
        return subject == null || subject.isBlank() ? "FinGuide User" : subject;
    }

    private UserProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new UserProfile(
                rs.getObject("id", UUID.class),
                rs.getString("keycloak_subject"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("avatar_url"),
                integer(rs, "age"),
                enumValue(UserProfile.Gender.class, rs.getString("gender")),
                rs.getBigDecimal("initial_balance"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
