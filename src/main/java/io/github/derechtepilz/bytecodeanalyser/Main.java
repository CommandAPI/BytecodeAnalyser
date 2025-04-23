package io.github.derechtepilz.bytecodeanalyser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class Main {

    private final Pattern versionFolderPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private final Pattern commandAPIJarNamePattern = Pattern.compile("CommandAPI-(\\d+)\\.(\\d+)\\.(\\d+)(-SNAPSHOT)?_(\\d{1,2})_(\\w{3})_(\\d{4})_\\((\\d{2}-\\d{2}-\\d{2}(am|pm|AM|PM))\\)\\.jar");

    private final List<BytecodeFile> bytecodes = new ArrayList<>();

    private final BiFunction<String, String, String> getDisassembledBytecodeFileName = (version, className) -> "bytecode_" + version + "_" + className + ".txt";
    private final BiFunction<String, String, String> getMethodBytecodeFileName = (version, className) -> "bytecode_" + version + "_" + className + "_methods.txt";

    public static void main(String[] args) throws IOException, InterruptedException {
        Main main = new Main();
        List<String> versions = main.collectFolders();
        System.out.println(versions);
        main.clean(versions);
        main.unzipMatchingJar(versions);
        List<String> classNames = main.collectClassNames();
        System.out.println(classNames);
        main.createBytecodeFiles(versions, classNames);
        main.loadBytecodeFile(versions, classNames);
        try {
            main.compareAllBytecodes(classNames);
        } catch (DiffException e) {
            if (e.methodKnown) {
                System.out.println("There is a mappings issue with " + e.getMessage());
                for (BytecodeFile file : main.bytecodes) {
                    if (!file.getClassName().equals(e.className)) {
                        continue;
                    }
                    System.out.println("Bytecode " + file.getVersion() + ":");
                    System.out.println(e.getMessage());
                    System.out.println(file.methodImpls().keySet());
                    file.methodImpls().get(e.getMessage()).forEach(impl -> {
                        System.out.println("\t" + impl);
                    });
                    System.out.println();
                }
            } else {
                System.out.println(e.getMessage());
            }
        }
    }

    public List<String> collectFolders() {
        List<String> folders = new ArrayList<>();
        File folder = new File(".");
        File[] files = folder.listFiles();
        if (files == null) {
            return folders;
        }
        for (File file : files) {
            if (file.isDirectory() && versionFolderPattern.matcher(file.getName()).matches()) {
                folders.add(file.getName());
            }
        }
        return folders;
    }

    public List<String> collectClassNames() {
        List<String> classNames = new ArrayList<>();
        // Do the work based on the first matching folder, we compile only one class several
        // times so the amount of emitted classes should be the same
        File[] files = new File(".").listFiles();
        File workingDir = null;
        if (files == null) {
            return classNames;
        }
        for (File file : files) {
            if (file.isDirectory() && versionFolderPattern.matcher(file.getName()).matches()) {
                workingDir = file;
                break;
            }
        }
        if (workingDir == null) {
            return classNames;
        }
        File classesDir = new File(workingDir, "dev/jorel/commandapi/nms");
        File[] classFiles = classesDir.listFiles();
        if (classFiles == null) {
            return classNames;
        }
        for (File classFile : classFiles) {
            if (!classFile.isDirectory()) {
                classNames.add(classFile.getName().replace(".class", ""));
            }
        }
        return classNames;
    }

    public void clean(List<String> versions) {
        for (String version : versions) {
            File versionFolder = new File(version);
            File devFolder = new File(versionFolder, "dev");
            File metaFolder = new File(versionFolder, "META-INF");
            if (devFolder.exists()) {
                deleteRecursively(devFolder);
            }
            if (metaFolder.exists()) {
                deleteRecursively(metaFolder);
            }
        }
    }

    public void unzipMatchingJar(List<String> versions) throws IOException, InterruptedException {
        for (String version : versions) {
            Runtime runtime = Runtime.getRuntime();
            String fileName;
            if ((fileName = getMatchingFileName(version)) == null) {
                continue;
            }
            System.out.println("Unzipping " + version + "/" + fileName);
            Process process = runtime.exec(new String[]{"tar", "-xf", fileName}, null, new File(version));
            process.waitFor();
        }
    }

    public void createBytecodeFiles(List<String> versions, List<String> classNames) throws IOException, InterruptedException {
        for (String version : versions) {
            Runtime runtime = Runtime.getRuntime();
            for (String className : classNames) {
                String disassembledBytecodeFileName = getDisassembledBytecodeFileName.apply(version, className);
                String methodBytecodeFileName = getMethodBytecodeFileName.apply(version, className);
                File disassembledBytecodeFile = new File(version, disassembledBytecodeFileName);
                File methodBytecodeFile = new File(version, methodBytecodeFileName);
                if (disassembledBytecodeFile.exists()) {
                    disassembledBytecodeFile.delete();
                }
                if (methodBytecodeFile.exists()) {
                    methodBytecodeFile.delete();
                }

                Process disassembledBytecode = runtime.exec(new String[]{"javap", "-c", "dev/jorel/commandapi/nms/" + className + ".class"}, null, new File(version));
                Process classMethods = runtime.exec(new String[]{"javap", "dev/jorel/commandapi/nms/" + className + ".class"}, null, new File(version));
                try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(disassembledBytecode.getInputStream()));
                    BufferedWriter writer = new BufferedWriter(new FileWriter(new File(version, disassembledBytecodeFileName)))
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line + "\n");
                    }
                }
                try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(classMethods.getInputStream()));
                    BufferedWriter writer = new BufferedWriter(new FileWriter(new File(version, methodBytecodeFileName)))
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line + "\n");
                    }
                }
                disassembledBytecode.waitFor();
                classMethods.waitFor();
                System.out.println("Created bytecode files for version " + version + ", class " + className + ".class");
            }
        }
    }

    public void loadBytecodeFile(List<String> versions, List<String> classNames) {
        for (String version : versions) {
            File versionFolder = new File(version);
            for (String className : classNames) {
                File bytecodeFile = new File(versionFolder, getDisassembledBytecodeFileName.apply(version, className));
                File methodFile = new File(versionFolder, getMethodBytecodeFileName.apply(version, className));
                if (bytecodeFile.exists()) {
                    BytecodeFile bytecode = new BytecodeFile(version, className, bytecodeFile, methodFile);
                    bytecodes.add(bytecode);
                }
            }
        }
    }

    public void compareAllBytecodes(List<String> classNames) throws DiffException, IOException {
        System.out.println("Comparing all bytecodes...");
        for (BytecodeFile bytecode : bytecodes) {
            bytecode.writeAndDiff();
        }
        System.out.println("Performing sanity check...");
        boolean areBytecodesEqual = true;
        BytecodeFile previousBytecode = null;
        for (String className : classNames) {
            for (BytecodeFile bytecode : bytecodes) {
                if (!bytecode.getClassName().equals(className)) {
                    continue;
                }
                if (previousBytecode == null) {
                    previousBytecode = bytecode;
                    continue;
                }
                areBytecodesEqual &= previousBytecode.equals(bytecode);
                previousBytecode = bytecode;
            }
            previousBytecode = null;
            if (!areBytecodesEqual) {
                throw new DiffException("Bytecodes differ somewhere! The built-in checks did not catch that. Mappings issue will arise.", false);
            }
        }
        System.out.println("All bytecodes are identical! No mapping issues will arise!");
    }

    private void deleteRecursively(File file) {
        File[] files = file.listFiles();
        if (files == null || files.length == 0) {
            file.delete();
            return;
        }
        for (File file1 : files) {
            if (file1.isDirectory()) {
                deleteRecursively(file1);
                continue;
            }
            file1.delete();
        }
        file.delete();
    }

    private String getMatchingFileName(String version) {
        File folder = new File("./" + version);
        if (folder.listFiles() == null) {
            return null;
        }
        for (File file : folder.listFiles()) {
            if (commandAPIJarNamePattern.matcher(file.getName()).matches()) {
                return file.getName();
            }
        }
        return null;
    }

}
