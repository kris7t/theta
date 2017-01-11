package hu.bme.mit.theta.analysis.algorithm.impact;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import java.util.function.Function;

import hu.bme.mit.theta.analysis.Action;
import hu.bme.mit.theta.analysis.Precision;
import hu.bme.mit.theta.analysis.State;
import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.algorithm.ARG;
import hu.bme.mit.theta.analysis.algorithm.ArgBuilder;
import hu.bme.mit.theta.analysis.algorithm.ArgNode;
import hu.bme.mit.theta.analysis.algorithm.ArgTrace;
import hu.bme.mit.theta.analysis.algorithm.SafetyChecker;
import hu.bme.mit.theta.analysis.algorithm.SafetyStatus;
import hu.bme.mit.theta.analysis.algorithm.impact.ImpactRefiner.RefinementResult;
import hu.bme.mit.theta.analysis.waitlist.LifoWaitlist;
import hu.bme.mit.theta.analysis.waitlist.Waitlist;

public final class ImpactChecker<S extends State, A extends Action, P extends Precision>
		implements SafetyChecker<S, A, P> {

	private final ArgBuilder<S, A, P> argBuilder;
	private final ImpactRefiner<S, A> refiner;
	private final Function<? super S, ?> partitioning;

	private ImpactChecker(final ArgBuilder<S, A, P> argBuilder, final ImpactRefiner<S, A> refiner,
			final Function<? super S, ?> partitioning) {
		this.argBuilder = checkNotNull(argBuilder);
		this.refiner = checkNotNull(refiner);
		this.partitioning = checkNotNull(partitioning);
	}

	public static <S extends State, A extends Action, P extends Precision> ImpactChecker<S, A, P> create(
			final ArgBuilder<S, A, P> argBuilder, final ImpactRefiner<S, A> refiner,
			final Function<? super S, ?> partitioning) {
		return new ImpactChecker<>(argBuilder, refiner, partitioning);
	}

	////

	@Override
	public SafetyStatus<S, A> check(final P precision) {
		return new CheckMethod<>(argBuilder, refiner, partitioning, precision).run();
	}

	////

	private static final class CheckMethod<S extends State, A extends Action, P extends Precision> {
		private final ArgBuilder<S, A, P> argBuilder;
		private final ImpactRefiner<S, A> refiner;
		private final P precision;

		private final ARG<S, A> arg;

		private final ReachedSet<S, A> reachedSet;

		private CheckMethod(final ArgBuilder<S, A, P> argBuilder, final ImpactRefiner<S, A> refiner,
				final Function<? super S, ?> partitioning, final P precision) {
			this.argBuilder = argBuilder;
			this.refiner = refiner;
			this.precision = checkNotNull(precision);
			arg = argBuilder.createArg();
			reachedSet = ImpactReachedSet.create(partitioning);
		}

		private SafetyStatus<S, A> run() {
			final Optional<ArgNode<S, A>> unsafeNode = unwind();

			if (unsafeNode.isPresent()) {
				return SafetyStatus.unsafe(ArgTrace.to(unsafeNode.get()).toTrace(), arg);
			} else {
				return SafetyStatus.safe(arg);
			}
		}

		////

		private Optional<ArgNode<S, A>> dfs(final ArgNode<S, A> node) {
			final Waitlist<ArgNode<S, A>> waitlist = LifoWaitlist.create();
			waitlist.add(node);

			while (!waitlist.isEmpty()) {
				final ArgNode<S, A> v = waitlist.remove();
				close(v);
				if (!v.isExcluded()) {
					if (v.isTarget()) {
						refine(v);
						if (v.isExcluded()) {
							closeProperAncestorsOf(v);
						} else {
							return Optional.of(v);
						}
					} else {
						expand(v);
						reachedSet.addAll(v.getSuccNodes());
						waitlist.addAll(v.getSuccNodes());
					}
				}
			}

			return Optional.empty();
		}

		private Optional<ArgNode<S, A>> unwind() {
			argBuilder.init(arg, precision);
			reachedSet.addAll(arg.getInitNodes());

			while (true) {
				final Optional<ArgNode<S, A>> anyIncompleteNode = arg.getIncompleteNodes().findAny();

				if (anyIncompleteNode.isPresent()) {
					final ArgNode<S, A> v = anyIncompleteNode.get();

					assert v.isLeaf();

					closeProperAncestorsOf(v);

					final Optional<ArgNode<S, A>> unsafeDescendant = dfs(v);
					if (unsafeDescendant.isPresent()) {
						return unsafeDescendant;
					}
				} else {
					return Optional.empty();
				}
			}
		}

		////

		private void close(final ArgNode<S, A> node) {
			reachedSet.tryToCover(node);
		}

		private void closeProperAncestorsOf(final ArgNode<S, A> v) {
			v.properAncestors().forEach(w -> close(w));
		}

		private void expand(final ArgNode<S, A> v) {
			argBuilder.expand(v, precision);
		}

		private void refine(final ArgNode<S, A> v) {
			final ArgTrace<S, A> argTrace = ArgTrace.to(v);

			final Trace<S, A> trace = argTrace.toTrace();
			final RefinementResult<S, A> refinementResult = refiner.refine(trace);

			if (refinementResult.isSuccesful()) {
				final Trace<S, A> refinedTrace = refinementResult.asSuccesful().getTrace();
				for (int i = 0; i < argTrace.nodes().size(); i++) {
					final ArgNode<S, A> vi = argTrace.node(i);
					vi.clearCoveredNodes();
					vi.setState(refinedTrace.getState(i));
				}
			}
		}

	}
}
