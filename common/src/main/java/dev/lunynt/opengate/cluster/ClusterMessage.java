package dev.lunynt.opengate.cluster;

import java.util.Optional;
import java.util.UUID;

record ClusterMessage(String type, long sequence, UUID instanceId, UUID accountId) {
    static Optional<ClusterMessage> parse(String message) {
        if (message == null || message.length() > 128) {
            return Optional.empty();
        }
        var parts = message.split("\\|", -1);
        if (parts.length != 4 || !(parts[0].equals("AUTH") || parts[0].equals("REVOKE"))) {
            return Optional.empty();
        }
        try {
            var sequence = Long.parseLong(parts[1]);
            var instanceId = UUID.fromString(parts[2]);
            var accountId = UUID.fromString(parts[3]);
            if (sequence <= 0 || !Long.toString(sequence).equals(parts[1])
                    || !instanceId.toString().equals(parts[2]) || !accountId.toString().equals(parts[3])) {
                return Optional.empty();
            }
            return Optional.of(new ClusterMessage(parts[0], sequence, instanceId, accountId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
