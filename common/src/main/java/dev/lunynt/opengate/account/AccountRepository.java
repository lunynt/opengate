package dev.lunynt.opengate.account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends AutoCloseable {
    Optional<Account> findByPlayerId(UUID playerId);

    Optional<Account> findByUsername(String username);

    void save(Account account);

    default void updatePassword(UUID playerId, String passwordHash) {
        save(findByPlayerId(playerId).orElseThrow().withPasswordHash(passwordHash));
    }

    default void updateTotpSecret(UUID playerId, String encryptedSecret) {
        save(findByPlayerId(playerId).orElseThrow().withTotpSecret(encryptedSecret));
    }

    boolean claimTotpStep(UUID playerId, long step);

    void delete(UUID playerId);

    @Override
    default void close() {}
}
