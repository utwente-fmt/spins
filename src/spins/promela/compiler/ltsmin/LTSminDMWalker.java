package spins.promela.compiler.ltsmin;

import static spins.promela.compiler.ltsmin.state.LTSminStateVector._NR_PR;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.chanLength;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelBottom;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelIndex;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.channelNext;
import static spins.promela.compiler.ltsmin.util.LTSminUtil.id;

import java.util.List;
import java.util.Map;

import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.actions.AssertAction;
import spins.promela.compiler.actions.AssignAction;
import spins.promela.compiler.actions.BreakAction;
import spins.promela.compiler.actions.ChannelReadAction;
import spins.promela.compiler.actions.ChannelSendAction;
import spins.promela.compiler.actions.ElseAction;
import spins.promela.compiler.actions.ExprAction;
import spins.promela.compiler.actions.GotoAction;
import spins.promela.compiler.actions.OptionAction;
import spins.promela.compiler.actions.PrintAction;
import spins.promela.compiler.actions.Sequence;
import spins.promela.compiler.expression.ChannelReadExpression;
import spins.promela.compiler.expression.ConstantExpression;
import spins.promela.compiler.expression.EvalExpression;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.expression.Identifier;
import spins.promela.compiler.expression.MTypeReference;
import spins.promela.compiler.expression.NAryExpression;
import spins.promela.compiler.expression.RemoteRef;
import spins.promela.compiler.expression.RunExpression;
import spins.promela.compiler.expression.TimeoutExpression;
import spins.promela.compiler.expression.TranslatableExpression;
import spins.promela.compiler.ltsmin.LTSminTreeWalker.Options;
import spins.promela.compiler.ltsmin.matrix.DepMatrix;
import spins.promela.compiler.ltsmin.matrix.LTSminGuard;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spins.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spins.promela.compiler.ltsmin.matrix.LTSminLocalGuard;
import spins.promela.compiler.ltsmin.matrix.RWMatrix;
import spins.promela.compiler.ltsmin.matrix.RWMatrix.RWDepRow;
import spins.promela.compiler.ltsmin.model.GuardInfo;
import spins.promela.compiler.ltsmin.model.LTSminIdentifier;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.ltsmin.model.ResetProcessAction;
import spins.promela.compiler.ltsmin.state.LTSminSlot;
import spins.promela.compiler.ltsmin.state.LTSminSubVector;
import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.ltsmin.util.LTSminDebug.MessageKind;
import spins.promela.compiler.ltsmin.util.LTSminProgress;
import spins.promela.compiler.ltsmin.util.LTSminRendezVousException;
import spins.promela.compiler.parser.PromelaConstants;
import spins.promela.compiler.variable.ChannelType;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableType;

/**
 * Traverses LTSminModel structure and records read/write dependencies of
 * variables (slots in LTSmin state vector) in a matrix.
 * 
 * The code heavily depends on the state vector's ability to be navigated upon
 * its type structure and subdivided into sub vectors.
 * 
 * @see LTSminSubVector
 * 
 * @see Blom, van de Pol, Weber - Bridging the Gap between Enumerative and Symbolic Model Checkers 
 *
 * @author FIB, Alfons Laarman
 */
public class LTSminDMWalker {

	// An expression without constant value, to be used as unknow array index: 
	static final Identifier STAR = new LTSminIdentifier(new Variable(VariableType.INT, "STAR", -1));
	
	static public class Params {
		public LTSminModel model;
		public RWMatrix depMatrix;
		public GuardInfo gi;
		public Options opts;
		public int trans;
		public boolean inTimeOut = false;

		public Params(LTSminModel model, GuardInfo gi, RWMatrix depMatrix,
		              int trans, Options opts) {
			this.model = model;
			this.gi = gi;
			this.depMatrix = depMatrix;
			this.trans = trans;
			this.opts = opts;
		}
	}

	private static Params sParams = new Params(null, null, new RWMatrix(null, null, null),
	                                           0, new Options(false, false, false, false, false));
	public static void walkOneGuard(LTSminModel model, DepMatrix dm,
									Expression e, int num) {
	    sParams.model = model;
	    sParams.depMatrix.read = dm;
	    sParams.trans = num;
        walkExpression(sParams, e, MarkAction.READ);
	}

