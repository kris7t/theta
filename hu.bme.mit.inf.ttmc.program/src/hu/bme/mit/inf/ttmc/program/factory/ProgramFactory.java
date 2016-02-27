package hu.bme.mit.inf.ttmc.program.factory;

import java.util.List;

import com.google.common.collect.ImmutableList;

import hu.bme.mit.inf.ttmc.constraint.decl.ParamDecl;
import hu.bme.mit.inf.ttmc.constraint.expr.Expr;
import hu.bme.mit.inf.ttmc.constraint.type.BoolType;
import hu.bme.mit.inf.ttmc.constraint.type.Type;
import hu.bme.mit.inf.ttmc.program.decl.ProcDecl;
import hu.bme.mit.inf.ttmc.program.decl.VarDecl;
import hu.bme.mit.inf.ttmc.program.expr.PrimedExpr;
import hu.bme.mit.inf.ttmc.program.expr.ProcCallExpr;
import hu.bme.mit.inf.ttmc.program.expr.ProcRefExpr;
import hu.bme.mit.inf.ttmc.program.expr.VarRefExpr;
import hu.bme.mit.inf.ttmc.program.stmt.AssertStmt;
import hu.bme.mit.inf.ttmc.program.stmt.AssignStmt;
import hu.bme.mit.inf.ttmc.program.stmt.AssumeStmt;
import hu.bme.mit.inf.ttmc.program.stmt.BlockStmt;
import hu.bme.mit.inf.ttmc.program.stmt.DoStmt;
import hu.bme.mit.inf.ttmc.program.stmt.HavocStmt;
import hu.bme.mit.inf.ttmc.program.stmt.IfElseStmt;
import hu.bme.mit.inf.ttmc.program.stmt.IfStmt;
import hu.bme.mit.inf.ttmc.program.stmt.ReturnStmt;
import hu.bme.mit.inf.ttmc.program.stmt.SkipStmt;
import hu.bme.mit.inf.ttmc.program.stmt.Stmt;
import hu.bme.mit.inf.ttmc.program.stmt.WhileStmt;
import hu.bme.mit.inf.ttmc.program.type.ProcType;

public interface ProgramFactory {

	public <T extends Type> VarDecl<T> Var(final String name, final T type);
	
	public <R extends Type> ProcDecl<R> Proc(final String name, List<? extends ParamDecl<? extends Type>> paramDecls, final R returnType);

	////
	
	public <R extends Type> ProcType<R> Proc(final List<? extends Type> paramTypes, final R returnType);
	
	////
	
	public <T extends Type> VarRefExpr<T> Ref(final VarDecl<T> varDecl);
	
	public <R extends Type> ProcRefExpr<R> Ref(final ProcDecl<R> procDecl);
	
	public <R extends Type> ProcCallExpr<R> Call(final Expr<? extends ProcType<? extends R>> proc,
			final List<? extends Expr<?>> params);

	public <T extends Type> PrimedExpr<T> Prime(final Expr<? extends T> op);

	////
	
	public AssumeStmt Assume(final Expr<? extends BoolType> cond);

	public AssertStmt Assert(final Expr<? extends BoolType> cond);
	
	public <T1 extends Type, T2 extends T1> AssignStmt<T1, T2> Assign(final VarDecl<T1> varDecl,
			final Expr<T2> expr);

	public <T extends Type> HavocStmt<T> Havoc(final VarDecl<T> varDecl);

	public BlockStmt Block(final List<? extends Stmt> stmts);
	
	public default BlockStmt Block(Stmt... stmts) {
		return Block(ImmutableList.copyOf(stmts));
	}
	
	public <T extends Type> ReturnStmt<T> Return(final Expr<? extends T> expr);

	public IfStmt If(final Expr<? extends BoolType> cond, final Stmt then);

	public IfElseStmt If(final Expr<? extends BoolType> cond, final Stmt then, final Stmt elze);

	public WhileStmt While(final Expr<? extends BoolType> cond, final Stmt stmt);

	public DoStmt Do(final Stmt stmt, final Expr<? extends BoolType> cond);

	public SkipStmt Skip();

}