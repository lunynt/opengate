package dev.lunynt.opengate.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.MAC})
class SecureFilesTest {
    @TempDir
    Path directory;

    @Test
    void restrictsDirectoryAndFilePermissions() throws Exception {
        var privateDirectory = directory.resolve("OpenGate");
        SecureFiles.createPrivateDirectory(privateDirectory);
        var file = Files.writeString(privateDirectory.resolve("opengate.db"), "data");
        SecureFiles.makeOwnerOnly(file);

        assertEquals(
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE),
                Files.getPosixFilePermissions(privateDirectory));
        assertEquals(
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(file));
    }
}
