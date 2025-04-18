package io.github.derechtepilz.bytecodeanalyser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Main {

    private final Pattern pattern = Pattern.compile("CommandAPI-(\\d+)\\.(\\d+)\\.(\\d+)(-SNAPSHOT)?_(\\d{1,2})_(\\w{3})_(\\d{4})_\\((\\d{2}-\\d{2}-\\d{2}(am|pm))\\)\\.jar");

    private final List<BytecodeFile> bytecodes = new ArrayList<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        Main main = new Main();
        List<String> versions = main.collectFolders();
        System.out.println(versions);
        main.clean(versions);
        main.unzipMatchingJar(versions);
        main.createBytecodeFile(versions);
        main.loadBytecodeFile(versions);
        try {
            main.compareAllBytecodes();
        } catch (DiffException e) {
            if (e.methodKnown) {
                System.out.println("There is a mappings issue with " + e.getMessage());
                for (BytecodeFile file : main.bytecodes) {
                    System.out.println("Bytecode " + file.getVersion() + ":");
                    System.out.println(e.getMessage());
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
            if (file.isDirectory()) {
                folders.add(file.getName());
            }
        }
        return folders;
    }

    public void clean(List<String> versions) {
        for (String version : versions) {
            File versionFolder = new File(version);
            File bytecodeFile = new File(versionFolder, "bytecode_" + version + ".txt");
            File devFolder = new File(versionFolder, "dev");
            File metaFolder = new File(versionFolder, "META-INF");
            if (bytecodeFile.exists()) {
                bytecodeFile.delete();
            }
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

    public void createBytecodeFile(List<String> versions) throws IOException, InterruptedException {
        for (String version : versions) {
            Runtime runtime = Runtime.getRuntime();
            System.out.println("Creating bytecode file for version " + version);
            Process process = runtime.exec(new String[]{"javap", "-c", "dev/jorel/commandapi/nms/NMS_Common.class"}, null, new File(version));
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new FileWriter(new File(version, "bytecode_" + version + ".txt")))
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line + "\n");
                }
            }
            process.waitFor();
            System.out.println("Created bytecode file for version " + version);
        }
    }

    public void loadBytecodeFile(List<String> versions) {
        for (String version : versions) {
            File versionFolder = new File(version);
            File bytecodeFile = new File(versionFolder, "bytecode_" + version + ".txt");
            if (bytecodeFile.exists()) {
                BytecodeFile bytecode = new BytecodeFile(version, bytecodeFile);
                bytecodes.add(bytecode);
            }
        }
    }

    public void compareAllBytecodes() throws DiffException, IOException {
        System.out.println("Comparing all bytecodes...");
        for (BytecodeFile bytecode : bytecodes) {
            bytecode.writeAndDiff();
        }
        System.out.println("Performing sanity check...");
        boolean areBytecodesEqual = true;
        BytecodeFile previousBytecode = null;
        for (BytecodeFile bytecode : bytecodes) {
            if (previousBytecode == null) {
                previousBytecode = bytecode;
                continue;
            }
            areBytecodesEqual &= previousBytecode.equals(bytecode);
            previousBytecode = bytecode;
        }
        if (!areBytecodesEqual) {
            throw new DiffException("Bytecodes differ somewhere! The built-in checks did not catch that. Mappings issue will arise.", false);
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
            if (pattern.matcher(file.getName()).matches()) {
                return file.getName();
            }
        }
        return null;
    }

}
