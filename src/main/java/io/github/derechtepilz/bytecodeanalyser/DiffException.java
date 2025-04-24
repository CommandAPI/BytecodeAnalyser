package io.github.derechtepilz.bytecodeanalyser;

import java.util.List;

public class DiffException extends Exception {

    public final String className;
    public final boolean methodKnown;

    public final List<String> methods;

    public DiffException(List<String> methods, boolean methodKnown) {
        this(null, methods, methodKnown);
    }

    public DiffException(String className, List<String> methods) {
        this(className, methods, true);
    }

    public DiffException(String className, List<String> methods, boolean methodKnown) {
        this.methods = methods;
        this.className = className;
        this.methodKnown = methodKnown;
    }

}
