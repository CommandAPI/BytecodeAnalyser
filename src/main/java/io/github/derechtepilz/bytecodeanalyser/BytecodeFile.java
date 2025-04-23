package io.github.derechtepilz.bytecodeanalyser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BytecodeFile {

    private static final Map<String, List<String>> DIFF = new HashMap<>();

    private final String version;
    private final String className;
    private final File bytecodeFile;
    private final Pattern finder = Pattern.compile("// [A-Za-z]+");
    private final List<String> methods = new ArrayList<>();
    private final Map<String, List<String>> methodImpls = new HashMap<>();

    public BytecodeFile(String version, String className, File bytecodeFile, File methodFile) {
        this.version = version;
        this.className = className;
        this.bytecodeFile = bytecodeFile;
        try (BufferedReader reader = new BufferedReader(new FileReader(methodFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("  ")) {
                    methods.add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        readMethodImpls();
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

    public String getClassName() {
        return className;
    }

    public List<String> methods() {
        return methods;
    }

    public Map<String, List<String>> methodImpls() {
        return methodImpls;
    }

    public void writeAndDiff() throws DiffException {
        if (DIFF.get(className) == null) {
            writeInitialBytecodeFile();
            return;
        }
        compareAndWriteIfEqual();
    }

    private void writeInitialBytecodeFile() {
        List<String> methodImplementation = new ArrayList<>();
        for (String methodSignature : methods) {
            methodImplementation.add(methodSignature);
            methodImplementation.addAll(methodImpls.get(methodSignature));
        }
        DIFF.put(className, methodImplementation);
    }

    private void compareAndWriteIfEqual() throws DiffException {
        List<String> methodsDiff = collectToComparable();
        for (int i = 0; i < methodsDiff.size(); i++) {
            if (!methodsDiff.get(i).equals(DIFF.get(className).get(i))) {
                if (methods.contains(methodsDiff.get(i))) {
                    // We've just compared a method that doesn't match
                    throw new DiffException(className, methodsDiff.get(i));
                }
                // We didn't compare a method, but we want to know the method to be able to
                // know which method to implement version-specific
                for (Map.Entry<String, List<String>> entry : methodImpls.entrySet()) {
                    if (entry.getValue().contains(methodsDiff.get(i))) {
                        throw new DiffException(className, entry.getKey());
                    }
                }
            }
            DIFF.get(className).set(i, methodsDiff.get(i));
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

    private void readMethodImpls() {
        List<String> bytecodeLines = new ArrayList<>();
        try {
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(bytecodeFile));
            while ((line = reader.readLine()) != null) {
                bytecodeLines.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (String method : methods) {
            readMethod(method, bytecodeLines);
        }
    }

    private void readMethod(String name, List<String> bytecodeContents) {
        int methodIndex = bytecodeContents.indexOf(name);
        for (int i = methodIndex + 1; i < bytecodeContents.size(); i++) {
            if (methods.contains(bytecodeContents.get(i))) {
                break;
            }
            Matcher matcher = finder.matcher(bytecodeContents.get(i));
            if (matcher.find()) {
                String potentialMapping = bytecodeContents.get(i).split(matcher.group())[1].trim();
                if (!potentialMapping.contains("net/minecraft/")) {
                    continue;
                }
                // Minecraft mapping found
                List<String> methods = methodImpls.getOrDefault(name, new ArrayList<>());
                methods.add(potentialMapping);
                methodImpls.put(name, methods);
            }
        }
        if (!methodImpls.containsKey(name)) {
            methodImpls.put(name, new ArrayList<>());
        }
    }

}
