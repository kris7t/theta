package hu.bme.mit.theta.analysis.tcfa;

import static hu.bme.mit.theta.core.decl.impl.Decls.Var;
import static hu.bme.mit.theta.core.expr.impl.Exprs.True;
import static hu.bme.mit.theta.core.type.impl.Types.Int;

import java.util.Collections;

import org.junit.Test;

import hu.bme.mit.theta.analysis.Analysis;
import hu.bme.mit.theta.analysis.algorithm.ARG;
import hu.bme.mit.theta.analysis.algorithm.cegar.Abstractor;
import hu.bme.mit.theta.analysis.algorithm.cegar.WaitlistBasedAbstractor;
import hu.bme.mit.theta.analysis.expl.ExplAnalysis;
import hu.bme.mit.theta.analysis.expl.ExplPrecision;
import hu.bme.mit.theta.analysis.expl.ExplState;
import hu.bme.mit.theta.analysis.impl.FixedPrecisionAnalysis;
import hu.bme.mit.theta.analysis.impl.NullPrecision;
import hu.bme.mit.theta.analysis.loc.LocAnalysis;
import hu.bme.mit.theta.analysis.loc.LocPrecision;
import hu.bme.mit.theta.analysis.loc.LocState;
import hu.bme.mit.theta.analysis.utils.ArgVisualizer;
import hu.bme.mit.theta.common.visualization.GraphvizWriter;
import hu.bme.mit.theta.common.waitlist.FifoWaitlist;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.type.IntType;
import hu.bme.mit.theta.formalism.tcfa.TCFA;
import hu.bme.mit.theta.formalism.tcfa.TcfaEdge;
import hu.bme.mit.theta.formalism.tcfa.TcfaLoc;
import hu.bme.mit.theta.solver.Solver;
import hu.bme.mit.theta.solver.z3.Z3SolverFactory;

public class TcfaExplTest {

	@Test
	public void test() {
		final int n = 2;
		final VarDecl<IntType> vlock = Var("lock", Int());
		final TCFA fischer = TcfaTestHelper.fischer(n, vlock);

		final Solver solver = Z3SolverFactory.getInstace().createSolver();

		final TcfaLts lts = TcfaLts.create(fischer);

		final Analysis<LocState<ExplState, TcfaLoc, TcfaEdge>, TcfaAction, NullPrecision> analysis = FixedPrecisionAnalysis
				.create(LocAnalysis.create(fischer.getInitLoc(), ExplAnalysis.create(solver, True())),
						LocPrecision.constant(ExplPrecision.create(Collections.singleton(vlock))));

		final Abstractor<LocState<ExplState, TcfaLoc, TcfaEdge>, TcfaAction, NullPrecision> abstractor = WaitlistBasedAbstractor
				.create(lts, analysis, s -> false, FifoWaitlist.supplier());

		final ARG<LocState<ExplState, TcfaLoc, TcfaEdge>, TcfaAction> arg = abstractor.createArg();

		abstractor.check(arg, NullPrecision.getInstance());

		System.out.println(new GraphvizWriter().writeString(ArgVisualizer.visualize(arg)));
	}

}
