package dev.lunynt.opengate.account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends AutoCloseable {
    Optional<Account> findByPlayerId(UUID playerId);

    Optional<Account> findByUsername(String username);

    void save(Account account);

    @Override
    default void close() {}
}
