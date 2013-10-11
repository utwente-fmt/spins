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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import spins.options.BooleanOption;
import spins.options.MultiStringOption;
import spins.options.OptionParser;
import spins.options.StringOption;
import spins.promela.compiler.Preprocessor;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.Specification;
import spins.promela.compiler.ltsmin.LTSminPrinter;
import spins.promela.compiler.ltsmin.LTSminTreeWalker;
import spins.promela.compiler.ltsmin.model.LTSminModel;
import spins.promela.compiler.ltsmin.model.LTSminTransition;
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
	                                     final String name,
		                                 final boolean useStateMerging,
		                                 final boolean verbose) {
		try {
			Preprocessor.setFilename(promFile.getName());
			String path = promFile.getAbsoluteFile().getParent();
			Preprocessor.setDirname(path);

			System.out.print("Start parsing " + promFile.getName() + "...");
			final Promela prom = new Promela(new FileInputStream(promFile));
			final Specification spec = prom.spec(name);
			System.out.println("done");
			System.out.println("");

			System.out.println("Optimizing graphs...");
			final GraphOptimizer[] optimizers = new GraphOptimizer[] {
					useStateMerging ? new StateMerging() : null, new RemoveUselessActions(),
					new RemoveUselessGotos(), new RenumberAll(),
			};
			for (final GraphOptimizer opt : optimizers) {
				if (opt == null) continue;
				int reduction = 0;
				for (final Proctype proc : spec.getProcs()) {
					if (verbose) {
						System.out.println("Initial graph for process " + proc + ":");
						System.out.println(proc.getAutomaton());
					}
					reduction += opt.optimize(proc.getAutomaton());
					if (verbose) {
						System.out.println("After " + opt.getClass().getSimpleName() + ":");
						System.out.println(proc.getAutomaton());
					}
				}
				System.out.println("   "+ opt.getClass().getSimpleName() +" changed "+ reduction +" states/transitions.");
			}

			final Proctype never = spec.getNever();
			if (never != null) {
				if (verbose) {
					System.out.println("Initial graph for never claim:");
					System.out.println(never.getAutomaton());
				}
				for (final GraphOptimizer opt : optimizers) {
					if (opt == null) continue;
					int reduction = opt.optimize(never.getAutomaton());
					System.out.println("   "+ opt.getClass().getSimpleName() +" reduces "+ reduction +" states");
					if (verbose) {
						System.out.println("After " + opt.getClass().getSimpleName() + ":");
						System.out.println(never.getAutomaton());
					}
				}
				if (verbose) System.out.println(never.getAutomaton());
			}
			System.out.println("Optimization done");
			System.out.println("");
			return spec;
		} catch (final FileNotFoundException ex) {
			System.out.println("Promela file " + promFile.getName() + " could not be found.");
		} catch (final ParseException ex) {
			System.out.println("Parse exception in file " + Preprocessor.getFileName() + ": "
								+ ex.getMessage());
		}
		return null;
	}

	public static void main(final String[] args) {
		final String  defaultname = "Pan";
		final String  shortd  = 
			"SpinS Promela Compiler - version " + Version.VERSION + " (" + Version.DATE + ")\n" +
			"(C) University of Twente, Formal Methods and Tools group";
		final String  longd   = 
			"SpinS Promela Compiler: this compiler converts a Promela source file\n" +
			"to a Java model that can be checked by the SpinS Model Checker." ;

		final OptionParser parser = 
			new OptionParser("java spins.Compiler", shortd, longd, true);

		final StringOption modelname = new StringOption('n',
			"sets the name of the model \n" +
			"(default: " + defaultname + ")", false);
		parser.addOption(modelname);

		final StringOption define = new StringOption('D',
			"sets preprocessor macro define value", true);
		parser.addOption(define);

		final BooleanOption dot = new BooleanOption('d',
			"only write dot output (ltsmin/spins) \n");
		parser.addOption(dot);

		final BooleanOption ltsmin_ltl = new BooleanOption('L',
			"sets output to LTSmin LTL semantics \n");
		parser.addOption(ltsmin_ltl);

        final BooleanOption textbook_ltl = new BooleanOption('t',
            "sets output to textbook LTL semantics \n");
        parser.addOption(textbook_ltl);
		
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

		parser.parse(args);
		final List<String> files = parser.getFiles();

		if (files.size() != 1) {
			System.out.println("Please specify one file that is to be compiled!");
			parser.printUsage();
		}

		final File file = new File(files.get(0));
		if (!file.exists() || !file.isFile()) {
			System.out.println("File " + file.getName() + " does not exist or is not a valid file!");
			parser.printUsage();
		}

        if (textbook_ltl.isSet()) {
            System.err.println("Textbook LTL semantics not yet implemented.");
            System.exit(-1);
        }

		String name = null;
		if (modelname.isSet()) {
			name = modelname.getValue();
		} else {
			name = defaultname;
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

		final Specification spec = 
			Compile.compile(file, name, !optimalizations.isSet("3"), verbose.isSet());

		if (spec == null) {
			System.exit(-4);
		}

		File outputDir = new File(System.getProperty("user.dir"));
		if (dot.isSet()) {
			Compile.writeLTSminDotFile(spec, file.getName(), outputDir, verbose.isSet(), ltsmin_ltl.isSet());
			System.out.println("Written DOT file to " + outputDir + "/" + file.getName()+".spins.dot");
		} else {
			Compile.writeLTSMinFiles(spec, file.getName(), outputDir, verbose.isSet(), ltsmin_ltl.isSet());
			System.out.println("Written C model to " + outputDir + "/" + file.getName()+".spins.c");
		}
	}

	private static void writeLTSminDotFile (final Specification spec,
										final String name, final File outputDir,
										boolean verbose, boolean ltsmin_ltl) {
		final File dotFile = new File(outputDir, name + ".spins.dot");

		LTSminTreeWalker walker = new LTSminTreeWalker(spec, ltsmin_ltl);
		LTSminModel model = walker.createLTSminModel(name, verbose);
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
										 boolean verbose, boolean ltsmin_ltl) {
		final File javaFile = new File(outputDir, name + ".spins.c");
		try {
			final FileOutputStream fos = new FileOutputStream(javaFile);
			LTSminTreeWalker walker = new LTSminTreeWalker(spec, ltsmin_ltl);
			LTSminModel model = walker.createLTSminModel(name, verbose);
			String code = LTSminPrinter.generateCode(model);
            fos.write(code.getBytes());
			fos.flush();
			fos.close();
		} catch (final IOException ex) {
			System.out.println("IOException while writing java files: " + ex.getMessage());
			System.exit(-5);
		}
	}
}