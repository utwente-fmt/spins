/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_PRIORITY;
import static spinja.promela.compiler.ltsmin.LTSminStateVector.C_STATE_TMP;

import java.util.Set;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.promela.compiler.variable.VariableType;

public class PriorityExpression extends Expression {

    public PriorityExpression() {
        super(new Token(PromelaConstants.NUMBER,C_STATE_TMP + "." + C_PRIORITY));
    }
    public Set<VariableAccess> readVariables() {
        return null;
    }
    public VariableType getResultType() throws ParseException {
        return null;
    }

    @Override
    public String getIntExpression() {
        return C_STATE_TMP + "." + C_PRIORITY;
    }

}