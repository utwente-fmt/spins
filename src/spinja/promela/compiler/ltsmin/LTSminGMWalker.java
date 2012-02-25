package spinja.promela.compiler.ltsmin;

import java.util.List;

import spinja.promela.compiler.ltsmin.instr.DepMatrix;
import spinja.promela.compiler.ltsmin.instr.GuardInfo;

/**
 *
 * @author FIB
 */
public class LTSminGMWalker {

	static public class Params {
		public final LTSminModel model;
		public final GuardInfo guardMatrix;
		public int trans;
		public int guards;

		public Params(LTSminModel model, GuardInfo guardMatrix) {
			this.model = model;
			this.guardMatrix = guardMatrix;
			this.trans = 0;
			this.guards = 0;
		}
	}

	static void walkModel(LTSminModel model) {
		if(model.getGuardInfo()==null)
			model.setGuardInfo(new GuardInfo(model.getTransitions().size()));
		GuardInfo guardInfo = model.getGuardInfo();
		Params params = new Params(model, guardInfo);
		// extact guards bases
		walkTransitions(params);

		// generate guard DM
		generateGuardMatrix(model, guardInfo);
		
		// generate Maybe Coenabled matrix
		generateCoenMatrix (guardInfo);
	}

	private static void generateGuardMatrix(LTSminModel model,
			GuardInfo guardInfo) {
		DepMatrix dm = new DepMatrix(guardInfo.size(), model.sv.size());
		guardInfo.setDepMatrix(dm);
		for (int i = 0; i < guardInfo.size(); i++) {
			LTSminDMWalker.walkOneGuard(model, dm, guardInfo.get(i), i);
		}
	}

	private static void generateCoenMatrix(GuardInfo gm) {
		DepMatrix co = new DepMatrix(gm.size(), gm.size());
		gm.setCoenMatrix(co);
		for (int i = 0; i < gm.size(); i++) {
			// same guard is always coenabled:
			co.incRead(i, i);
			for (int j = i+1; j < gm.size(); j++) {
				if (!canNeverBeCoenabled(gm.get(i),gm.get(j))) {
					co.incRead(i, j);
					co.incRead(j, i);
				}
			}
		}
	}

	private static boolean canNeverBeCoenabled(LTSminGuard ltSminGuard,
											   LTSminGuard ltSminGuard2) {
		return false;
	}

	static void walkTransitions(Params params) {
		for(LTSminTransitionBase t : params.model.getTransitions()) {
			walkTransition(params,t);
			params.trans++;
		}
	}

	static void walkTransition(	Params params, LTSminTransitionBase transition) {
		if(transition instanceof LTSminTransition) {
			LTSminTransition t = (LTSminTransition)transition;
			List<LTSminGuardBase> guards = t.getGuards();
			for(LTSminGuardBase g: guards)
				walkGuard(params, g);
		} else if (transition instanceof LTSminTransitionCombo) {
			LTSminTransitionCombo t = (LTSminTransitionCombo)transition;
			for(LTSminTransitionBase tb: t.transitions)
				walkTransition(params,tb);
		} else {
			throw new AssertionError("UNSUPPORTED: " + transition.getClass().getSimpleName());
		}
	}

	static void walkGuard(Params params, LTSminGuardBase guard) {
		if(guard instanceof LTSminLocalGuard) { //Nothing
		} else if(guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			params.guardMatrix.addGuard(params.trans, g);
		} else if(guard instanceof LTSminGuardNand) {
			LTSminGuardNand g = (LTSminGuardNand)guard;
			for(LTSminGuardBase gb : g.guards)
				walkGuard(params, gb);
		} else if(guard instanceof LTSminGuardAnd) {
			LTSminGuardAnd g = (LTSminGuardAnd)guard;
			for(LTSminGuardBase gb : g.guards)
				walkGuard(params,gb);
		} else if(guard instanceof LTSminGuardOr) {
			LTSminGuardOr g = (LTSminGuardOr)guard;
			for(LTSminGuardBase gb : g.guards)
				walkGuard(params,gb);
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}
}
