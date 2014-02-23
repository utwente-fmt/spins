package spins.promela.compiler.ltsmin.util;

import static spins.promela.compiler.ltsmin.state.LTSminTypeChanStruct.CHAN_FILL_VAR;
import static spins.promela.compiler.ltsmin.state.LTSminTypeChanStruct.bufferVar;
import static spins.promela.compiler.ltsmin.state.LTSminTypeChanStruct.elemVar;
import static spins.promela.compiler.parser.PromelaConstants.EQ;
import static spins.promela.compiler.parser.PromelaConstants.GT;
import static spins.promela.compiler.parser.PromelaConstants.GTE;
import static spins.promela.compiler.parser.PromelaConstants.IDENTIFIER;
import static spins.promela.compiler.parser.PromelaConstants.LT;
import static spins.promela.compiler.parser.PromelaConstants.LTE;
import static spins.promela.compiler.parser.PromelaConstants.NEQ;

import java.util.Arrays;
import java.util.HashSet;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.actions.AssignAction;
import spins.promela.compiler.actions.ChannelReadAction;
import spins.promela.compiler.actions.ChannelSendAction;
import spins.promela.compiler.automaton.State;
import spins.promela.compiler.automaton.Transition;
import spins.promela.compiler.expression.AritmicExpression;
import spins.promela.compiler.expression.BooleanExpression;
import spins.promela.compiler.expression.ChannelLengthExpression;
import spins.promela.compiler.expression.CompareExpression;
import spins.promela.compiler.expression.ConstantExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spins.promela.compiler.ltsmin.matrix.LTSminPCGuard;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.state.LTSminPointer;
import spins.promela.compiler.ltsmin.state.LTSminStateVector;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.ChannelVariable;
import spins.promela.compiler.variable.Variable;

public class LTSminUtil {

    public static class Pair<L,R> {
        public L left; public R right;
        public Pair(L l, R r) { this.left = l; this.right = r; }
    }

	/** Expressions **/
	public static Identifier channelBottom(Identifier id, int elem) {
		return channelIndex(id, constant(0), elem) ;
	}

	public static Identifier channelNext(Identifier id, int elem) {
		return channelIndex(id, chanLength(id), elem) ;
	}

	public static Identifier channelIndex(Identifier id, Expression index, int elem) {
		ChannelVariable cv = (ChannelVariable)id.getVariable();
		//int size = cv.getType().getBufferSize();
		//if (1 == size) index = constant(0);
		Identifier m = id(elemVar(elem));
		Identifier buf = id(bufferVar(cv), index, m);
		return new Identifier(id, buf);
	}

	public static AssignAction assign(Variable v, Expression expr) {
		return assign (id(v), expr);
	}

	public static AssignAction incr(Identifier id) {
		return new AssignAction(new Token(PromelaConstants.INCR,"++"), id, null);
	}

	public static AssignAction decr(Identifier id) {
		return new AssignAction(new Token(PromelaConstants.DECR,"--"), id, null);
	}

	public static AssignAction assign(Identifier id, Expression expr) {
		return new AssignAction(new Token(PromelaConstants.ASSIGN,"="), id, expr);
	}

	public static AssignAction assign(Variable v, int nr) {
		return assign(id(v), constant(nr));
	}

	public static Identifier id(Variable v) {
		return new Identifier(new Token(IDENTIFIER,v.getName()), v, null);
	}

	public static Identifier id(Variable v, int c) {
		return new Identifier(new Token(IDENTIFIER,v.getName()), v, constant(c), null);
	}

	public static Identifier id(Variable v, Expression arrayExpr, Identifier sub) {
		return new Identifier(new Token(IDENTIFIER,v.getName()), v, arrayExpr, sub);
	}

	public static CompareExpression compare(int m, Expression e1, Expression e2) {
		String name = PromelaConstants.tokenImage[m];
		return new CompareExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}
	
	public static BooleanExpression bool(int m, Expression e1, Expression e2) {
		String name = PromelaConstants.tokenImage[m];
		return new BooleanExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}
	 
	public static AritmicExpression calc(int m, Expression e1, Expression e2) {
		String name = PromelaConstants.tokenImage[m];
		return new AritmicExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}

	public static BooleanExpression not(Expression e1) {
		int m = PromelaConstants.LNOT;
		String name = PromelaConstants.tokenImage[m];
		return new BooleanExpression(new Token(m,name.substring(1,name.length()-1)), e1);
	}

