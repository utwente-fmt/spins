package spins.test;

import static spins.promela.compiler.ltsmin.util.LTSminUtil.id;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.junit.Test;

import spins.promela.compiler.ProcInstance;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.Specification;
import spins.promela.compiler.automaton.Automaton;
import spins.promela.compiler.expression.AritmicExpression;
import spins.promela.compiler.expression.BooleanExpression;
import spins.promela.compiler.expression.ChannelLengthExpression;
import spins.promela.compiler.expression.ChannelOperation;
import spins.promela.compiler.expression.ChannelReadExpression;
import spins.promela.compiler.expression.CompareExpression;
import spins.promela.compiler.expression.ConstantExpression;
import spins.promela.compiler.expression.EvalExpression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.expression.MTypeReference;
import spins.promela.compiler.expression.RemoteRef;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.parser.PromelaTokenManager;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableType;

public final class EqualsContracts {

    static final Variable x = new Variable(VariableType.BIT,"x",2);
    static final Variable y = new Variable(VariableType.BIT,"y",2);

    static final Token tx = new Token(PromelaTokenManager.IDENTIFIER,"x");
    static final Token ty = new Token(PromelaTokenManager.IDENTIFIER,"y");

    static final Proctype pa = new Proctype(new Specification("p"), 0, 1, "a");
    static final Proctype pb = new Proctype(new Specification("p"), 0, 2, "b");

    static final ProcInstance ia = new ProcInstance(pa, 1, 2);
    static final ProcInstance ib = new ProcInstance(pb, 2, 1);

    static final Automaton A = new Automaton(pa);
    static final Automaton B = new Automaton(pb);

    @Test
    public void equalsContract() {
        createVerifier(Proctype.class)
            .withRedefinedSubclass(ProcInstance.class)
            .verify();
        createVerifier(ProcInstance.class)
            .withRedefinedSuperclass()
            .verify();
        createVerifier(Variable.class)
            .suppress(Warning.NONFINAL_FIELDS).verify();
        createVerifier(Identifier.class).verify();
        createVerifier(AritmicExpression.class).verify();
        createVerifier(BooleanExpression.class).verify();
        createVerifier(ChannelOperation.class).verify();
        createVerifier(ChannelLengthExpression.class).verify();
        createVerifier(ChannelReadExpression.class).verify();
        createVerifier(CompareExpression.class).verify();
        createVerifier(ConstantExpression.class).verify();
        createVerifier(EvalExpression.class).verify();
        createVerifier(MTypeReference.class).verify();
        createVerifier(RemoteRef.class).verify();
        createVerifier(RunExpression.class).verify();
    }

    private static <T> EqualsVerifier<T> createVerifier(Class<T> c) {
        EqualsVerifier<T> verifier =
                EqualsVerifier.forClass(c)
            .withPrefabValues(Identifier.class, id(x), id(y))
            .withPrefabValues(Token.class, tx, ty)
            .withPrefabValues(Variable.class, x, y)
            .withPrefabValues(VariableType.class, VariableType.BIT, VariableType.INT)
            .withPrefabValues(Proctype.class, pa, pb)
            .withPrefabValues(ProcInstance.class, ia, ib)
            .withPrefabValues(Automaton.class, A, B)
            .suppress(Warning.NULL_FIELDS);
        return verifier;
    }
}
