package hu.bme.mit.inf.ttmc.constraint.z3.trasform;

import com.microsoft.z3.Context;

import hu.bme.mit.inf.ttmc.constraint.decl.Decl;
import hu.bme.mit.inf.ttmc.constraint.expr.Expr;
import hu.bme.mit.inf.ttmc.constraint.type.Type;

public class Z3TransformationManager {

	final Z3TypeTransformer typeTransformer;
	final Z3DeclTransformer declTransformer;
	final Z3ExprTransformer exprTransformer;

	public Z3TransformationManager(final Z3SymbolTable symbolTable, final Context context) {
		this.typeTransformer = new Z3TypeTransformer(this, context);
		this.declTransformer = new Z3DeclTransformer(this, symbolTable, context);
		this.exprTransformer = new Z3ExprTransformer(this, context);
	}

	public com.microsoft.z3.Sort toSort(final Type type) {
		return typeTransformer.toSort(type);
	}

	public com.microsoft.z3.FuncDecl toSymbol(final Decl<?, ?> decl) {
		return declTransformer.toSymbol(decl);
	}

	public com.microsoft.z3.Expr toTerm(final Expr<?> expr) {
		return exprTransformer.toTerm(expr);
	}

}
