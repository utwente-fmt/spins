/**
 * 
 */
package spinja.promela.compiler.ltsmin.instr;

import static spinja.promela.compiler.ltsmin.LTSminStateVector.*;

import java.util.Set;

import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.ltsmin.LTSminTreeWalker;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.promela.compiler.variable.VariableType;

public class PCExpression extends Expression {
    private String processName;

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public PCExpression(String processName) {
        super(new Token(PromelaConstants.NUMBER,C_STATE_TMP + "." + LTSminTreeWalker.wrapName(processName) + "." + C_STATE_PROC_COUNTER));
        this.processName = processName;
    }
    public Set<VariableAccess> readVariables() {
        return null;
    }
    public VariableType getResultType() throws ParseException {
        return null;
    }

    @Override
    public String getIntExpression() {
        return C_STATE_TMP + "." + LTSminTreeWalker.wrapName(processName) + "." + C_STATE_PROC_COUNTER;
    }
}