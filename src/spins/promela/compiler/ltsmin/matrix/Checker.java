package spins.promela.compiler.ltsmin.matrix;

public abstract class Checker<A, B> {
    public abstract boolean check (int xi, int yi, A x, B y);   
}