	static void walkModel(LTSminModel model, LTSminDebug debug, Options opts) {
        int nTrans = model.getTransitions().size();
        int nSlots = model.sv.size();

        if (model.getGuardInfo() == null)
            model.setGuardInfo(new GuardInfo(model));
        GuardInfo guardInfo = model.getGuardInfo();
        if (model.getDepMatrix() != null)
            debug.say(MessageKind.FATAL, "Dependency matrix is already set");
        model.setDepMatrix(new RWMatrix(nTrans, nSlots));

        LTSminProgress report = new LTSminProgress(debug);
        debug.say("Generating transitions/state dependency matrices (%d / %d slots) ... ", nTrans, nSlots);
        report.resetTimer().startTimer();
        debug.say_indent++;

        // extact guards
        generateTransitionGuardLabels (model, guardInfo, debug, opts);

        // add the normal state labels
        // We extend the NES and NDS matrices to include all labels
        // The special labels, e.g. progress and valid end, can then be used in
        // LTL properties with precise (in)visibility information.
        for (Map.Entry<String, LTSminGuard> label : model.getLabels()) {
            guardInfo.addLabel(label.getKey(), label.getValue());
        }

        // generate label / slot read matrix
        generateLabelMatrix (model, guardInfo, report);

        Params params = new Params(model, guardInfo, model.getDepMatrix(), 0, opts);
		walkTransitions(params, report);

		debug.say_indent--;
		debug.say("Generating transition/state dependency matrices done (%s sec)",
		          report.stopTimer().sec());
		debug.say("");
	}

    private static void generateLabelMatrix(LTSminModel model,
                                            GuardInfo guardInfo,
                                            LTSminProgress report) {
        int nLabels = guardInfo.getNumberOfLabels();
        int nSlots = model.sv.size();
        int nTrans = model.getDepMatrix().getNrRows();

        DepMatrix gm = new DepMatrix(nLabels, nSlots);
        guardInfo.setDepMatrix(gm);
        int reads = 0;
        report.setTotal(nLabels * nSlots);
        for (int i = 0; i < nLabels; i++) {
            LTSminGuard g = guardInfo.get(i);
            LTSminDMWalker.walkOneGuard(model, gm, g.expr, i);
            reads += gm.getRow(i).getCardinality();
            report.updateProgress(nSlots);
        }
        report.overwriteTotals(reads, "Guard/slot reads");

        DepMatrix testset = new DepMatrix(nTrans, nSlots);
        guardInfo.setTestSetMatrix(testset);
        int tests = 0;
        report.setTotal(nTrans * nSlots);
        for (int t = 0; t < nTrans; t++) {
            for (int gg : guardInfo.getTransMatrix().get(t)) {
                testset.orRow(t, gm.getRow(gg));
            }
            tests += testset.getRow(t).getCardinality();
            report.updateProgress(nSlots);
        }
        report.overwriteTotals(tests, "Transition/slot tests");
    }

    static void generateTransitionGuardLabels(LTSminModel model,
                                              GuardInfo gi,
                                              LTSminDebug debug,
                                              Options opts) {
        for(LTSminTransition t : model.getTransitions()) {
            for (LTSminGuardBase g : t.getGuards()) {
                walkGuard(model, gi, debug, t, g, opts);
            } // we do not have to handle atomic actions since the first guard only matters
        }
    }

    /* Split guards */
    static void walkGuard(LTSminModel model, GuardInfo gi, LTSminDebug debug,
                          LTSminTransition t, LTSminGuardBase guard,
                          Options opts) {
        if (guard instanceof LTSminLocalGuard) { // Nothing
        } else if (guard instanceof LTSminGuard) {
            LTSminGuard g = (LTSminGuard)guard;
            if (g.getExpression() == null)
                return;
            gi.addGuard(t.getGroup(), g.getExpression(), debug, opts);
        } else {
            throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
        }
    }

