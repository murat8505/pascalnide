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

package com.duy.pascal.ui.code;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;

import com.duy.pascal.interperter.exceptions.parsing.ParsingException;
import com.duy.pascal.interperter.exceptions.parsing.UnrecognizedTokenException;
import com.duy.pascal.interperter.exceptions.parsing.convert.UnConvertibleTypeException;
import com.duy.pascal.interperter.exceptions.parsing.define.BadFunctionCallException;
import com.duy.pascal.interperter.exceptions.parsing.define.DuplicateIdentifierException;
import com.duy.pascal.interperter.exceptions.parsing.define.MainProgramNotFoundException;
import com.duy.pascal.interperter.exceptions.parsing.define.MultipleDefaultValuesException;
import com.duy.pascal.interperter.exceptions.parsing.define.MultipleDefinitionsMainException;
import com.duy.pascal.interperter.exceptions.parsing.define.OverridingFunctionBodyException;
import com.duy.pascal.interperter.exceptions.parsing.define.TypeIdentifierExpectException;
import com.duy.pascal.interperter.exceptions.parsing.define.UnknownIdentifierException;
import com.duy.pascal.interperter.exceptions.parsing.define.VariableIdentifierExpectException;
import com.duy.pascal.interperter.exceptions.parsing.grouping.GroupingException;
import com.duy.pascal.interperter.exceptions.parsing.grouping.StrayCharacterException;
import com.duy.pascal.interperter.exceptions.parsing.index.LowerGreaterUpperBoundException;
import com.duy.pascal.interperter.exceptions.parsing.index.NonArrayIndexed;
import com.duy.pascal.interperter.exceptions.parsing.index.NonIntegerIndexException;
import com.duy.pascal.interperter.exceptions.parsing.io.LibraryNotFoundException;
import com.duy.pascal.interperter.exceptions.parsing.missing.MissingCommaTokenException;
import com.duy.pascal.interperter.exceptions.parsing.missing.MissingSemicolonTokenException;
import com.duy.pascal.interperter.exceptions.parsing.missing.MissingTokenException;
import com.duy.pascal.interperter.exceptions.parsing.operator.BadOperationTypeException;
import com.duy.pascal.interperter.exceptions.parsing.operator.ConstantCalculationException;
import com.duy.pascal.interperter.exceptions.parsing.operator.DivisionByZeroException;
import com.duy.pascal.interperter.exceptions.parsing.syntax.ExpectedTokenException;
import com.duy.pascal.interperter.exceptions.parsing.syntax.NotAStatementException;
import com.duy.pascal.interperter.exceptions.parsing.syntax.WrongIfElseStatement;
import com.duy.pascal.interperter.exceptions.parsing.value.ChangeValueConstantException;
import com.duy.pascal.interperter.exceptions.parsing.value.NonConstantExpressionException;
import com.duy.pascal.interperter.exceptions.parsing.value.NonIntegerException;
import com.duy.pascal.interperter.exceptions.parsing.value.UnAssignableTypeException;
import com.duy.pascal.interperter.exceptions.runtime.InvalidNumericFormatException;
import com.duy.pascal.interperter.exceptions.runtime.MethodCallException;
import com.duy.pascal.interperter.exceptions.runtime.PascalArithmeticException;
import com.duy.pascal.interperter.exceptions.runtime.RuntimePascalException;
import com.duy.pascal.interperter.exceptions.runtime.StackOverflowException;
import com.duy.pascal.interperter.libraries.exceptions.CanNotReadVariableException;
import com.duy.pascal.interperter.libraries.file.exceptions.DiskReadErrorException;
import com.duy.pascal.interperter.libraries.file.exceptions.FileException;
import com.duy.pascal.interperter.libraries.file.exceptions.FileNotAssignException;
import com.duy.pascal.interperter.libraries.file.exceptions.FileNotOpenException;
import com.duy.pascal.interperter.libraries.file.exceptions.FileNotOpenForInputException;
import com.duy.pascal.interperter.tokens.Token;
import com.duy.pascal.interperter.utils.ArrayUtil;
import com.duy.pascal.ui.R;
import com.duy.pascal.ui.autocomplete.autofix.Patterns;

