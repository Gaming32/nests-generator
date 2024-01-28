package io.github.gaming32.nestsgenerator;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface ClassFinder {
    ClassFinder EMPTY = className -> {
        throw new ClassNotFoundException(className);
    };

    ClassReader find(String className) throws ClassNotFoundException;

    static ClassFinder of(Path source) {
        final String separator = source.getFileSystem().getSeparator();
        return className -> {
            final Path path = source.resolve(className.replace("/", separator).concat(".class"));
            if (!Files.isRegularFile(path)) {
                throw new ClassNotFoundException(className);
            }
            try (InputStream is = Files.newInputStream(path)) {
                return new ClassReader(is);
            } catch (IOException e) {
                throw new ClassNotFoundException(className, e);
            }
        };
    }

    static ClassFinder of(Path... sources) {
        return of(List.of(sources));
    }

    static ClassFinder of(List<Path> sources) {
        return aggregate(sources.stream().map(ClassFinder::of).toList());
    }

    static ClassFinder aggregate(ClassFinder... finders) {
        return aggregate(List.of(finders));
    }

    static ClassFinder aggregate(List<ClassFinder> finders) {
        return switch (finders.size()) {
            case 0 -> EMPTY;
            case 1 -> finders.get(0);
            default -> className -> {
                List<ClassNotFoundException> suppressed = null;
                for (final ClassFinder finder : finders) {
                    try {
                        return finder.find(className);
                    } catch (ClassNotFoundException e) {
                        if (suppressed == null) {
                            suppressed = new ArrayList<>();
                        }
                        suppressed.add(e);
                    }
                }
                final ClassNotFoundException e = new ClassNotFoundException(className);
                assert suppressed != null;
                suppressed.forEach(e::addSuppressed);
                throw e;
            };
        };
    }
}
