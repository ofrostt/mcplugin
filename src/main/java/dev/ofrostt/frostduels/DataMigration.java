package dev.ofrostt.frostduels;

import java.io.IOException;
import java.nio.file.*;

/** Copy existing server data on rename; never overwrite or remove the original. */
final class DataMigration {
    static void copyLegacy(Path destination) throws IOException {
        if (Files.exists(destination.resolve("config.yml"))) return;
        Path source = destination.resolveSibling("FrostDuels");
        if (!Files.isDirectory(source)) return;
        try (var files = Files.walk(source)) {
            for (Path file : files.sorted(java.util.Comparator.comparing(path -> path.equals(source.resolve("config.yml")) ? 1 : 0)).toList()) {
                Path target = destination.resolve(source.relativize(file));
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(target);
                else if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.exists(target)) Files.copy(file, target);
            }
        }
    }
}