	public static Expression negate(Expression e) {
        if (e instanceof BooleanExpression &&
                e.getToken().kind ==  PromelaConstants.LNOT) {
            return ((BooleanExpression) e).getExpr1();
        } else if (e instanceof CompareExpression) {
            CompareExpression ae = (CompareExpression)e;
            switch(ae.getToken().kind) {
                case LT:    return new CompareExpression(ae, GTE);
                case LTE:   return new CompareExpression(ae, GT);
                case EQ:    return new CompareExpression(ae, NEQ);
                case NEQ:   return new CompareExpression(ae, EQ);
                case GT:    return new CompareExpression(ae, LTE);
                case GTE:   return new CompareExpression(ae, LT);
                default: throw new AssertionError("Unknown arithmetic expression kind: "+ ae.getToken());
            }
        } else {
            return not(e);
        }
    }

	public static String getName(int kind) {
        String name = PromelaConstants.tokenImage[kind];
        return name.substring(1, name.length() - 1);
    }

	public static BooleanExpression or(Expression e1, Expression e2) {
		int m = PromelaConstants.LOR;
		String name = PromelaConstants.tokenImage[m];
		return new BooleanExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}

	public static BooleanExpression and(Expression e1, Expression e2) {
		int m = PromelaConstants.LAND;
		String name = PromelaConstants.tokenImage[m];
		return new BooleanExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}

	public static CompareExpression eq(Expression e1, Expression e2) {
		int m = PromelaConstants.EQ;
		String name = PromelaConstants.tokenImage[m];
		return new CompareExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}

	public static Expression compare(int m, Expression e1, int nr) {
		return compare(m, e1, constant(nr));
	}

    public static ConstantExpression bool(boolean b) {
        return new ConstantExpression(new Token(PromelaConstants.BOOL, ""+b), b?1:0);
    }
	
	public static ConstantExpression constant(int nr) {
		return new ConstantExpression(new Token(PromelaConstants.NUMBER, ""+nr), nr);
	}

	public static Identifier chanLength(Identifier id) {
		return new Identifier(id, CHAN_FILL_VAR);
	}
	
	/** Guards **/
	public static LTSminPCGuard pcGuard(LTSminModel model, State s, Proctype p) {
		Variable pc = model.sv.getPC(p);
		Expression left = id(pc);
		Expression right = constant(s.getStateId());
		Expression e = compare(PromelaConstants.EQ, left, right);
		return new LTSminPCGuard(e);
	}

	public static Expression dieGuard(LTSminModel model, Proctype p) {
		Variable pid = model.sv.getPID(p);
		Expression left = calc(PromelaConstants.PLUS, id(pid), constant(1)); 
		return compare (PromelaConstants.EQ, left, id(LTSminStateVector._NR_PR));
	}

	public static Expression chanEmptyGuard(Identifier id) {
		Expression left;
		try {
			left = new ChannelLengthExpression(null, id);
		} catch (ParseException e1) {
			throw new AssertionError(e1);
		}
		Expression right = constant(((ChannelType)id.getVariable().getType()).getBufferSize());
		Expression e = compare(PromelaConstants.LT, left, right);
		return e;
	}

	public static Expression chanContentsGuard(Identifier id) {
		return chanContentsGuard(id, 0);
	}

	public static Expression chanContentsGuard(Identifier id, int i) {
		return chanContentsGuard(id, PromelaConstants.GT, i);
	}

	public static Expression chanContentsGuard(Identifier id, int C, int i) {
        Expression left;
        try {
            left = new ChannelLengthExpression(null, id);
        } catch (ParseException e1) {
            throw new AssertionError(e1);
        }
        Expression e = compare(C, left, constant(i));
        return e;
    }

	/** Strings **/
	public static String printPC(Proctype process, LTSminPointer out) {
		Variable var = out.getPC(process);
		return printVar(var, out);
	}

	public static String printPID(Proctype process, LTSminPointer out) {
		Variable var = out.getPID(process);
		return printVar(var, out);
	}

	public static String printVar(Variable var, LTSminPointer out) {
		return print(new Identifier(var), out);
	}

	public static String print(Expression id, LTSminPointer out) {
		ExprPrinter printer = new ExprPrinter(out);
		return printer.print(id);
	}

	/** others **/
	public static Iterable<Transition> getOutTransitionsOrNullSet(State s) {
		if (s == null)
			return new HashSet<Transition>(Arrays.asList((Transition)null));
		return s.output;
	}

	public static boolean isRendezVousReadAction(Action a) {
		return a instanceof ChannelReadAction && 
				((ChannelReadAction)a).isRendezVous();
	}

	public static boolean isRendezVousSendAction(Action a) {
		return a instanceof ChannelSendAction &&
				((ChannelSendAction)a).isRendezVous();
	}
	
	/** Errors **/
	public static ParseException exception(String string, Token token) {
		return new ParseException(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}

	public static AssertionError error(String string, Token token) {
		return new AssertionError(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}
}