	static void walkTransitions(Params params, LTSminProgress report) {
	    int nSlots = params.model.sv.size();
        int nTrans = params.model.getTransitions().size();
        int nActions = params.model.getActions().size();

        // Individual actions
        RWMatrix a2s = new RWMatrix(nActions, nSlots);
        params.model.setActionDepMatrix(a2s);
        report.setTotal(nActions * nSlots);
        int reads = 0;
        int mayWrites = 0;
        int mustWrites = 0;
        Params p = new Params(params.model, null, a2s, -1, params.opts);
        for (Action a : params.model.getActions()) {
            p.trans = a.getIndex();
            walkAction (p, a);
            RWDepRow row = a2s.getRow(a.getIndex());
            reads += row.readCardinality();
            mayWrites += row.mayWriteCardinality();
            mustWrites += row.mustWriteCardinality();
            report.updateProgress(nSlots);
        }
        report.overwriteTotals(reads, mayWrites, mustWrites, "Actions/slot r,W,w");

        // Transitions and their atomic follow-ups (including guards of follow-ups)
        RWMatrix atomicDep = new RWMatrix(nTrans, nSlots);
        params.model.setAtomicDepMatrix(atomicDep);
        report.setTotal(nTrans * nSlots);
	    reads = 0;
	    mayWrites = 0;
	    mustWrites = 0;
		for (LTSminTransition t : params.model.getTransitions()) {
			walkTransition(params, a2s, atomicDep, t);
			RWDepRow row = atomicDep.getRow(t.getGroup());
            reads += row.readCardinality();
            mayWrites += row.mayWriteCardinality();
            mustWrites += row.mustWriteCardinality();
			report.updateProgress(nSlots);
		}
        report.overwriteTotals(reads, mayWrites, mustWrites, "Atomics/slot r,W,w");

        // preserve the dep matrix with only action dependencies!
        // copy the matrix so that we can add guard dependencies 
        params.depMatrix = new RWMatrix(atomicDep);
        params.model.setDepMatrix(params.depMatrix);

        // For complete R/W, we only need to add guards to atomicDep
        report.setTotal(nTrans * nSlots);
        reads = 0;
        mayWrites = 0;
        mustWrites = 0;
        DepMatrix t2g = params.gi.getTestSetMatrix();
        for (LTSminTransition t : params.model.getTransitions()) {
            params.depMatrix.read.orRow(t.getGroup(), t2g.getRow(t.getGroup()));
            report.updateProgress(nSlots);
            RWDepRow row = params.depMatrix.getRow(t.getGroup());
            reads += row.readCardinality();
            mayWrites += row.mayWriteCardinality();
            mustWrites += row.mustWriteCardinality();
        }
        report.overwriteTotals(reads, mayWrites, mustWrites, "Transition/slot r,W,w");    
	}

	static void walkTransition(Params params, RWMatrix a2s, RWMatrix atomicDep,
	                           LTSminTransition t) {
		for (Action a : t.getActions()) {
		    atomicDep.orRow(t.getGroup(), a2s.getRow(a.getIndex()));
		}

        DepMatrix t2g = params.gi.getTestSetMatrix();
		// transitively add dependencies of atomic transitions
		for (LTSminTransition atomic : t.getTransitions()) {
			for (Action a : atomic.getActions()) {
			    int act = a.getIndex();
	            atomicDep.orRow(t.getGroup(), a2s.getRow(act));
	            if (params.opts.must_write) {
	                atomicDep.read.orRow(t.getGroup(), a2s.mayWrite.getRow(act));
	            }	            
	            atomicDep.mustWrite.clearRow(t.getGroup());
			}
            atomicDep.read.orRow(t.getGroup(), t2g.getRow(atomic.getGroup()));
		}
	}

