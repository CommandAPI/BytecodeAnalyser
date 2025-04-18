package io.github.derechtepilz.bytecodeanalyser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BytecodeFile {

    private static final List<String> DIFF = new ArrayList<>();

    private final String version;
    private final List<String> methods = new ArrayList<>();
    private final Map<String, List<String>> methodImpls = new HashMap<>();

    public BytecodeFile(String version, File bytecodeFile) {
        this.version = version;
        List<String> preprocessedLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(bytecodeFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                preprocessedLines.add(!(line.length() < 3) ? line.substring(2) : line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        readMethods(preprocessedLines);
        Collections.sort(methods);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BytecodeFile other = (BytecodeFile) obj;
        return Objects.equals(methods, other.methods) &&
            Objects.equals(methodImpls, other.methodImpls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methods, methodImpls);
    }

    public String getVersion() {
        return version;
    }

    public List<String> methods() {
        return methods;
    }

    public Map<String, List<String>> methodImpls() {
        return methodImpls;
    }

    public void writeAndDiff() throws DiffException {
        if (DIFF.isEmpty()) {
            writeInitialBytecodeFile();
            return;
        }
        compareAndWriteIfEqual();
    }

    private void writeInitialBytecodeFile() {
        for (String methodSignature : methods) {
            DIFF.add(methodSignature);
            DIFF.addAll(methodImpls.get(methodSignature));
        }
    }

    private void compareAndWriteIfEqual() throws DiffException {
        List<String> methodsDiff = collectToComparable();
        for (int i = 0; i < methodsDiff.size(); i++) {
            if (!methodsDiff.get(i).equals(DIFF.get(i))) {
                if (methods.contains(methodsDiff.get(i))) {
                    // We've just compared a method that doesn't match
                    throw new DiffException(methodsDiff.get(i));
                }
                // We didn't compare a method, but we want to know the method to be able to
                // know which method to implement version-specific
                for (Map.Entry<String, List<String>> entry : methodImpls.entrySet()) {
                    if (entry.getValue().contains(methodsDiff.get(i))) {
                        throw new DiffException(entry.getKey());
                    }
                }
            }
            DIFF.set(i, methodsDiff.get(i));
        }
    }

    private List<String> collectToComparable() {
        List<String> methods = new ArrayList<>();
        for (String methodSignature : this.methods) {
            methods.add(methodSignature);
            methods.addAll(methodImpls.get(methodSignature));
        }
        return methods;
    }

    private void readMethods(List<String> rawBytecode) {
        Iterator<String> iterator = rawBytecode.iterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            if (line.startsWith("protected abstract")) {
                methods.add(line);
                methodImpls.put(line, new ArrayList<>());
            }
            if (!line.startsWith("public final")) {
                while (iterator.hasNext() && !(line = iterator.next()).startsWith("public final")) {
                }
            }
            if (line.startsWith("public final")) {
                methods.add(line);
                readMethodImpl(line, iterator);
            }
        }
    }

    private void readMethodImpl(String currentMethod, Iterator<String> bytecodeIterator) {
        String line;
        while (bytecodeIterator.hasNext() && !(line = bytecodeIterator.next()).contains("areturn") && !line.contains("freturn")) {
            if (!line.contains("net/minecraft/")) {
                continue;
            }
            if (line.contains("// Method")) {
                String minecraftCode = line.split("// Method")[1].trim();
                List<String> methods = methodImpls.getOrDefault(currentMethod, new ArrayList<>());
                methods.add(minecraftCode);
                methodImpls.put(currentMethod, methods);
                continue;
            }
            if (line.contains("// InterfaceMethod")) {
                String minecraftCode = line.split("// InterfaceMethod")[1].trim();
                List<String> methods = methodImpls.getOrDefault(currentMethod, new ArrayList<>());
                methods.add(minecraftCode);
                methodImpls.put(currentMethod, methods);
                continue;
            }
            if (line.contains("// class")) {
                String minecraftCode = line.split("// class")[1].trim();
                List<String> methods = methodImpls.getOrDefault(currentMethod, new ArrayList<>());
                methods.add(minecraftCode);
                methodImpls.put(currentMethod, methods);
                continue;
            }
            if (line.contains("// Field")) {
                String minecraftCode = line.split("// Field")[1].trim();
                List<String> methods = methodImpls.getOrDefault(currentMethod, new ArrayList<>());
                methods.add(minecraftCode);
                methodImpls.put(currentMethod, methods);
                continue;
            }
            System.out.println("[WARNING] This line in " + version  + " contains Minecraft mappings but wasn't processed!");
            System.out.println("\t" + line);
        }
        if (!methodImpls.containsKey(currentMethod)) {
            methodImpls.put(currentMethod, new ArrayList<>());
        }
    }

}
