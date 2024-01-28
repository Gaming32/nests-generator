package io.github.gaming32.nestsgenerator;

import net.ornithemc.nester.nest.NesterIo;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class NestsGeneratorCli {
    @SuppressWarnings("ThrowFromFinallyBlock")
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if (args.length == 0) {
            System.err.println("Usage: java -jar nests-generator.jar classes");
            System.err.println();
            System.err.println("  classes -- Sources (jars or directories) to read classes from");
            System.exit(1);
        }
        final List<Path> sources = new ArrayList<>(args.length);
        final List<FileSystem> toClose = new ArrayList<>();
        try {
            for (final String sourcePath : args) {
                final Path source = Path.of(sourcePath);
                if (!Files.exists(source)) {
                    System.err.println("Couldn't find source " + source);
                    System.exit(1);
                }
                if (Files.isDirectory(source)) {
                    sources.add(source);
                    continue;
                }
                final FileSystem fs = FileSystems.newFileSystem(source);
                toClose.add(fs);
                fs.getRootDirectories().forEach(sources::add);
            }
            generate(sources);
        } finally {
            List<Throwable> suppressed = null;
            for (final FileSystem fs : toClose) {
                try {
                    fs.close();
                } catch (Throwable t) {
                    if (suppressed == null) {
                        suppressed = new ArrayList<>();
                    }
                    suppressed.add(t);
                }
            }
            if (suppressed != null) {
                if (suppressed.size() == 1) {
                    throw new IOException("Failed to close jar", suppressed.get(0));
                }
                final IOException exc = new IOException("Failed to close " + suppressed.size() + " jars");
                suppressed.forEach(exc::addSuppressed);
                throw exc;
            }
        }
    }

    private static void generate(List<Path> sources) throws IOException, ClassNotFoundException {
        final NestsGenerator generator = new NestsGenerator(ClassFinder.of(sources));
        for (final Path source : sources) {
            System.err.println("Generating nests from " + source);
            try (Stream<Path> stream = Files.find(
                source, Integer.MAX_VALUE, (p, a) -> a.isRegularFile() && p.toString().endsWith(".class")
            )) {
                stream.forEach(p -> {
                    try {
                        generator.visit(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (ClassNotFoundException e) {
                        throw new UncheckedClassNotFoundException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            } catch (UncheckedClassNotFoundException e) {
                throw e.getCause();
            }
        }
        System.err.println("Collecting additional classes");
        generator.finish();
        NesterIo.write(generator.getNests(), new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)));
    }
}
