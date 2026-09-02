package dev.lunynt.opengate.account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends AutoCloseable {
    Optional<Account> findByPlayerId(UUID playerId);

    Optional<Account> findByUsername(String username);

    void save(Account account);

    boolean claimTotpStep(UUID playerId, long step);

    void delete(UUID playerId);

    @Override
    default void close() {}
}
