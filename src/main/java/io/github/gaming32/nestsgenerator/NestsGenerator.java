package io.github.gaming32.nestsgenerator;

import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.NestType;
import net.ornithemc.nester.nest.Nests;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NestsGenerator {
    private final Nests nests;
    private final ClassFinder classFinder;
    private final Map<String, List<InnerClassInfo>> innerInfos = new HashMap<>();
    private final Map<String, OuterClassInfo> outerInfos = new HashMap<>();
    private final Map<String, InnerClassInfo> outerReferences = new HashMap<>();
    private final Set<String> finalPass = new HashSet<>();

    public NestsGenerator(Nests nests, ClassFinder classFinder) {
        this.nests = nests;
        this.classFinder = classFinder;
    }

    public NestsGenerator(ClassFinder classFinder) {
        this(Nests.empty(), classFinder);
    }

    public Nests getNests() {
        return nests;
    }

    public void visit(ClassReader classReader) throws ClassNotFoundException {
        final String className = classReader.getClassName();
        if (nests.get(className) != null || innerInfos.containsKey(className)) return;
        final List<InnerClassInfo> innerInfo = new ArrayList<>();
        final OuterClassInfo[] outerInfo = {null};
        innerInfos.put(className, innerInfo);
        try {
            classReader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visitInnerClass(String name, String outerName, String innerName, int access) {
                    final InnerClassInfo info = new InnerClassInfo(name, outerName, innerName, access);
                    innerInfo.add(info);
                    outerReferences.put(name, info);
                    finalPass.remove(name);
                }

                @Override
                public void visitOuterClass(String owner, String name, String descriptor) {
                    outerInfo[0] = new OuterClassInfo(name, descriptor);
                    try {
                        NestsGenerator.this.visit(owner);
                    } catch (ClassNotFoundException e) {
                        throw new UncheckedClassNotFoundException(e);
                    }
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        } catch (UncheckedClassNotFoundException e) {
            throw e.getCause();
        }
        if (outerInfo[0] != null) {
            outerInfos.put(className, outerInfo[0]);
        }
        final InnerClassInfo outerReference = outerReferences.get(className);
        if (outerReference != null) {
            nests.add(createNest(outerReference, outerInfo[0]));
        } else {
            finalPass.add(className);
        }
    }

    public void visit(String className) throws ClassNotFoundException {
        if (nests.get(className) != null) return;
        visit(classFinder.find(className));
    }

    public void visit(Path path) throws IOException, ClassNotFoundException {
        final ClassReader reader;
        try (InputStream is = Files.newInputStream(path)) {
            reader = new ClassReader(is);
        }
        visit(reader);
    }

    public void finish() {
        for (final String className : finalPass) {
            if (nests.get(className) != null) continue; // We already found what we need
            final InnerClassInfo innerInfo = outerReferences.get(className);
            if (innerInfo == null) continue; // We never found the associated outer class
            final OuterClassInfo outerInfo = outerInfos.get(className);
            nests.add(createNest(innerInfo, outerInfo));
        }
        finalPass.clear();
    }

    private static Nest createNest(InnerClassInfo innerInfo, OuterClassInfo outerInfo) {
        final String innerName = innerInfo.innerName;
        int firstNonDigit = 0;
        while (firstNonDigit < innerName.length() && Character.isDigit(innerName.charAt(firstNonDigit))) {
            firstNonDigit++;
        }
        final NestType type = firstNonDigit == innerName.length() ? NestType.ANONYMOUS : firstNonDigit == 0 ? NestType.INNER : NestType.LOCAL;
        if (outerInfo == null) {
            outerInfo = new OuterClassInfo(null, null);
        }
        return new Nest(
            type, innerInfo.name, innerInfo.outerName, outerInfo.outerMethod, outerInfo.outerDesc, innerName, innerInfo.access
        );
    }

    private record InnerClassInfo(String name, String outerName, String innerName, int access) {
    }

    private record OuterClassInfo(String outerMethod, String outerDesc) {
    }
}
