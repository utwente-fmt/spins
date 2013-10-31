package spins.promela.compiler.ltsmin.model;

import java.util.List;

public interface LTSminModelFeature {

    public int getIndex();

    public abstract class ModelFeature<F extends LTSminModelFeature> {
        private final String name;
        public ModelFeature (String name) { this.name = name; }
        public String getName() { return this.name; }

        public abstract List<F> getInstances(); 
    }
}
