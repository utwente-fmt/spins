// Copyright 2010, University of Twente, Formal Methods and Tools group
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package spins;

import static spins.promela.compiler.ltsmin.util.LTSminUtil.not;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import spins.options.BooleanOption;
import spins.options.MultiStringOption;
import spins.options.OptionParser;
import spins.options.StringOption;
import spins.promela.compiler.Preprocessor;
import spins.promela.compiler.Preprocessor.DefineMapping;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.Specification;
import spins.promela.compiler.automaton.State;
import spins.promela.compiler.expression.Expression;
import spins.promela.compiler.ltsmin.LTSminPrinter;
import spins.promela.compiler.ltsmin.LTSminTreeWalker;
import spins.promela.compiler.ltsmin.LTSminTreeWalker.Options;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.ltsmin.util.LTSminDebug.MessageKind;
import spins.promela.compiler.ltsmin.util.LTSminProgress;
import spins.promela.compiler.optimizer.GraphOptimizer;
import spins.promela.compiler.optimizer.RemoveUselessActions;
import spins.promela.compiler.optimizer.RemoveUselessGotos;
import spins.promela.compiler.optimizer.RenumberAll;
import spins.promela.compiler.optimizer.StateMerging;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Promela;
import spins.promela.compiler.parser.PromelaTokenManager;
import spins.promela.compiler.parser.SimpleCharStream;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.parser.TokenMgrError;

public class Compile {
	private static Specification compile(final File promFile, 
		                                 final boolean useStateMerging,
		                                 final Options opts) {
        LTSminDebug debug = new LTSminDebug(opts.verbose);
		try {
			Preprocessor.setFilename(promFile.getName());
			String path = promFile.getAbsoluteFile().getParent();
			Preprocessor.setDirname(path);

	        LTSminProgress report = new LTSminProgress(debug).startTimer();
			debug.say("Parsing " + promFile.getName() + "...");
			final Promela prom = new Promela(new FileInputStream(promFile));
			final Specification spec = prom.spec("pan");
			debug.say("Parsing " + promFile.getName() + " done (%s sec)",
			           report.stopTimer().sec());
			debug.say("");
			
			if (opts.no_atomic) {
				for (Proctype p : spec.getProcs()) {
					for (State s : p.getAutomaton()) {
						s.setInAtomic(false);
					}
				}
				if (spec.getNever() != null) {
					for (State s : spec.getNever().getAutomaton()) {
						s.setInAtomic(false);
					}
				}
			}

			report.resetTimer().startTimer();
			debug.say("Optimizing graphs...");
			final GraphOptimizer[] optimizers = new GraphOptimizer[] {
					useStateMerging ? new StateMerging() : null, new RemoveUselessActions(),
					new RemoveUselessGotos(), new RenumberAll(),
			};
			for (final GraphOptimizer opt : optimizers) {
				if (opt == null) continue;
				int reduction = 0;
				for (final Proctype proc : spec.getProcs()) {
					debug.say(MessageKind.DEBUG, "Initial graph for process " + proc + ":");
					debug.say(MessageKind.DEBUG, proc.getAutomaton());
					reduction += opt.optimize(proc.getAutomaton());
					debug.say(MessageKind.DEBUG, "After " + opt.getClass().getSimpleName() + ":");
					debug.say(MessageKind.DEBUG, proc.getAutomaton());
				}
				debug.say("   "+ opt.getClass().getSimpleName() +" changed "+ reduction +" states/transitions.");
			}

			final Proctype never = spec.getNever();
			if (never != null) {
				debug.say(MessageKind.DEBUG, "Initial graph for never claim:");
				debug.say(MessageKind.DEBUG, never.getAutomaton());
				for (final GraphOptimizer opt : optimizers) {
					if (opt == null) continue;
					int reduction = opt.optimize(never.getAutomaton());
					debug.say("   "+ opt.getClass().getSimpleName() +" reduces "+ reduction +" states");
					debug.say(MessageKind.DEBUG, "After " + opt.getClass().getSimpleName() + ":");
					debug.say(MessageKind.DEBUG, never.getAutomaton());
				}
				debug.say(MessageKind.DEBUG, never.getAutomaton());
			}
			debug.say("Optimization done (%s sec)", report.stopTimer().sec());
			debug.say("");
			return spec;
		} catch (final FileNotFoundException ex) {
			debug.say("Promela file " + promFile.getName() + " could not be found.");
		} catch (final ParseException ex) {
			debug.say("Parse exception in file " + Preprocessor.getFileName() + ": "
								+ ex.getMessage());
		}
		return null;
	}