	static void walkGuard(Params params, LTSminGuardBase guard) {
		if (guard instanceof LTSminLocalGuard) { // Nothing
		} else if(guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			walkExpression(params, g.expr, MarkAction.READ);
		} else if(guard instanceof LTSminGuardContainer) {
			LTSminGuardContainer g = (LTSminGuardContainer)guard;
			for(LTSminGuardBase gb : g) {
				walkGuard(params,gb);
			}
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}

	static void walkAction(Params params, Action a) {
		if(a instanceof AssignAction) {
			AssignAction as = (AssignAction)a;
			Identifier id = as.getIdentifier();
			walkExpression(params, id, MarkAction.MAY_MUST_WRITE); // read
			switch (as.getToken().kind) {
				case PromelaConstants.ASSIGN:
					walkExpression(params, as.getExpr(), MarkAction.READ); // read
					break;
				case PromelaConstants.DECR:
				case PromelaConstants.INCR:
					walkExpression(params, id, MarkAction.READ);
					break;
				default:
					throw new AssertionError("unknown assignment type");
			}
		} else if(a instanceof ResetProcessAction) {
			ResetProcessAction rpa = (ResetProcessAction)a;
			DMIncMayMustWritePC(params, rpa.getProcess());
			DMIncMayMustWrite(params, _NR_PR);
			DMIncRead(params, _NR_PR); // also done by guard, just to be sure
			DMIncMayMustWriteEntire(params, params.model.sv.sub(rpa.getProcess()));
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();
			walkExpression(params, e, MarkAction.READ);
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			List<Expression> exprs = pa.getExprs();
			for (final Expression expr : exprs) {
				walkExpression(params, expr, MarkAction.READ);
			}
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			if (expr.getSideEffect() == null) return; // simple expressions are guards
			//a RunExpression has side effects... yet it does not block if less than 255 processes are started atm
			assert (expr instanceof RunExpression);
			DMIncMayMustWrite(params, _NR_PR);
			DMIncRead(params, _NR_PR);
			RunExpression re = (RunExpression)expr;
			for (Proctype p : re.getInstances()) {
				DMIncMayMustWritePC(params, p);
				DMIncReadPC(params, p); // we also read to check multiple instantiations
				DMIncMayMustWritePID(params, p);
				//write to the arguments of the target process
				for (Variable v : p.getArguments()) {
					if (v.getType() instanceof ChannelType || v.isStatic())
						continue; //passed by reference or in initial state 
					DMIncMayMustWrite(params, v);
				}
			}
			for (Action rea : re.getInitActions()) {
				walkAction(params, rea);
			}
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			LTSminGuardAnd orc = new LTSminGuardAnd();
			for (Sequence seq : oa) {
				Action act = seq.iterator().next();
				try {
                    LTSminTreeWalker.createEnabledGuard(act, orc);
                } catch (LTSminRendezVousException e) {
                    throw new AssertionError(e);
                }
				for (Action sa : seq) {
					walkAction(params, sa);
				}
			}
			walkGuard(params, orc);
		} else if(a instanceof BreakAction) {
			// noop
		} else if(a instanceof ElseAction) {
			// noop
		} else if(a instanceof GotoAction) { // only in d_steps (not a transition)
			// noop
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;			
			Identifier id = csa.getIdentifier();
			int m = 0;
			for (Expression e : csa.getExprs()) {
				Expression top = channelNext(id, m++);
				walkExpression(params, top, MarkAction.EMAY_MUST_WRITE);
				walkExpression(params, e, MarkAction.EREAD);
			}
			walkExpression(params, chanLength(id), MarkAction.BOTH);
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;			
			Identifier id = cra.getIdentifier();
			int m = 0;
			// the read action itself:
			for (Expression e : cra.getExprs()) {
				Expression read = cra.isRandom() ? channelIndex(id, STAR, m) :
									channelBottom(id, m);
				if (e instanceof Identifier) { // otherwise it is a guard!
					walkExpression(params, e, MarkAction.EMAY_MUST_WRITE);
					walkExpression(params, read, MarkAction.EREAD);
				}
				m++;
			}
			if (!cra.isPoll()) {
				walkExpression(params, chanLength(id), MarkAction.BOTH);
				// the replacement action:
				m = 0;
				for (@SuppressWarnings("unused") Expression e : cra.getExprs()) {
					Expression buf = channelIndex(id, STAR, m++);
					walkExpression(params, buf, MarkAction.EBOTH);
				}
			}
		} else { // Handle not yet implemented action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}
	
	/**
	 * Uses Identifier expressions to walk over LTSmin sub vectors. Complete
	 * identifiers, i.e. those that point to (an index of) a native type,
	 * will be marked in the DM using the associated MarkAction. Incomplete ones
	 * throw an AssertionError.
	 */
	public static class IdMarker {
		MarkAction mark;
		public Params params;
		public IdMarker(Params params, MarkAction mark) {
			this.params = params;
			this.mark = mark;
		}
		public IdMarker(IdMarker idMarker, MarkAction mark) {
			this.params = idMarker.params;
			this.mark = mark;
		}

		public void mark(Expression e) {
			if (null == e) return;
			if (e instanceof LTSminIdentifier) {
			} else if (e instanceof Identifier) {
				Identifier id = (Identifier)e;
				if (id.getVariable().isHidden())
					return;
				LTSminSubVector sub = params.model.sv.sub(id.getVariable());
				try {
					sub.mark(this, id);
				} catch (AssertionError ae) {
				    ae.printStackTrace();
					throw new AssertionError("Marking expression "+ id +" failed with: "+ ae);
				}
			} else {
				walkExpression(params, e, mark);
			}
		}

		public void doMark(LTSminSubVector sub) {
			mark.DMIncMark(params, sub);
		}
        public boolean isStrict() {
            return mark.strict;
        }
        public boolean isMayMustWrite() {
            return mark == MarkAction.MAY_MUST_WRITE || mark == MarkAction.EMAY_MUST_WRITE;
        }
	}

	/**
	 * A marker for SLOTS in the state vector: READ, WRITE or BOTH.
	 */
	public static enum	MarkAction {
	    
		READ(true),
        MAY_MUST_WRITE(true),
        MAY_WRITE(true),
		BOTH(true),
		EREAD(false),
        EMAY_MUST_WRITE(false),
        EMAY_WRITE(false),
        EBOTH(false);

		boolean strict;
        MarkAction(boolean strict) {
            this.strict = strict;
        }
        
		private void DMIncMark(Params params, LTSminSubVector sub) {
			switch (this) {
			case EREAD:
			case READ: DMIncRead (params, sub); break;
            case EMAY_MUST_WRITE:
            case MAY_MUST_WRITE:DMIncMayMustWrite(params, sub); break;
            case EMAY_WRITE:
            case MAY_WRITE:DMIncMayWrite(params, sub); break;
			case EBOTH:
			case BOTH: DMIncRead (params, sub);
					   DMIncMayMustWrite(params, sub); break;
			default: throw new AssertionError("Not implemented "+ this);
			}
		}
	}

	static void walkExpression(Params params, Expression e, MarkAction mark) {
		if ((mark == MarkAction.MAY_MUST_WRITE || mark == MarkAction.EMAY_MUST_WRITE) && !(e instanceof Identifier))
			throw new AssertionError("Only identifiers and TopExpressions can be written to!");
		if(e instanceof LTSminIdentifier) { //nothing
		} else if(e instanceof Identifier) {
			new IdMarker(params, mark).mark(e);
		} else if(e instanceof NAryExpression) {
            NAryExpression ae = (NAryExpression)e;
            for (Expression expr : ae) {
                walkExpression(params, expr, mark);
            }
		} else if(e instanceof TranslatableExpression) {
		    TranslatableExpression te = (TranslatableExpression)e;
			walkExpression(params, te.translate(), mark);
		} else if(e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = cre.getIdentifier();
			// mark variables as read
			int m = 0;
			for (Expression expr : cre.getExprs()) {
				Expression read = cre.isRandom() ? channelIndex(id, STAR, m) :
													channelBottom(id, m);
				walkExpression(params, read, mark);
				walkExpression(params, expr, mark);
				m++;
			}
			walkExpression(params, chanLength(id), mark);
		} else if(e instanceof RunExpression) {
			DMIncRead(params, _NR_PR); // only the guard!
		} else if(e instanceof MTypeReference) {
		} else if(e instanceof ConstantExpression) {
        } else if (e instanceof TimeoutExpression) {
            TimeoutExpression timeout = (TimeoutExpression)e;
            Expression deadlock = timeout.getDeadlock();
            walkExpression(params, deadlock, mark);
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
			Expression labelExpr = rr.getLabelExpression(params.model);
			walkExpression(params, labelExpr, mark);
		} else if(e instanceof EvalExpression) {
			EvalExpression eval = (EvalExpression)e;
			walkExpression(params, eval.getExpression(), mark);
		} else {
			throw new AssertionError("LTSminDMWalker: Not yet implemented: "+e.getClass().getName());
		}
	}

    static void DMIncMayMustWrite(Params params, LTSminSubVector sub) {
        if (!(sub instanceof LTSminSlot))
            throw new AssertionError("Variable is not a native type: "+ sub);
        int offset = ((LTSminSlot)sub).getIndex();
        params.depMatrix.incMayWrite(params.trans, offset);
        params.depMatrix.incMustWrite(params.trans, offset);
    }

    static void DMIncMayWrite(Params params, LTSminSubVector sub) {
        if (!(sub instanceof LTSminSlot))
            throw new AssertionError("Variable is not a native type: "+ sub);
        int offset = ((LTSminSlot)sub).getIndex();
        params.depMatrix.incMayWrite(params.trans, offset);
    }

	static void DMIncRead(Params params, LTSminSubVector sub) {
		if (!(sub instanceof LTSminSlot))
			throw new AssertionError("Variable is not a native type: "+ sub);
		int offset = ((LTSminSlot)sub).getIndex();
		params.depMatrix.incRead(params.trans, offset);
	}

	static void DMIncMayMustWriteEntire(Params params, LTSminSubVector sub) {
		for (LTSminSlot slot : sub) {
			DMIncMayMustWrite(params, slot);
		}
	}

	private static void DMIncReadPC(Params params, Proctype p) {
		Variable pc = params.model.sv.getPC(p);
		DMIncRead(params, pc);
	}

	private static void DMIncMayMustWritePC(Params params, Proctype p) {
		Variable pc = params.model.sv.getPC(p);
		DMIncMayMustWrite(params, pc);
	}

	private static void DMIncMayMustWritePID(Params params, Proctype p) {
		Variable pc = params.model.sv.getPID(p);
		DMIncMayMustWrite(params, pc);
	}
	
	private static void DMIncRead(Params params, Variable var) {
		walkExpression(params, id(var), MarkAction.READ);
	}

	private static void DMIncMayMustWrite(Params params, Variable var) {
		walkExpression(params, id(var), MarkAction.MAY_MUST_WRITE);
	}
}
