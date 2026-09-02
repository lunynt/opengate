package dev.lunynt.opengate.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class SecureFiles {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private SecureFiles() {}

    public static void createPrivateDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            setPermissions(directory, DIRECTORY_PERMISSIONS);
        } catch (IOException exception) {
            throw new IllegalStateException("could not secure " + directory, exception);
        }
    }

    public static void makeOwnerOnly(Path file) {
        try {
            setPermissions(file, FILE_PERMISSIONS);
        } catch (IOException exception) {
            throw new IllegalStateException("could not secure " + file, exception);
        }
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
        }
    }
}
