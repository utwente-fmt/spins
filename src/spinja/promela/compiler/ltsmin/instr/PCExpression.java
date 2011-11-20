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

public class PCExpression extends Expression {
    private String processName;

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public PCExpression(String processName) {
        super(new Token(PromelaConstants.NUMBER,LTSminTreeWalker.C_STATE_TMP + "." + LTSminTreeWalker.wrapName(processName) + "." + LTSminTreeWalker.C_STATE_PROC_COUNTER));
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
        return LTSminTreeWalker.C_STATE_TMP + "." + LTSminTreeWalker.wrapName(processName) + "." + LTSminTreeWalker.C_STATE_PROC_COUNTER;
    }
}