	public static void main(final String[] args) {
		final String  shortd  = 
			"SpinS Promela Compiler - version " + Version.VERSION + " (" + Version.DATE + ")\n" +
			"(C) University of Twente, Formal Methods and Tools group";
		String  longd   = 
			"SpinS Promela Compiler: compiles a library from a Promela model.\n" ;

		longd += "The library implements the PINS interface:\n";
		longd += "    - a next-state and state-label function\n";
		longd += "    - transition read/write dependency matrices (used for symbolic exploration)\n";
		longd += "    - guard dependency matrices (used for partial-order reduction)\n";
		longd += "Consult http://fmt.cs.utwente.nl/tools/ltsmin/ for details.\n";

		final OptionParser parser = 
			new OptionParser("java spins.Compiler", shortd, longd, true);

		final StringOption define = new StringOption('D',
			"sets preprocessor macro define value", true);
		parser.addOption(define);

        final StringOption export = new StringOption('E',
            "export #define as state label", true);
        parser.addOption(export);

        final StringOption progress = new StringOption('P',
            "sets #define as progress state label (prepend a '!' to define non-progress)", false);
        parser.addOption(progress);

        final BooleanOption dot = new BooleanOption('d',
            "only write dot output (ltsmin/spins) \n");
        parser.addOption(dot);

		final BooleanOption java = new BooleanOption('J',
			"Java escape semantics for unless statements (equivalent to SPIN's -J option) \n");
		parser.addOption(java);

        final BooleanOption no_cnf = new BooleanOption('C',
            "Do not rewrite guards to CNF\n");
        parser.addOption(no_cnf);

        final BooleanOption no_guards = new BooleanOption('S',
            "speed up compilation by skipping guards \n");
        parser.addOption(no_guards);

        final BooleanOption must_write = new BooleanOption('W',
            "Strengthen write dependencies to must-write (maybe-write will become R+W) \n");
        parser.addOption(must_write);

		final BooleanOption ltsmin_ltl = new BooleanOption('L',
			"sets output to LTSmin LTL semantics \n");
		parser.addOption(ltsmin_ltl);

        final BooleanOption textbook_ltl = new BooleanOption('t',
            "sets output to textbook LTL semantics \n");
        parser.addOption(textbook_ltl);
		
		
		final BooleanOption total = new BooleanOption('T',
			"make next-state a total function\n");
		parser.addOption(total);

		final BooleanOption no_cpy = new BooleanOption('A',
				"do not initialize copy _A_rrays\n");
			parser.addOption(no_cpy);
		
		final BooleanOption useNever = new BooleanOption('N',
			"Force use of never claim (slow; it is preferable to supply the LTL formula to LTSmin).\n");
		parser.addOption(useNever);

        final BooleanOption preprocessor = new BooleanOption('I',
			"prints output of preprocessor\n");
		parser.addOption(preprocessor);

		final MultiStringOption optimalizations = new MultiStringOption('o',
			"disables one or more optimalisations", 
				new String[] { "3" }, 
				new String[] { "disables statement merging" }
			);
		parser.addOption(optimalizations);

		final BooleanOption verbose = new BooleanOption('v',
			"verbose: show diagnostic information on the \n" +
			"compilation process.");
		parser.addOption(verbose);

		final BooleanOption no_atomic = new BooleanOption('i',
			"Ignore atomic sections (better for LTSmin POR and symbolic tools).");
		parser.addOption(no_atomic);

		parser.parse(args);
		final List<String> files = parser.getFiles();

		if (files.size() != 1) {
			System.out.println("Please specify one file that is to be compiled!");
			parser.printUsage();
	        System.exit(-1);
		}

		final File file = new File(files.get(0));
		if (!file.exists() || !file.isFile()) {
			System.out.println("File " + file.getName() + " does not exist or is not a valid file!");
			parser.printUsage();
	        System.exit(-1);
		}

        if (textbook_ltl.isSet()) {
            System.err.println("Textbook LTL semantics not yet implemented.");
            System.exit(-1);
        }

		for (String def : define) {
		    int indexEq = def.indexOf('=');
		    String defName = def;
		    String defArg = "";
		    if (indexEq != -1) {
		        defName = def.substring(0, indexEq).trim();
		        defArg = def.substring(indexEq + 1);
		    }
		    Preprocessor.define.name = defName;
		    Preprocessor.addDefine(defArg, false);
		}

		if (preprocessor.isSet()) {
			Preprocessor.setFilename(file.getName());
			String path = file.getAbsoluteFile().getParent();
			Preprocessor.setDirname(path);
			PromelaTokenManager tm;
			try {
				tm = new PromelaTokenManager(null, new SimpleCharStream(new FileInputStream(file)));
			} catch (FileNotFoundException e) {
				throw new AssertionError(e);
			}
			while (true) {
				try {
					Token t = tm.getNextToken();
					System.out.print(t.image);
					if (t.image == "") break;
				} catch (TokenMgrError e) {
					break;
				}
			}
			System.exit(0);
		}

        Options opts = new Options(verbose.isSet(), no_guards.isSet(),
                					  must_write.isSet(), !no_cnf.isSet(),
                					  java.isSet(), no_atomic.isSet(),
                					  total.isSet(), no_cpy.isSet());

		final Specification spec = 
			Compile.compile(file, !optimalizations.isSet("3"), opts);

		if (!useNever.isSet() && spec.getNever() != null) {
    			LTSminTreeWalker.NEVER = false;
    			System.out.println("\nWARNING: Ignoring NEVER claim. Supply LTL formula to LTSmin or override with -N.\n\n");
        		spec.setNever(null);
        }

        if (spec == null) {
            System.exit(-4);
        }

        Expression progressLabel = null;        
        String progressLabelName = progress.getValue();
        if (progressLabelName != null) {
            boolean np = progressLabelName.startsWith("!"); 
            progressLabelName = np ? progressLabelName.substring(1) : progressLabelName;
            progressLabel = parseDefine(spec, progressLabelName);
            if (np)
                progressLabel = not(progressLabel);
            System.out.println("Rewired progress label to '"+ progressLabelName +"'");
            System.out.println("");
        }
        Map<String, Expression> exportLabels = new HashMap<String, Expression>();
        for (String defined : export) {
            Expression label = parseDefine(spec, defined);
            exportLabels.put(defined, label);
            System.out.println("Exporting state label '"+ defined +"'");
            System.out.println("");
        }


		File outputDir = new File(System.getProperty("user.dir"));
		if (dot.isSet()) {
			Compile.writeLTSminDotFile(spec, file.getName(), outputDir,
			                           ltsmin_ltl.isSet(), opts,
	                                   exportLabels, progressLabel);
			System.out.println("Written DOT file to " + outputDir + "/" + file.getName()+".spins.dot");
		} else {
			Compile.writeLTSMinFiles(spec, file.getName(), outputDir,
			                         ltsmin_ltl.isSet(), opts,
			                         exportLabels, progressLabel);
			System.out.println("Written C code to " + outputDir + "/" + file.getName()+".spins.c");
		}
	}

