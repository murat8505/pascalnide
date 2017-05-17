package com.js.interpreter.ast.runtime_value;

import com.duy.pascal.backend.exceptions.ParsingException;
import com.duy.pascal.backend.linenumber.LineInfo;
import com.duy.pascal.backend.pascaltypes.RuntimeType;
import com.js.interpreter.ast.expressioncontext.CompileTimeContext;
import com.js.interpreter.ast.expressioncontext.ExpressionContext;
import com.js.interpreter.runtime.VariableContext;
import com.js.interpreter.runtime.codeunit.RuntimeExecutable;
import com.js.interpreter.runtime.exception.RuntimePascalException;

public interface RuntimeValue {
    RuntimeValue[] getOutputFormat();

    /**
     * used for output to console
     *
     * @param formatInfo formatInfo[0] : number column for display in the screen
     *                   formatInfo[1] :  length of floating point if this variable is double
     */
    void setOutputFormat(RuntimeValue[] formatInfo);

    /**
     * @return value of variable or function
     */
    Object getValue(VariableContext f, RuntimeExecutable<?> main)
            throws RuntimePascalException;

    /**
     * get type of variable or return type of function
     */
    RuntimeType getType(ExpressionContext f)
            throws ParsingException;

    /**
     * @return line of code
     */
    LineInfo getLineNumber();

    /*
     * returns null if not a compile-time constant.
     */
    Object compileTimeValue(CompileTimeContext context)
            throws ParsingException;

    RuntimeValue compileTimeExpressionFold(CompileTimeContext context) throws ParsingException;

    /**
     * reference of variable, set and get value
     */
    AssignableValue asAssignableValue(ExpressionContext f);
}