/*
 *  Copyright 2017 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hu.bme.mit.theta.solver.z3;

import static hu.bme.mit.theta.core.decl.Decls.Param;
import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Eq;
import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Geq;
import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Gt;
import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Leq;
import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Lt;
import static hu.bme.mit.theta.core.type.anytype.Exprs.Ite;
import static hu.bme.mit.theta.core.type.arraytype.ArrayExprs.Read;
import static hu.bme.mit.theta.core.type.arraytype.ArrayExprs.Write;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.And;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.Bool;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.Exists;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.False;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.Forall;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.Iff;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.Imply;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.Not;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.Or;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;
import static hu.bme.mit.theta.core.type.functype.FuncExprs.App;
import static hu.bme.mit.theta.core.type.inttype.IntExprs.Add;
import static hu.bme.mit.theta.core.type.inttype.IntExprs.Div;
import static hu.bme.mit.theta.core.type.inttype.IntExprs.Int;
import static hu.bme.mit.theta.core.type.inttype.IntExprs.Mul;
import static hu.bme.mit.theta.core.type.inttype.IntExprs.ToRat;
import static hu.bme.mit.theta.core.type.rattype.RatExprs.Add;
import static hu.bme.mit.theta.core.type.rattype.RatExprs.Mul;
import static hu.bme.mit.theta.core.type.rattype.RatExprs.Rat;
import static java.lang.String.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.google.common.collect.ImmutableList;
import com.microsoft.z3.BoolSort;
import com.microsoft.z3.IntSort;
import com.microsoft.z3.RealSort;
import com.microsoft.z3.Sort;

import hu.bme.mit.theta.core.decl.ConstDecl;
import hu.bme.mit.theta.core.decl.Decl;
import hu.bme.mit.theta.core.decl.ParamDecl;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.Type;
import hu.bme.mit.theta.core.type.arraytype.ArrayType;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.core.type.functype.FuncType;
import hu.bme.mit.theta.core.type.inttype.IntType;
import hu.bme.mit.theta.core.type.rattype.RatType;
import hu.bme.mit.theta.core.utils.TypeUtils;

final class Z3TermTransformer {
	private static final String PARAM_NAME_FORMAT = "_p%d";

	private final Z3SymbolTable symbolTable;
	private final Map<String, BiFunction<com.microsoft.z3.Expr, List<Decl<?>>, Expr<?>>> environment;

	public Z3TermTransformer(final Z3SymbolTable symbolTable) {
		this.symbolTable = symbolTable;

		environment = new HashMap<>();
		environment.put("true", this::transformTrue);
		environment.put("false", this::transformFalse);
		environment.put("not", this::transformNot);
		environment.put("or", this::transformOr);
		environment.put("and", this::transformAnd);
		environment.put("=>", this::transformImply);
		environment.put("iff", this::transformIff);
		environment.put("=", this::transformEq);
		environment.put("<=", this::transformLeq);
		environment.put("<", this::transformLt);
		environment.put(">=", this::transformGeq);
		environment.put(">", this::transformGt);
		environment.put("+", this::transformAdd);
		environment.put("*", this::transformMul);
		environment.put("div", this::transformIntDiv);
		environment.put("ite", this::transformIte);
		environment.put("select", this::transformRead);
		environment.put("store", this::transformWrite);
		environment.put("to_real", this::transformToReal);
	}

	public Expr<?> toExpr(final com.microsoft.z3.Expr term) {
		return transform(term, new ArrayList<>());
	}

	////////

	private Expr<?> transform(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		if (term.isIntNum()) {
			return transformIntLit(term);

		} else if (term.isRatNum()) {
			return transformRatLit(term);

		} else if (term.isApp()) {
			return transformApp(term, vars);

		} else if (term.isQuantifier()) {
			final com.microsoft.z3.Quantifier quantifier = (com.microsoft.z3.Quantifier) term;
			return transformQuantifier(quantifier, vars);

		} else if (term.isVar()) {
			return transformVar(term, vars);

		} else {
			return transformUnsupported(term, vars);
		}
	}

	private <T extends Type> List<Expr<T>> transformAll(final com.microsoft.z3.Expr[] terms, final List<Decl<?>> vars,
			final T type) {
		final List<Expr<T>> result = new LinkedList<>();
		for (final com.microsoft.z3.Expr term : terms) {
			result.add(TypeUtils.cast(transform(term, vars), type));
		}
		return result;
	}

	////

	private Expr<?> transformIntLit(final com.microsoft.z3.Expr term) {
		final com.microsoft.z3.IntNum intNum = (com.microsoft.z3.IntNum) term;
		final int value = intNum.getInt();
		return Int(value);
	}

	private Expr<?> transformRatLit(final com.microsoft.z3.Expr term) {
		final com.microsoft.z3.RatNum ratNum = (com.microsoft.z3.RatNum) term;
		final int num = ratNum.getNumerator().getInt();
		final int denom = ratNum.getDenominator().getInt();
		return Rat(num, denom);
	}

	private final Expr<?> transformApp(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.FuncDecl funcDecl = term.getFuncDecl();
		if (symbolTable.definesSymbol(funcDecl)) {
			final ConstDecl<?> constDecl = symbolTable.getConst(funcDecl);
			return transformFuncApp(constDecl.getRef(), term.getArgs(), vars);
		} else {
			final String symbol = funcDecl.getName().toString();
			return environment.getOrDefault(symbol, this::transformUnsupported).apply(term, vars);
		}
	}

	private Expr<?> transformQuantifier(final com.microsoft.z3.Quantifier term, final List<Decl<?>> vars) {
		if (term.isUniversal()) {
			return transformForall(term, vars);

		} else if (term.isExistential()) {
			return transformExists(term, vars);

		} else {
			throw new AssertionError("Unhandled case: " + term.toString());
		}
	}

	private Expr<?> transformVar(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final int index = term.getIndex();
		final Decl<?> decl = vars.get(vars.size() - index - 1);
		return decl.getRef();
	}

	private <P extends Type, R extends Type> Expr<?> transformFuncApp(final Expr<?> expr,
			final com.microsoft.z3.Expr[] argTerms, final List<Decl<?>> vars) {
		Expr<?> result = expr;
		for (final com.microsoft.z3.Expr term : argTerms) {
			@SuppressWarnings("unchecked")
			final Expr<FuncType<P, R>> func = (Expr<FuncType<P, R>>) result;
			final Expr<P> arg = TypeUtils.cast(transform(term, vars), func.getType().getParamType());
			result = App(func, arg);
		}
		return result;
	}

	////////

	private Expr<?> transformTrue(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		return True();
	}

	private Expr<?> transformFalse(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		return False();
	}

	private Expr<?> transformNot(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr opTerm = term.getArgs()[0];
		final Expr<BoolType> op = TypeUtils.cast(transform(opTerm, vars), Bool());
		return Not(op);
	}

	private Expr<?> transformOr(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr[] opTerms = term.getArgs();
		final List<Expr<BoolType>> ops = transformAll(opTerms, vars, Bool());
		return Or(ops);
	}

	private Expr<?> transformAnd(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr[] opTerms = term.getArgs();
		final List<Expr<BoolType>> ops = transformAll(opTerms, vars, Bool());
		return And(ops);
	}

	private Expr<?> transformImply(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr leftOpTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr rightOpTerm = term.getArgs()[1];
		final Expr<BoolType> leftOp = TypeUtils.cast(transform(leftOpTerm, vars), Bool());
		final Expr<BoolType> rightOp = TypeUtils.cast(transform(rightOpTerm, vars), Bool());
		return Imply(leftOp, rightOp);
	}

	private Expr<?> transformIff(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr leftOpTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr rightOpTerm = term.getArgs()[1];
		final Expr<BoolType> leftOp = TypeUtils.cast(transform(leftOpTerm, vars), Bool());
		final Expr<BoolType> rightOp = TypeUtils.cast(transform(rightOpTerm, vars), Bool());
		return Iff(leftOp, rightOp);
	}

	private Expr<?> transformEq(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr leftOpTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr rightOpTerm = term.getArgs()[1];
		final Expr<?> leftOp = transform(leftOpTerm, vars);
		final Expr<?> rightOp = transform(rightOpTerm, vars);
		return Eq(leftOp, rightOp);
	}

	private Expr<?> transformLeq(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr leftOpTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr rightOpTerm = term.getArgs()[1];
		final Expr<?> leftOp = transform(leftOpTerm, vars);
		final Expr<?> rightOp = transform(rightOpTerm, vars);
		return Leq(leftOp, rightOp);
	}

	private Expr<?> transformLt(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr leftOpTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr rightOpTerm = term.getArgs()[1];
		final Expr<?> leftOp = transform(leftOpTerm, vars);
		final Expr<?> rightOp = transform(rightOpTerm, vars);
		return Lt(leftOp, rightOp);
	}

	private Expr<?> transformGeq(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr leftOpTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr rightOpTerm = term.getArgs()[1];
		final Expr<?> leftOp = transform(leftOpTerm, vars);
		final Expr<?> rightOp = transform(rightOpTerm, vars);
		return Geq(leftOp, rightOp);
	}

	private Expr<?> transformGt(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr leftOpTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr rightOpTerm = term.getArgs()[1];
		final Expr<?> leftOp = transform(leftOpTerm, vars);
		final Expr<?> rightOp = transform(rightOpTerm, vars);
		return Gt(leftOp, rightOp);
	}

	private Expr<?> transformAdd(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr[] opTerms = term.getArgs();
		if (term instanceof com.microsoft.z3.IntExpr) {
			final List<Expr<IntType>> ops = transformAll(opTerms, vars, Int());
			return Add(ops);
		} else if (term instanceof com.microsoft.z3.ArithExpr) {
			final List<Expr<RatType>> ops = transformAll(opTerms, vars, Rat());
			return Add(ops);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	private Expr<?> transformMul(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr[] opTerms = term.getArgs();
		if (term instanceof com.microsoft.z3.IntExpr) {
			final List<Expr<IntType>> ops = transformAll(opTerms, vars, Int());
			return Mul(ops);
		} else if (term instanceof com.microsoft.z3.ArithExpr) {
			final List<Expr<RatType>> ops = transformAll(opTerms, vars, Rat());
			return Mul(ops);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	private Expr<?> transformIntDiv(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr leftOpTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr rightOpTerm = term.getArgs()[1];
		final Expr<IntType> leftOp = TypeUtils.cast(transform(leftOpTerm, vars), Int());
		final Expr<IntType> rightOp = TypeUtils.cast(transform(rightOpTerm, vars), Int());
		return Div(leftOp, rightOp);
	}

	private <T extends Type> Expr<?> transformIte(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr condTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr thenTerm = term.getArgs()[1];
		final com.microsoft.z3.Expr elzeTerm = term.getArgs()[2];
		final Expr<BoolType> cond = TypeUtils.cast(transform(condTerm, vars), Bool());
		@SuppressWarnings("unchecked")
		final Expr<T> then = (Expr<T>) transform(thenTerm, vars);
		final Expr<T> elze = TypeUtils.cast(transform(elzeTerm, vars), then.getType());
		return Ite(cond, then, elze);
	}

	private <I extends Type, E extends Type> Expr<?> transformRead(final com.microsoft.z3.Expr term,
			final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr arrayTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr indexTerm = term.getArgs()[1];
		@SuppressWarnings("unchecked")
		final Expr<ArrayType<I, E>> array = (Expr<ArrayType<I, E>>) transform(arrayTerm, vars);
		final Expr<I> index = TypeUtils.cast(transform(indexTerm, vars), array.getType().getIndexType());
		return Read(array, index);
	}

	private <I extends Type, E extends Type> Expr<?> transformWrite(final com.microsoft.z3.Expr term,
			final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr arrayTerm = term.getArgs()[0];
		final com.microsoft.z3.Expr indexTerm = term.getArgs()[1];
		final com.microsoft.z3.Expr elemTerm = term.getArgs()[2];
		@SuppressWarnings("unchecked")
		final Expr<ArrayType<I, E>> array = (Expr<ArrayType<I, E>>) transform(arrayTerm, vars);
		final Expr<I> index = TypeUtils.cast(transform(indexTerm, vars), array.getType().getIndexType());
		final Expr<E> elem = TypeUtils.cast(transform(elemTerm, vars), array.getType().getElemType());
		return Write(array, index, elem);
	}

	private <I extends Type, E extends Type> Expr<?> transformToReal(final com.microsoft.z3.Expr term,
			final List<Decl<?>> vars) {
		final com.microsoft.z3.Expr opTerm = term.getArgs()[0];
		final Expr<IntType> op = TypeUtils.cast(transform(opTerm, vars), Int());
		return ToRat(op);
	}

	////

	private Expr<?> transformForall(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Quantifier quantifier = (com.microsoft.z3.Quantifier) term;
		final com.microsoft.z3.BoolExpr opTerm = quantifier.getBody();
		final com.microsoft.z3.Sort[] sorts = quantifier.getBoundVariableSorts();
		final List<ParamDecl<?>> paramDecls = transformParams(vars, sorts);

		pushParams(vars, paramDecls);
		final Expr<BoolType> op = TypeUtils.cast(transform(opTerm, vars), Bool());
		popParams(vars, paramDecls);

		return Forall(paramDecls, op);
	}

	private Expr<?> transformExists(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		final com.microsoft.z3.Quantifier quantifier = (com.microsoft.z3.Quantifier) term;
		final com.microsoft.z3.BoolExpr opTerm = quantifier.getBody();
		final com.microsoft.z3.Sort[] sorts = quantifier.getBoundVariableSorts();
		final List<ParamDecl<?>> paramDecls = transformParams(vars, sorts);

		pushParams(vars, paramDecls);
		final Expr<BoolType> op = TypeUtils.cast(transform(opTerm, vars), Bool());
		popParams(vars, paramDecls);

		return Exists(paramDecls, op);
	}

	private List<ParamDecl<?>> transformParams(final List<Decl<?>> vars, final com.microsoft.z3.Sort[] sorts) {
		final ImmutableList.Builder<ParamDecl<?>> builder = ImmutableList.builder();
		for (final com.microsoft.z3.Sort sort : sorts) {
			final Type type = transformSort(sort);
			final ParamDecl<?> param = Param(format(PARAM_NAME_FORMAT, vars.size()), type);
			builder.add(param);
		}
		final List<ParamDecl<?>> paramDecls = builder.build();
		return paramDecls;
	}

	private Type transformSort(final Sort sort) {
		if (sort instanceof BoolSort) {
			return Bool();
		} else if (sort instanceof IntSort) {
			return Int();
		} else if (sort instanceof RealSort) {
			return Rat();
		} else {
			throw new AssertionError("Unsupported sort: " + sort);
		}
	}

	private void pushParams(final List<Decl<?>> vars, final List<ParamDecl<?>> paramDecls) {
		vars.addAll(paramDecls);
	}

	private void popParams(final List<Decl<?>> vars, final List<ParamDecl<?>> paramDecls) {
		for (int i = 0; i < paramDecls.size(); i++) {
			vars.remove(vars.size() - 1);
		}
	}

	private Expr<?> transformUnsupported(final com.microsoft.z3.Expr term, final List<Decl<?>> vars) {
		throw new UnsupportedOperationException("Unsupported term: " + term);
	}

}
