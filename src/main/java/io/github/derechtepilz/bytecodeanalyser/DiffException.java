package io.github.derechtepilz.bytecodeanalyser;

public class DiffException extends Exception {

    public final boolean methodKnown;

    public DiffException(String method) {
        this(method, true);
    }

    public DiffException(String method, boolean methodKnown) {
        super(method);
        this.methodKnown = methodKnown;
    }

}