import java.util.List;
import java.util.regex.Matcher;

/**
 * Created by Duy on 11-Mar-17.
 */


public class ExceptionManager {
    public static final String TAG = ExceptionManager.class.getSimpleName();
    private Context mContext;

    public ExceptionManager(Context context) {
        this.mContext = context;
    }

    public static Spannable highlight(Context context, Spannable spannable) {
        Matcher matcher = Patterns.REPLACE_HIGHLIGHT.matcher(spannable);
        TypedValue typedValue = new TypedValue();
        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[]{R.attr.colorAccent});
        int color = a.getColor(0, 0);
        a.recycle();
        while (matcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(color), matcher.start(),
                    matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(),
                    matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    public static Spannable highlight(Context context, String spannable) {
        return highlight(context, new SpannableString(spannable));
    }

    public static Spanned getMessageResource(Throwable e, Context mContext, int resourceID, Object... arg) {
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        if (e instanceof ParsingException) {
            stringBuilder.append(String.valueOf(((ParsingException) e).getLineInfo()));
            stringBuilder.append("\n").append("\n");
            stringBuilder.append(mContext.getString(resourceID, arg));
            return highlight(mContext, stringBuilder);
        } else if (e instanceof RuntimePascalException) {
            stringBuilder.append(String.valueOf(((RuntimePascalException) e).getLineNumber()));
            stringBuilder.append("\n").append("\n");
            stringBuilder.append(mContext.getString(resourceID, arg));
            return highlight(mContext, stringBuilder);
        }
        return new SpannableString(e.getLocalizedMessage());
    }

    public Spanned getMessage(Throwable e) {
        if (e == null) {
            return new SpannableString("null");
        }
        try {

            if (e instanceof ExpectedTokenException) {
                return getExpectedTokenException((ExpectedTokenException) e);
            }
            if (e instanceof StackOverflowException) {
                return getMessageResource(e, R.string.StackOverflowException);
            }

            if (e instanceof MissingSemicolonTokenException) {
                return getMessageResource(e, R.string.MissingSemicolonTokenException,
                        ((MissingSemicolonTokenException) e).getLineInfo().getLine());
            } else if (e instanceof MissingCommaTokenException) {
                return getMessageResource(e, R.string.MissingCommaTokenException,
                        ((MissingCommaTokenException) e).getLineInfo().getLine());
            } else if (e instanceof MissingTokenException) {
                return getMessageResource(e, R.string.MissingTokenException,
                        ((MissingTokenException) e).getMissingToken());
            }

            if (e instanceof StrayCharacterException)
                return getMessageResource(e, R.string.StrayCharacterException,
                        ((StrayCharacterException) e).getCharCode());

            if (e instanceof UnknownIdentifierException) {
                return getMessageResource(e, R.string.NoSuchFunctionOrVariableException, ((UnknownIdentifierException) e).getName());
            }
            if (e instanceof VariableIdentifierExpectException) {
                return getMessageResource(e, R.string.VariableIdentifierExpectException, ((VariableIdentifierExpectException) e).getName().getOriginName());
            }
            if (e instanceof BadFunctionCallException) return getBadFunctionCallException(e);

            if (e instanceof MultipleDefinitionsMainException)
                return new SpannableString(mContext.getString(R.string.multi_define_main));

            if (e instanceof GroupingException) {
                if (((GroupingException) e).getExceptionTypes().equals(GroupingException.Type.EXTRA_END)) {
                    Token openToken = ((GroupingException) e).getOpenToken();
                    return getMessageResource(e, R.string.unbalance_end,
                            openToken.toString(),
                            openToken.getLineNumber().getLine(),
                            openToken.getLineNumber().getColumn(),
                            ((GroupingException) e).getCloseToken().toString(),
                            ((GroupingException) e).getLineInfo().getLine(),
                            ((GroupingException) e).getLineInfo().getColumn());
                }
                return getEnumeratedGroupingException((GroupingException) e);
            }

            if (e instanceof UnrecognizedTokenException)
                return getUnrecognizedTokenException((UnrecognizedTokenException) e);

            if (e instanceof MainProgramNotFoundException) {
                return new SpannableString(mContext.getString(R.string.main_program_not_define));
            }
            if (e instanceof BadOperationTypeException) {
                return getBadOperationTypeException((BadOperationTypeException) e);
            }
            if (e instanceof FileException) return getFileException((FileException) e);
            if (e instanceof MethodCallException) return getPluginCallException(e);
            if (e instanceof NonIntegerIndexException) return getNonIntegerIndexException(e);
            if (e instanceof NonIntegerException) return getNonIntegerException(e);
            if (e instanceof ConstantCalculationException) {
                return getConstantCalculationException(e);
            }

            if (e instanceof ChangeValueConstantException) {
                String name = ((ChangeValueConstantException) e).getConst().getName().getOriginName();
                return getMessageResource(e, R.string.ChangeValueConstantException2, name);
            }

            if (e instanceof UnConvertibleTypeException) {
                return new SpannableString(((UnConvertibleTypeException) e).getMessage(mContext));
            }
            if (e instanceof LibraryNotFoundException) {
                return getMessageResource(e, R.string.LibraryNotFoundException,
                        ((LibraryNotFoundException) e).getName());
            }
            if (e instanceof MultipleDefaultValuesException) {
                return getMessageResource(e, R.string.MultipleDefaultValuesException);
            }
            if (e instanceof NonArrayIndexed) {
                return getMessageResource(e, R.string.NonArrayIndexed, ((NonArrayIndexed) e).getType().toString());
            }
            if (e instanceof NonConstantExpressionException) {
                return getMessageResource(e, R.string.NonConstantExpressionException);
            }
            if (e instanceof NotAStatementException) {
                return getMessageResource(e, R.string.NotAStatementException,
                        ((NotAStatementException) e).getRuntimeValue().toString());
            }
            if (e instanceof DuplicateIdentifierException) {
                DuplicateIdentifierException exception = (DuplicateIdentifierException) e;
                return getMessageResource(e, R.string.SameNameException, exception.getType(),
                        exception.getName(), exception.getPreType(), exception.getPreLine());
            }
            if (e instanceof UnAssignableTypeException) {
                return getMessageResource(e, R.string.UnAssignableTypeException,
                        ((UnAssignableTypeException) e).getRuntimeValue().toString());
            }
            if (e instanceof TypeIdentifierExpectException) {
                return getMessageResource(e, R.string.UnrecognizedTypeException,
                        ((TypeIdentifierExpectException) e).getMissingType());
            }
            if (e instanceof InvalidNumericFormatException) {
                return getMessageResource(e, R.string.InvalidNumericFormatException);
            }
            if (e instanceof PascalArithmeticException) {
                return getMessageResource(e, R.string.PascalArithmeticException, ((PascalArithmeticException) e).error.getLocalizedMessage());
            }
            if (e instanceof CanNotReadVariableException) {
                return getMessageResource(e, R.string.CanNotReadVariableException);
            }
            if (e instanceof WrongIfElseStatement) {
                return getMessageResource(e, R.string.WrongIfElseStatement);
            }
            if (e instanceof LowerGreaterUpperBoundException) {
                return getMessageResource(e, R.string.SubRangeException,
                        ((LowerGreaterUpperBoundException) e).getHigh(), ((LowerGreaterUpperBoundException) e).getLow());
            }
            if (e instanceof OverridingFunctionBodyException) {
                if (!((OverridingFunctionBodyException) e).isMethod()) {
                    return getMessageResource(e, R.string.OverridingFunctionException,
                            ((OverridingFunctionBodyException) e).getFunctionDeclaration().getName(),
                            ((OverridingFunctionBodyException) e).getFunctionDeclaration().getLineNumber());
                } else {
                    return getMessageResource(e, R.string.OverridingFunctionException);
                }
            }
            if (e instanceof ParsingException) {
                return new SpannableString(((ParsingException) e).getLineInfo() + "\n\n" + e.getLocalizedMessage());
            }

            if (e instanceof DivisionByZeroException) {
                return getMessageResource(e, R.string.DivisionByZeroException);
            }
            return new SpannableString(e.getLocalizedMessage());
        } catch (Exception err) {
            err.printStackTrace();
            return new SpannableString(err.toString());
        }
    }

    public  Spanned getMessageResource(Throwable e, int resourceID, Object... arg) {
        return getMessageResource(e, mContext, resourceID, arg);
    }

    private Spanned getConstantCalculationException(Throwable e) {
        ConstantCalculationException exception = (ConstantCalculationException) e;
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        stringBuilder.append(String.valueOf(exception.getLineInfo()));
        stringBuilder.append("\n").append("\n");
        String format = String.format(mContext.getString(R.string.ConstantCalculationException),
                exception.getException().getLocalizedMessage());
        stringBuilder.append(format);
        return stringBuilder;

    }

    private Spanned getNonIntegerException(Throwable e) {
        NonIntegerIndexException exception = (NonIntegerIndexException) e;
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        stringBuilder.append(String.valueOf(exception.getLineInfo()));
        stringBuilder.append("\n").append("\n");
        String format = String.format(
                mContext.getString(R.string.NonIntegerException),
                exception.getValue().toString());
        stringBuilder.append(format);
        return stringBuilder;
    }

    private Spanned getNonIntegerIndexException(Throwable e) {
        NonIntegerIndexException exception = (NonIntegerIndexException) e;
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        stringBuilder.append(String.valueOf(exception.getLineInfo()));
        stringBuilder.append("\n").append("\n");
        String format = String.format(mContext.getString(R.string.NonIntegerIndexException), exception.getValue().toString());
        stringBuilder.append(format);
        return highlight(mContext, stringBuilder);
    }

    private Spanned getPluginCallException(Throwable e) {
        MethodCallException exception = (MethodCallException) e;
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        String format = String.format(
                mContext.getString(R.string.PluginCallException),
                exception.function.toString(),
                exception.cause.getClass().getSimpleName());
        stringBuilder.append(format);
        stringBuilder.append("\n");
        stringBuilder.append("\n");

        stringBuilder.append(getMessage(exception.cause));
        return stringBuilder;
    }

    private Spanned getFileException(FileException e) {
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        stringBuilder.append(mContext.getString(R.string.file));
        stringBuilder.append(": ");
        stringBuilder.append(e.filePath);
        stringBuilder.setSpan(new ForegroundColorSpan(Color.YELLOW), 0,
                stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        stringBuilder.append("\n");
        stringBuilder.append("\n");

        if (e instanceof DiskReadErrorException) {
            stringBuilder.append(mContext.getString(R.string.DiskReadErrorException));
        } else if (e instanceof FileNotAssignException) {
            stringBuilder.append(mContext.getString(R.string.FileNotAssignException));
        } else if (e instanceof com.duy.pascal.interperter.libraries.file.exceptions.FileNotFoundException) {
            stringBuilder.append(mContext.getString(R.string.FileNotFoundException));
        } else if (e instanceof FileNotOpenException) {
            stringBuilder.append(mContext.getString(R.string.FileNotOpenException));
        } else if (e instanceof FileNotOpenForInputException) {
            stringBuilder.append(mContext.getString(R.string.FileNotOpenForInputException));
        }
        return stringBuilder;
    }

    private Spannable getBadOperationTypeException(BadOperationTypeException e) {
        String source;
        if (e.getValue1() == null) {
            source = String.format(mContext.getString(R.string.BadOperationTypeException2), e.getOperatorTypes());
        } else {
            source = String.format(mContext.getString(R.string.BadOperationTypeException), e.getOperatorTypes(), e.getValue1(), e.getValue2(), e.getDeclaredType(), e.getDeclaredType1());
        }
        SpannableStringBuilder spannable = new SpannableStringBuilder(e.getLineInfo().toString());
        spannable.append("\n\n").append(source);
        return highlight(mContext, spannable);
    }

    private Spannable getUnrecognizedTokenException(UnrecognizedTokenException e) {
        String msg = mContext.getString(R.string.token_not_belong) + " ";
        Spannable span = new SpannableString(msg + e.getToken().toString());
        span.setSpan(new ForegroundColorSpan(Color.YELLOW),
                msg.length(), msg.length() + e.getToken().toString().length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    private Spannable getEnumeratedGroupingException(GroupingException e) {
        GroupingException.Type exceptionTypes = e.getExceptionTypes();
        if (exceptionTypes == GroupingException.Type.IO_EXCEPTION) {
            return new SpannableString(mContext.getString(R.string.IO_EXCEPTION));
        } else if (exceptionTypes == GroupingException.Type.EXTRA_END) {
            return new SpannableString(mContext.getString(R.string.unbalance_end));
        } else if (exceptionTypes == GroupingException.Type.INCOMPLETE_CHAR) {
            return new SpannableString(mContext.getString(R.string.INCOMPLETE_CHAR));
        } else if (exceptionTypes == GroupingException.Type.MISMATCHED_BEGIN_END) {
            return new SpannableString(mContext.getString(R.string.MISMATCHED_BEGIN_END));
        } else if (exceptionTypes == GroupingException.Type.MISMATCHED_BRACKETS) {
            return new SpannableString(mContext.getString(R.string.MISMATCHED_BRACKETS));
        } else if (exceptionTypes == GroupingException.Type.MISMATCHED_PARENTHESES) {
            return new SpannableString(mContext.getString(R.string.MISMATCHED_PARENTHESES));
        } else if (exceptionTypes == GroupingException.Type.UNFINISHED_BEGIN_END) {
            return new SpannableString(mContext.getString(R.string.UNFINISHED_BEGIN_END));
        } else if (exceptionTypes == GroupingException.Type.UNFINISHED_PARENTHESES) {
            return new SpannableString(mContext.getString(R.string.UNFINISHED_PARENTHESES));
        } else if (exceptionTypes == GroupingException.Type.UNFINISHED_BRACKETS) {
            return new SpannableString(mContext.getString(R.string.UNFINISHED_BRACKETS));
        } else if (exceptionTypes == GroupingException.Type.MISSING_INCLUDE) {
            return new SpannableString(mContext.getString(R.string.MISSING_INCLUDE));
        } else if (exceptionTypes == GroupingException.Type.NEWLINE_IN_QUOTES) {
            return new SpannableString(mContext.getString(R.string.NEWLINE_IN_QUOTES));
        }
        return new SpannableString(e.getLocalizedMessage());
    }

    private Spannable getBadFunctionCallException(Throwable throwable) {
        BadFunctionCallException e = (BadFunctionCallException) throwable;
        boolean functionExists = e.getFunctionExists();
        boolean argsMatch = e.getArgsMatch();
        if (functionExists) { //function is exist, but wrong argument
            SpannableStringBuilder result = new SpannableStringBuilder();
            result.append(e.getLineInfo().toString()).append("\n\n");
            if (argsMatch) { //wrong type
                String msg = String.format(mContext.getString(R.string.BadFunctionCallException_1), e.getFunctionName());
                result.append(msg);
            } else { //wrong size of args
                String msg = String.format(mContext.getString(R.string.BadFunctionCallException_2), e.getFunctionName());
                result.append(msg);
            }

            //add list function
            List<String> functions = e.getFunctions();
            if (functions != null) {
                result.append("\n\n");
                result.append("Accept functions: ").append("\n");
                for (String function : functions) {
                    result.append(function).append("\n");
                }
            }

            return highlight(mContext, result);
        } else {
            String msg = String.format(mContext.getString(R.string.BadFunctionCallException_3), e.getFunctionName());
            SpannableString spannableString = new SpannableString(msg);
            return highlight(mContext, spannableString);
        }
    }

    private Spanned getExpectedTokenException(ExpectedTokenException e) {
        String msg = String.format(mContext.getString(R.string.ExpectedTokenException_3),
                ArrayUtil.expectToString(e.getExpected(), mContext), e.getCurrent());
        SpannableString spannableString = new SpannableString(msg);
        return highlight(mContext, spannableString);
    }
}
