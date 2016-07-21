package hu.bme.mit.inf.ttmc.analysis.composite;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;

import hu.bme.mit.inf.ttmc.analysis.Action;
import hu.bme.mit.inf.ttmc.analysis.Precision;
import hu.bme.mit.inf.ttmc.analysis.State;
import hu.bme.mit.inf.ttmc.analysis.TransferFunction;

public class CompositeTransferFunction<S1 extends State, S2 extends State, A extends Action, P1 extends Precision, P2 extends Precision>
		implements TransferFunction<CompositeState<S1, S2>, A, CompositePrecision<P1, P2>> {

	private final TransferFunction<S1, A, P1> transferFunction1;
	private final TransferFunction<S2, A, P2> transferFunction2;
	private final StrengtheningOperator<S1, S2, P1, P2> strenghteningOperator;

	public CompositeTransferFunction(final TransferFunction<S1, A, P1> transferFunction1,
			final TransferFunction<S2, A, P2> transferFunction2,
			final StrengtheningOperator<S1, S2, P1, P2> strenghteningOperator) {
		this.transferFunction1 = checkNotNull(transferFunction1);
		this.transferFunction2 = checkNotNull(transferFunction2);
		this.strenghteningOperator = checkNotNull(strenghteningOperator);
	}

	public CompositeTransferFunction(final TransferFunction<S1, A, P1> transferFunction1,
			final TransferFunction<S2, A, P2> transferFunction2) {
		this(transferFunction1, transferFunction2, (states, precision) -> states);
	}

	@Override
	public Collection<? extends CompositeState<S1, S2>> getSuccStates(final CompositeState<S1, S2> state,
			final A action, final CompositePrecision<P1, P2> precision) {
		checkNotNull(state);
		checkNotNull(action);
		checkNotNull(precision);

		final Collection<? extends S1> succStates1 = transferFunction1.getSuccStates(state._1(), action,
				precision._1());
		final Collection<? extends S2> succStates2 = transferFunction2.getSuccStates(state._2(), action,
				precision._2());
		final Collection<CompositeState<S1, S2>> compositeIniStates = CompositeState.product(succStates1, succStates2);
		return strenghteningOperator.strengthen(compositeIniStates, precision);
	}

}