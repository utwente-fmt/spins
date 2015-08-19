package spins.promela.compiler.ltsmin.util;

import spins.promela.compiler.ltsmin.state.LTSminVariable;

public class PArrayIndexOutOfBoundsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PArrayIndexOutOfBoundsException(LTSminVariable var, int index) {
        super("Array index out of bound for: "+ var +"["+ index +"]: "+ index +" >= "+ var.array());
    }
}