    private static Expression parseDefine(Specification spec, String name) {
        DefineMapping def = Preprocessor.defines(name);
        if (def == null || def.size() > 0) {
            System.err.println("Could not set '"+ name +"' as label.");
            System.err.println(def == null ? "It does not exist."
                                           : "It has parameters.");
            System.exit(-1);
        }
        InputStream is = new ByteArrayInputStream(def.defineText.getBytes());
        final Promela prom = new Promela(spec, is); // start new parser for condition
        try {
            Expression expr = prom.expr();
            return expr;
        } catch (Exception e) {
            System.err.println("Could not set "+ name +" as label.");
            System.err.println("Failure in parsing: "+ e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
        return null;
    }

	private static void writeLTSminDotFile (final Specification spec,
										final String name, final File outputDir,
										boolean ltsmin_ltl,
										Options opts,
										Map<String, Expression> exports,
										Expression progress) {
		final File dotFile = new File(outputDir, name + ".spins.dot");

		
		LTSminTreeWalker walker = new LTSminTreeWalker(spec, ltsmin_ltl);
		LTSminModel model = walker.createLTSminModel(name, opts,
		                                             exports, progress);

		String out = "digraph {\n";
		for (LTSminTransition t : model.getTransitions()) {
			String s[] = t.getName().split(" X ");
			String trans_s;
			for (String proc : s) {
				String n[] = proc.split("\\(");
				String names = n[0];
				n = n[1].split("-->");
				String from = n[0];
				String to = n[1].substring(0, n[1].length()-1);
				String from_s = names +"_"+ from;
				String to_s = names +"_"+ to;
				
				LTSminTransition tt = (LTSminTransition)t;
				boolean atomic = tt.isAtomic();
				if (s.length == 1) {
					trans_s = "\"" + from_s +"\" -> \""+ to_s +"\"";
					if (atomic) trans_s += "[penwidth=4]";
					out += "\t"+ trans_s +";\n";

					atomic &= !tt.leavesAtomic();
					out += "\t\""+ to_s +"\""+  (atomic ? "[penwidth=4]" : "") +"\n";
				} else {
					trans_s = "\"" + from_s +"\" -> \""+ t.getName() +"\"";
					if (atomic)	trans_s += "[penwidth=4]";
					out += "\t"+ trans_s +";\n";
					
					trans_s = "\"" + t.getName() +"\" -- \""+ to_s +"\"";
					if (atomic)	trans_s += "[penwidth=4]";
					out += "\t"+ trans_s +";\n";

					out += "\t\""+ t.getName() +"\"[shape=box]"+  (atomic ? "[penwidth=4]" : "") +";\n";

					atomic &= !tt.leavesAtomic();
					out += "\t\""+ to_s +"\""+  (atomic ? "[penwidth=4]" : "") +"\n";
				}
			}
		}
		out += "}\n";
		try {
			FileOutputStream fos = new FileOutputStream(dotFile);
			fos.write(out.getBytes());
			fos.flush();
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void writeLTSMinFiles(final Specification spec,
										 final String name, final File outputDir,
										 boolean ltsmin_ltl, Options opts,
										 Map<String, Expression> exports,
	                                     Expression progress) {
		LTSminTreeWalker walker = new LTSminTreeWalker(spec, ltsmin_ltl);
		LTSminModel model = walker.createLTSminModel(name, opts, exports, progress);
		String code = LTSminPrinter.generateCode(model, opts);
		final File javaFile = new File(outputDir, name + ".spins.c");
		try {
			final FileOutputStream fos = new FileOutputStream(javaFile);
            fos.write(code.getBytes());
			fos.flush();
			fos.close();
		} catch (final IOException ex) {
			System.out.println("IOException while writing java files: " + ex.getMessage());
			System.exit(-5);
		}
	}
}