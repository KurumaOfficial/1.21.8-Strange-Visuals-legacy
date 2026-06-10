package ru.strange.client.utils.io;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicFileIO {

    private AtomicFileIO() {
    }

    @FunctionalInterface
    public interface WriterAction {
        void write(Writer writer) throws IOException;
    }

    public static Path tempPath(Path targetPath) {
        return targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
    }

    public static void ensureParentDirectory(Path targetPath) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    public static void writeUtf8StringAtomically(Path targetPath, String content) throws IOException {
        writeUtf8Atomically(targetPath, writer -> writer.write(content));
    }

    public static void writeUtf8Atomically(Path targetPath, WriterAction writerAction) throws IOException {
        ensureParentDirectory(targetPath);
        Path tempPath = tempPath(targetPath);

        try {
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                writerAction.write(writer);
            }
            moveReplace(tempPath, targetPath);
        } catch (IOException | RuntimeException exception) {
            cleanupTempFile(tempPath);
            throw exception;
        }
    }

    public static void moveReplace(Path sourcePath, Path targetPath) throws IOException {
        ensureParentDirectory(targetPath);
        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void cleanupTempFile(Path tempPath) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException ignored) {
        }
    }
}
