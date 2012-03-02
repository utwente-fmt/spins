package spinja.promela.compiler.ltsmin.model;

import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.CHAN_FILL_VAR;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.CHAN_READ_VAR;
import static spinja.promela.compiler.parser.PromelaConstants.IDENTIFIER;
import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.expression.AritmicExpression;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.ConstantExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spinja.promela.compiler.ltsmin.state.LTSminPointer;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.Variable;

public class LTSminUtil {

	public static ChannelTopExpression channelTop(Identifier id, int i) {
		return new ChannelTopExpression(new ChannelReadAction(null, id), i);
	}

	public static AssignAction assign(Variable v, Expression expr) {
		return assign (id(v), expr);
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

	public static Identifier id(Variable v, Expression mod, Identifier sub) {
		return new Identifier(new Token(IDENTIFIER,v.getName()), v, mod, sub);
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
	
	public static Expression compare(int m, Expression e1, int nr) {
		return compare(m, e1, constant(nr));
	}

	public static ConstantExpression constant(int nr) {
		return new ConstantExpression(new Token(PromelaConstants.NUMBER, ""+nr), nr);
	}

	public static Identifier chanLength(Identifier id) {
		return new Identifier(id, CHAN_FILL_VAR);
	}

	public static Identifier chanRead(Identifier id) {
		return new Identifier(id, CHAN_READ_VAR);
	}

	public static String printPC(Proctype process, LTSminPointer out) {
		Variable var = out.getPC(process);
		return printVar(var, out);
	}

	public static String printPID(Proctype process, LTSminPointer out) {
		Variable var = out.getPID(process);
		return printVar(var, out);
	}

	public static String printVar(Variable var, LTSminPointer out) {
		return printId(new Identifier(var), out);
	}

	public static String printId(Identifier id, LTSminPointer out) {
		ExprPrinter printer = new ExprPrinter(out);
		return printer.print(id);
	}

	public static ParseException exception(String string, Token token) {
		return new ParseException(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}

	public static AssertionError error(String string, Token token) {
		return new AssertionError(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}
}
