# BytecodeAnalyser

BytecodeAnalyser is a tool made for use in the [CommandAPI's](https://github.com/CommandAPI/CommandAPI) GitHub Actions to compare the bytecode
to make sure that the [commandapi-bukkit-nms-common](https://github.com/CommandAPI/CommandAPI/tree/5a3434a90f0608be7d9d009c490fd8ffaf5a3a0c/commandapi-platforms/commandapi-bukkit/commandapi-bukkit-nms/commandapi-bukkit-nms-common) module
is compatible with all version the CommandAPI supports.

## Running BytecodeAnalyser locally

While intended for use in the CommandAPI's GitHub Actions build, you can also run it locally.

### Cloning and compiling BytecodeAnalyser

For that, clone the repository first:

```
git clone https://github.com/CommandAPI/BytecodeAnalyser.git
```

To compile the BytecodeAnalyser, cd into the directory you cloned it to and run:

```
./gradlew jar
```

The compiled jar will be in `build/libs/`.

### Cloning and compiling the CommandAPI

Because this depends on compiled modules of the CommandAPI, also clone the CommandAPI:

```
git clone https://github.com/CommandAPI/CommandAPI.git
```

To obtain the required structure that the BytecodeAnalyser expects, cd into the directory you cloned the CommandAPI to and run:

```
./compileNMSCommon.sh
```

### Running BytecodeAnalyser

To run the BytecodeAnalyser, copy the jar you got from compiling the BytecodeAnalyser into the `commandapi-platforms/commandapi-bukkit/commandapi-bukkit-nms/commandapi-bukkit-nms-common/target` folder that you have after running the `compileNMSCommon.sh` script.

After that, just run:

```
java -jar BytecodeAnalyser*.jar
```