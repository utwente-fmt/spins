/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import java.util.Set;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.ltsmin.LTSminTreeWalker;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.promela.compiler.variable.VariableType;

public class PriorityExpression extends Expression {

    public PriorityExpression() {
        super(new Token(PromelaConstants.NUMBER,LTSminTreeWalker.C_STATE_TMP + "." + LTSminTreeWalker.C_PRIORITY));
    }
    public Set<VariableAccess> readVariables() {
        return null;
    }
    public VariableType getResultType() throws ParseException {
        return null;
    }

    @Override
    public String getIntExpression() {
        return LTSminTreeWalker.C_STATE_TMP + "." + LTSminTreeWalker.C_PRIORITY;
    }

}