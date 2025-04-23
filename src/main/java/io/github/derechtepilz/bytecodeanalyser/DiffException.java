package io.github.derechtepilz.bytecodeanalyser;

public class DiffException extends Exception {

    public final String className;
    public final boolean methodKnown;

    public DiffException(String method, boolean methodKnown) {
        this(null, method, methodKnown);
    }

    public DiffException(String className, String method) {
        this(className, method, true);
    }

    public DiffException(String className, String method, boolean methodKnown) {
        super(method);
        this.className = className;
        this.methodKnown = methodKnown;
    }

}
