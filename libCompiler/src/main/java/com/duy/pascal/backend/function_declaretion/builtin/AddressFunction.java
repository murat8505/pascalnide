/*
 *  Copyright (c) 2017 Tran Le Duy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duy.pascal.backend.function_declaretion.builtin;


import com.duy.pascal.backend.exceptions.ParsingException;
import com.duy.pascal.backend.exceptions.operator.ConstantCalculationException;
import com.duy.pascal.backend.linenumber.LineInfo;
import com.duy.pascal.backend.pascaltypes.ArgumentType;
import com.duy.pascal.backend.pascaltypes.BasicType;
import com.duy.pascal.backend.pascaltypes.DeclaredType;
import com.duy.pascal.backend.pascaltypes.PointerType;
import com.duy.pascal.backend.pascaltypes.RuntimeType;
import com.duy.pascal.backend.runtime.VariableContext;
import com.duy.pascal.backend.runtime.exception.RuntimePascalException;
import com.duy.pascal.backend.runtime.operators.pointer.DerefEval;
import com.duy.pascal.backend.runtime.references.Reference;
import com.duy.pascal.backend.runtime.value.ConstantAccess;
import com.duy.pascal.backend.runtime.value.FunctionCall;
import com.duy.pascal.backend.runtime.value.RuntimeValue;
import com.js.interpreter.codeunit.RuntimeExecutableCodeUnit;
import com.js.interpreter.expressioncontext.CompileTimeContext;
import com.js.interpreter.expressioncontext.ExpressionContext;
import com.js.interpreter.instructions.Executable;

public class AddressFunction implements IMethodDeclaration {

    private ArgumentType[] argumentTypes = {new RuntimeType(new PointerType(BasicType.create(Object.class)), true)};
    private PointerType pointerType;

    @Override
    public String getName() {
        return "addr";
    }

    @Override
    public FunctionCall generateCall(LineInfo line, RuntimeValue[] arguments,
                                                      ExpressionContext f) throws ParsingException {
        RuntimeValue pointer = arguments[0];
        this.pointerType = (PointerType) pointer.getType(f).declType;
        return new AddressFunctionCall(pointer, line);
    }

    @Override
    public FunctionCall generatePerfectFitCall(LineInfo line, RuntimeValue[] values, ExpressionContext f) throws ParsingException {
        return generateCall(line, values, f);
    }

    @Override
    public ArgumentType[] argumentTypes() {
        return argumentTypes;
    }

    @Override
    public DeclaredType returnType() {
        return pointerType;
    }

    @Override
    public String description() {
        return null;
    }

    private class AddressFunctionCall extends FunctionCall {

        private RuntimeValue pointer;
        private LineInfo line;

        public AddressFunctionCall(RuntimeValue pointer, LineInfo line) {
            this.pointer = pointer;
            this.line = line;
        }

        @Override
        public Object getValueImpl(VariableContext f, RuntimeExecutableCodeUnit<?> main) throws RuntimePascalException {
            Reference ref = (Reference) pointer.getValue(f, main);
            return ref.get();
        }

        @Override
        public RuntimeType getType(ExpressionContext f) throws ParsingException {
            RuntimeType pointertype = pointer.getType(f);
            return new RuntimeType(((PointerType) pointertype.declType).pointedToType, true);
        }

        @Override
        public LineInfo getLineNumber() {
            return line;
        }

        @Override
        public Executable compileTimeConstantTransform(CompileTimeContext c) throws ParsingException {
            return null;
        }

        @Override
        public Object compileTimeValue(CompileTimeContext context) throws ParsingException {
            Reference<?> ref = (Reference<?>) pointer.compileTimeValue(context);
            if (ref != null) {
                try {
                    return ref.get();
                } catch (RuntimePascalException e) {
                    throw new ConstantCalculationException(e);
                }
            }
            return null;
        }

        @Override
        public RuntimeValue compileTimeExpressionFold(CompileTimeContext context) throws ParsingException {
            Object val = this.compileTimeValue(context);
            if (val != null) {
                return new ConstantAccess(val, line);
            } else {
                return new DerefEval(pointer.compileTimeExpressionFold(context), line);
            }
        }


        @Override
        protected String getFunctionName() {
            return "addr";
        }
    }
}
