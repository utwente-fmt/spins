package spins.promela.compiler.ltsmin.matrix;

import java.util.List;

import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminModelFeature;
import spins.promela.compiler.ltsmin.model.LTSminModelFeature.ModelFeature;
import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.ltsmin.util.LTSminProgress;

public class ModelMatrixGenerator<X extends LTSminModelFeature,
                                  Y extends LTSminModelFeature> {
    
/*
 *  Use case:
 *  
        DepMatrix ndt = createGen(model.TRANSITIONS, model.TRANSITIONS,
            new Checker<LTSminTransition, LTSminTransition>() {
                public boolean check(int xi, int yi, LTSminTransition x, LTSminTransition y) {
                    DepMatrix nds = guardInfo.getNDSMatrix();
                    for (int g2 : guardInfo.getTransMatrix().get(yi)) { 
                        if (nds.isDependent(g2, xi)) {
                            return true;
                        }
                    }
                    return false;
                }
        }).reportInverse().name("!NDS transitions").generate();
 *
 * Unfortunately this carries a runtime penalty of a factor 3!
 * Moreover the code length does not reduce significantly. 
 */
    
    
    private final List<X> xList;
    private final List<Y> yList;
    private final int xSize;
    private final int ySize;
    private final String xName;
    private final String yName;
    private final Checker<X, Y> checker;

    private boolean symmetry = false;
    private boolean reportInverse = false;
    private String name = null;

    public static LTSminModel model = null;
    public static LTSminDebug debug = new LTSminDebug(false, true);
    private final LTSminProgress report;

    public static <A extends LTSminModelFeature, B extends LTSminModelFeature> 
        ModelMatrixGenerator<A,B>
    createGen(ModelFeature<A> xAxis, ModelFeature<B> yAxis, Checker<A, B> c)
    {
        return new ModelMatrixGenerator<A, B>(xAxis, yAxis, c);
    }

    private ModelMatrixGenerator(ModelFeature<X> xAxis, ModelFeature<Y> yAxis,
                                 Checker<X, Y> c) {
         this.xList = xAxis.getInstances();
         this.yList = yAxis.getInstances();
         this.xSize = xList.size();
         this.ySize = yList.size();
         this.xName = xAxis.getName();
         this.yName = yAxis.getName();
         this.checker = c;
         if (symmetry && xSize != ySize) {
             throw new AssertionError("Symmetry not possible between "+ xAxis +"s and "+ yAxis +"s");
         }
         report = new LTSminProgress(debug);
    }

    public ModelMatrixGenerator<X, Y> reportInverse() {
        this.reportInverse = true;
        return this;
    }

    public ModelMatrixGenerator<X, Y> symmetric() {
        this.symmetry = true;
        return this;
    }

    public ModelMatrixGenerator<X, Y> name(String name) {
        this.name = name;
        return this;
    }

    private String getName() {
        return name != null ? name : xName +"/"+ yName +" dependencies";
    }

    private class Result {
        public final int total;
        public final DepMatrix matrix;
        public int num = 0;

        public Result (int total) {
            this.total = total;
            report.setTotal(total);
            this.matrix = new DepMatrix(xSize, ySize);
        }
    }
    
    private Result generateSymmetric () {
        Result result = new Result(xSize * ySize / 2);
        for (int xi = 0; xi < xSize; xi++) {
            for (int yi = xi + 1; yi < ySize; yi++) {
                X x = xList.get(xi);
                Y y = yList.get(yi);
                if (checker.check(xi, yi, x, y)) {
                    result.matrix.setDependent(xi, yi);
                    result.matrix.setDependent(yi, xi);
                    result.num++;
                }
                report.updateProgress();
            }
        }
        return result;
    }

    private Result generateCross () {
        Result result = new Result(xSize * ySize);
        for (X x : xList) {
            for (Y y : yList) {
                int xi = x.getIndex();
                int yi = y.getIndex();
                if (checker.check(xi, yi, x, y)) {
                    result.matrix.setDependent(xi, yi);
                    result.num++;
                }
                report.updateProgress();
            }
        }
        return result;
    }

    public DepMatrix generate () {
        Result result;
        if (symmetry) {
            result = generateSymmetric ();
        } else {
            result = generateCross ();
        }

        if (reportInverse)
            result.num = result.total - result.num;
        report.overwriteReport(result.num, getName());
        return result.matrix;
    }
}
