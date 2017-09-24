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

package com.duy.pascal.frontend.autocomplete.autofix;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.Layout;
import android.widget.EditText;

import com.duy.pascal.frontend.DLog;
import com.duy.pascal.frontend.autocomplete.autofix.command.AutoFixCommand;
import com.duy.pascal.frontend.autocomplete.autofix.model.TextData;
import com.duy.pascal.frontend.autocomplete.completion.model.KeyWord;
import com.duy.pascal.frontend.autocomplete.completion.model.Patterns;
import com.duy.pascal.frontend.editor.view.AutoIndentEditText;
import com.duy.pascal.frontend.editor.view.EditorView;
import com.duy.pascal.frontend.editor.view.LineUtils;
import com.duy.pascal.interperter.ast.runtime_value.value.access.ConstantAccess;
import com.duy.pascal.interperter.ast.runtime_value.value.access.VariableAccess;
import com.duy.pascal.interperter.declaration.Name;
import com.duy.pascal.interperter.declaration.lang.function.FunctionDeclaration;
import com.duy.pascal.interperter.declaration.lang.types.Type;
import com.duy.pascal.interperter.exceptions.parsing.convert.UnConvertibleTypeException;
import com.duy.pascal.interperter.exceptions.parsing.define.MainProgramNotFoundException;
import com.duy.pascal.interperter.exceptions.parsing.define.TypeIdentifierExpectException;
import com.duy.pascal.interperter.exceptions.parsing.define.UnknownIdentifierException;
import com.duy.pascal.interperter.exceptions.parsing.grouping.GroupingException;
import com.duy.pascal.interperter.exceptions.parsing.missing.MissingTokenException;
import com.duy.pascal.interperter.exceptions.parsing.value.ChangeValueConstantException;
import com.duy.pascal.interperter.linenumber.LineInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.duy.pascal.frontend.structure.DialogProgramStructure.TAG;

/**
 * Created by Duy on 9/24/2017.
 */

public class AutoFixHelper {
    private AutoFixHelper() {
    }

    /**
     * This method will be import new type for program
     * the {@link TypeIdentifierExpectException} contains missing type.
     * <p>
     * First, we find the "type" keyword, if not found we will be create new keyword
     * Then, we insert a structure <code>"name" = "type"</code>
     */
    @NonNull
    private static AutoFixCommand fixMissingType(final TypeIdentifierExpectException exception) {
        return new AutoFixCommand() {

            @Override
            public void execute(EditorView editable) {
                //don't work if has selection
                //sub string from 0 to postion error
                TextData text = getText(editable, exception.getScope().getStartLine(), exception.getLineInfo());

                Name type = exception.getMissingType();
                String textToInsert;
                Matcher matcher = Patterns.TYPE.matcher(text.getText());
                int insertPosition = 0;

                if (matcher.find()) {
                    insertPosition = matcher.end();
                    textToInsert = "\n" + "    " + type + " = %t ;";
                } else {
                    /*
                    if not found "type" keyword, insert new type keyword
                    type    <== keyword type must be above var
                        ....
                    var
                        ....
                    */
                    if ((matcher = Patterns.PROGRAM.matcher(text.getText())).find()) {
                        insertPosition = matcher.end();
                    } else if ((matcher = Patterns.VAR.matcher(text.getText())).find()) {
                        insertPosition = matcher.start();
                    }
                    //if not found var keyword, insert "type" above "uses" keyword
                    else if ((matcher = Patterns.USES.matcher(text.getText())).find()) {
                        insertPosition = matcher.end();
                    }
                    textToInsert = "\ntype\n" + "    " + type + " = %t ;\n";
                }

                matcher = Patterns.REPLACE_CURSOR.matcher(textToInsert);
                if (matcher.find()) {
                    textToInsert = textToInsert.replaceAll("%\\w", "");

                    insertPosition += text.getOffset();
                    insertPosition = Math.max(0, insertPosition); //normalize

                    editable.getText().insert(insertPosition, textToInsert);
                    editable.setSelection(insertPosition + matcher.start());

                    //set suggest data
                    editable.setSuggestData(KeyWord.DATA_TYPE);
                }
            }
        };
    }

    /**
     * This method will be add missing define, such as variable,
     * constant, function or procedure
     */
    @Nullable
    private static AutoFixCommand fixMissingDefine(UnknownIdentifierException exception) {
        DLog.d(TAG, "fixMissingDefine() called with: e = [" + exception + "]" + " " + exception.getFitType());
        if (exception.getFitType() == DefineType.DECLARE_VAR) {
            //add missing var
            return declareVar(exception);
        } else if (exception.getFitType() == DefineType.DECLARE_CONST) {
            //add missing const
            return declareConst(exception);
        } else if (exception.getFitType() == DefineType.DECLARE_FUNCTION) {
            //add missing function
            return declareFunction(exception);
        } else if (exception.getFitType() == DefineType.DECLARE_PROCEDURE) {
            //add missing procedure
        }
        return null;
    }

    /**
     * Auto wrong type
     * For example
     * <code>
     * var c: integer;
     * begin
     * c := 'hello';            <=== this is wrong type
     * end.
     * </code>
     * <p>
     * This method will be match position of variable or function and change to
     * <code>
     * var c: string;             <== change to String
     * begin
     * c := 'hello';
     * end.
     * </code>
     */
    @NonNull
    private static AutoFixCommand fixUnConvertType(final UnConvertibleTypeException exception) {
        return new AutoFixCommand() {
            @Override
            public void execute(EditorView editable) {
                //get a part of text
                TextData text = getText(editable, exception.getScope().getStartLine(), exception.getLineInfo());
                if (exception.getIdentifier() instanceof VariableAccess) {
                    if (exception.getScope() instanceof FunctionDeclaration.FunctionExpressionContext) {
                        Name name = ((FunctionDeclaration.FunctionExpressionContext) exception.getScope()).function.getName();
                        //this is function name
                        if (name.equals(((VariableAccess) exception.getIdentifier()).getName())) {
                            changeTypeFunction(editable, name, text, exception.getValueType());
                        } else {
                            changeTypeVar(editable, text, (VariableAccess) exception.getIdentifier(), exception.getValueType());
                        }
                    } else {
                        changeTypeVar(editable, text, (VariableAccess) exception.getIdentifier(), exception.getValueType());
                    }
                } else if (exception.getIdentifier() instanceof ConstantAccess) {
                    changeTypeConst(editable, text, (ConstantAccess) exception.getIdentifier(), exception.getValueType());
                } else if (exception.getValue() instanceof VariableAccess) {
                    if (exception.getScope() instanceof FunctionDeclaration.FunctionExpressionContext) {
                        Name name = ((FunctionDeclaration.FunctionExpressionContext) exception.getScope()).function.getName();
                        //this is function name
                        if (name.equals(((VariableAccess) exception.getValue()).getName())) {
                            changeTypeFunction(editable, name, text, exception.getTargetType());
                        } else {
                            changeTypeVar(editable, text, (VariableAccess) exception.getValue(), exception.getTargetType());
                        }
                    } else {
                        changeTypeVar(editable, text, (VariableAccess) exception.getValue(), exception.getTargetType());
                    }

                } else if (exception.getValue() instanceof ConstantAccess) {
                    changeTypeConst(editable, text, (ConstantAccess) exception.getValue(), exception.getTargetType());
                }
            }
        };

    }

    private static AutoFixCommand insertToken(final MissingTokenException e) {
        return new AutoFixCommand() {
            @Override
            public void execute(EditorView editable) {
                LineInfo line = e.getLineInfo();
                int start = LineUtils.getStartIndexAtLine(editable, line.getLine()) + line.getColumn();
                String insertText = e.getMissingToken();
                editable.getText().insert(start, insertText);
                editable.setSelection(start, insertText.length() + start);
                editable.showKeyboard();
            }
        };
    }

    /**
     * Insert "end" into the final position of the editor
     */
    private static AutoFixCommand fixGroupException(final GroupingException e) {
        return new AutoFixCommand() {
            @Override
            public void execute(EditorView editable) {
                if (e.getExceptionTypes() == GroupingException.Type.UNFINISHED_BEGIN_END) {
                    String text = "\nend";
                    editable.getText().insert(editable.length(), text); //insert

                    //select the "end" token and show keyboard
                    //don't selected newline character
                    editable.setSelection(editable.length() - text.length() + 1, editable.length());
                    editable.showKeyboard();
                }
            }
        };
    }

    private static TextData getText(EditText editable, LineInfo startLine, LineInfo endLine) {
        CharSequence text = editable.getText().subSequence(
                editable.getLayout().getLineStart(startLine.getLine())
                        + startLine.getColumn(),

                editable.getLayout().getLineEnd(endLine.getLine()));

        int offset = editable.getLayout().getLineStart(startLine.getLine())
                + startLine.getColumn()
                + startLine.getLength();

        if (offset < 0) offset = 0;
        return new TextData(text, offset);
    }

    /**
     * This method will be declare const, the constant pascal
     * usually in the top of program, below "program" or "uses" keyword
     */
    @NonNull
    private static AutoFixCommand declareConst(final UnknownIdentifierException e) {
        return new AutoFixCommand() {
            @Override
            public void execute(EditorView editable) {
                //sub string from 0 to position error
                TextData text = getText(editable, e.getScope().getStartLine(), e.getLineInfo());

                String textToInsert = "";
                int insertPosition = 0;
                Name name = e.getName();

                Matcher matcher = Patterns.CONST.matcher(text.getText());
                if (matcher.find()) {
                    insertPosition = matcher.end();
                    textToInsert = "\n" + "    " + name + " = %v ;";
                } else {
                    if ((matcher = Patterns.PROGRAM.matcher(text.getText())).find()) {
                        insertPosition = matcher.end();
                    } else if ((matcher = Patterns.USES.matcher(text.getText())).find()) {
                        insertPosition = matcher.end();
                    } else if ((matcher = Patterns.TYPE.matcher(text.getText())).find()) {
                        insertPosition = matcher.start();
                    }
                    textToInsert = "\nconst \n" + AutoIndentEditText.TAB_CHARACTER + name + " = %v ;";
                }

                insertPosition += text.getOffset();

                matcher = Patterns.REPLACE_CURSOR.matcher(textToInsert);
                if (matcher.find()) {
                    textToInsert = textToInsert.replaceAll("%\\w", "");

                    editable.getText().insert(insertPosition, textToInsert);
                    editable.setSelection(insertPosition + matcher.start());
                }
            }
        };
    }

    @Nullable
    private static AutoFixCommand declareFunction(UnknownIdentifierException e) {
        return null;
    }

    /**
     * This method will be declare variable, the variable often below the
     * "const", "uses", "program" keyword,
     * First, match position of list keyword
     * Then insert new variable
     */
    @Nullable
    private static AutoFixCommand declareVar(UnknownIdentifierException e) {
        return declareVar(new LineInfo[]{e.getScope().getStartLine(), e.getLineInfo()},
                e.getName(),
                "",//unknown type
                null); //non init value
    }

    @Nullable
    private static AutoFixCommand declareVar(LineInfo[] lines, Name name, String type, String initValue) {
        if (lines.length != 2) {
            android.util.Log.e(TAG, "The length line array must be 2");
            return null;
        }
        return declareVar(lines[0], lines[1], name, type, initValue);
    }

    private static AutoFixCommand declareVar(final LineInfo start, final LineInfo end, final Name name, final String type, final String initValue) {
        return new AutoFixCommand() {
            @Override
            public void execute(EditorView editable) {
                declareVar(getText(editable, start, end), name, type, initValue).execute(editable);
            }
        };
    }

    /**
     * Declare variable with name and type
     *
     * @param scope     - scope of variable
     * @param name      - the name of variable will be declared
     * @param type      - the type of variable will be declared
     * @param initValue - init value
     */
    private static AutoFixCommand declareVar(final TextData scope, final Name name,
                                             final String type, final String initValue) {
        return new AutoFixCommand() {
            @Override
            public void execute(EditorView editable) {
                String textToInsert = "";
                int insertPosition = 0;
                int startSelect;
                int endSelect;

                Matcher matcher = Patterns.VAR.matcher(scope.getText());
                if (matcher.find()) {
                    insertPosition = matcher.end();
                    textToInsert = AutoIndentEditText.TAB_CHARACTER + name + ": ";

                    startSelect = textToInsert.length();
                    endSelect = startSelect + type.length();

                    textToInsert += type + (initValue != null ? " = " + initValue : "") + ";\n";
                } else {
                    if ((matcher = Patterns.TYPE.matcher(scope.getText())).find()) {
                        insertPosition = matcher.end();
                    } else if ((matcher = Patterns.USES.matcher(scope.getText())).find()) {
                        insertPosition = matcher.end();
                    } else if ((matcher = Patterns.PROGRAM.matcher(scope.getText())).find()) {
                        insertPosition = matcher.end();
                    }
                    textToInsert = "\nvar\n" + AutoIndentEditText.TAB_CHARACTER + name + ": ";

                    startSelect = textToInsert.length();
                    endSelect = startSelect + type.length();

                    textToInsert += type + (initValue != null ? " = " + initValue : "") + ";\n";
                }

                editable.getText().insert(scope.getOffset() + insertPosition, textToInsert);
                editable.setSelection(scope.getOffset() + insertPosition + startSelect,
                        scope.getOffset() + insertPosition + endSelect);

                //set suggest data
                editable.restoreAfterClick(KeyWord.DATA_TYPE);

                editable.showKeyboard();
            }
        };
    }

    /**
     * This method will be Change type constant to type of value
     * if constant is define with type
     * <p>
     * Example
     * const a: integer = 'adsda'; => change to string
     */
    private static void changeTypeConst(EditorView editable, TextData text, ConstantAccess identifier, Type valueType) {
        DLog.d(TAG, "fixUnConvertType: constant " + identifier);

        if (identifier.getName() == null) { //can not replace because it is not a identifier
            DLog.d(TAG, "changeTypeConst: this is not identifier");
            return;
        }

        Name name = identifier.getName();
        Pattern pattern = Pattern.compile("(^const\\s+|\\s+const\\s+)" + //match "const"  //1
                        "(.*?)" + //other const                                  //2
                        "(" + name + ")" + //name of const                       //3
                        "(\\s?)" +//one or more white space                         //4
                        "(:)" + //colon                                             //5
                        "(.*?)" + //type????                                        //6
                        "(=)" +
                        "(.*?)" +
                        "(;)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text.getText());

        if (matcher.find()) {
            DLog.d(TAG, "fixUnConvertType: match " + matcher);
            final int start = matcher.start(6) + text.getOffset();
            int end = matcher.end(6) + text.getOffset();

            final String insertText = valueType.toString();
            editable.getText().replace(start, end, insertText);
            editable.setSelection(start, start + insertText.length());
            editable.showKeyboard();
        }
    }

    /**
     * Change type of function from <code>valueType</code> to <code>name</code>
     *
     * @param editable
     * @param name     - name of function
     * @param text     - a part text of the edit start at 0 and end at lineInfo where then function place
     */
    private static void changeTypeFunction(EditorView editable, final Name name, TextData text, Type valueType) {
        Pattern pattern = Pattern.compile(
                "(^function\\s+|\\s+function\\s+)" + //function token //1
                        "(" + name + ")" + //name of function         //2
                        "(\\s?)" + //white space                      //3
                        "(:)" +                                       //4
                        "(.*?)" + //type of function                  //5
                        ";"); //end                                   //6
        Matcher matcher = pattern.matcher(text.getText());
        if (matcher.find()) {
            DLog.d(TAG, "changeTypeFunction: match " + matcher);
            int start = matcher.start(5) + text.getOffset();
            int end = matcher.end(5) + text.getOffset();

            String insertText = valueType.toString();
            editable.getText().replace(start, end, insertText);
            editable.setSelection(start, start + insertText.length());
            editable.showKeyboard();
        } else {
            DLog.d(TAG, "changeTypeFunction: can not find " + pattern);
        }
    }

    /**
     * Change type of variable
     *
     * @param editable
     * @param text       - type to change
     * @param identifier - variable
     * @param valueType  - current type of variable
     */
    private static void changeTypeVar(EditorView editable, TextData text, VariableAccess identifier, Type valueType) {
        DLog.d(TAG, "fixUnConvertType: variable");
        final Name name = identifier.getName();
        Pattern pattern = Pattern.compile("(^var\\s+|\\s+var\\s+)" + //match "var"  //1
                "(.*?)" + //other variable                                  //2
                "(" + name + ")" + //name of variable                       //3
                "(\\s?)" +//one or more white space                         //4
                "(:)" + //colon                                             //5
                "(.*?)" + //any type                                        //6
                "(;)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher matcher = pattern.matcher(text.getText());
        DLog.d(TAG, "fixUnConvertType: " + text);

        if (matcher.find()) {
            DLog.d(TAG, "fixUnConvertType: match " + matcher);
            int start = matcher.start(6) + text.getOffset();
            int end = matcher.end(6) + text.getOffset();

            String insertText = " " + valueType.toString();
            editable.getText().replace(start, end, insertText);
            editable.setSelection(start + 1, start + insertText.length());
            editable.showKeyboard();
        } else {
            DLog.d(TAG, "fixUnConvertType: can not find " + pattern);
        }
    }

    public static AutoFixCommand changeConstToVar(final ChangeValueConstantException e) {
        return new AutoFixCommand() {
            @Override
            public void execute(EditorView editable) {
                DLog.d(TAG, "changeConstToVar: " + e);

                TextData text = getText(editable, e.getScope().getStartLine(), e.getLineInfo());
                ConstantAccess<Object> constant = e.getConst();
                Pattern pattern = Pattern.compile(
                        "(^const\\s+|\\s+const\\s+)" + //1
                                "(" + constant.getName() + ")" + //2
                                "(\\s?)" + //3
                                "(=)" +//4
                                "(.*?)" +//5
                                "(;)",//6
                        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

                Matcher matcher = pattern.matcher(text.getText());
                if (matcher.find()) {
                    DLog.d(TAG, "changeConstToVar: " + matcher);
                    int start = matcher.start(2) + text.getOffset() - 1;
                    start = Math.max(0, start);
                    int end = matcher.end(6) + text.getOffset();

                    editable.getText().delete(start, end);

                    declareVar(text,
                            constant.getName(), //name
                            constant.getRuntimeType(null).declType.toString(), //type
                            constant.toCode()); //initialization value
                } else {
                    pattern = Pattern.compile(
                            "(^const\\s+|\\s+const\\s+)" + //1
                                    "(" + constant.getName() + ")" + //2
                                    "(\\s?)" + //3
                                    "(:)" + //4
                                    "(\\s?)" +//5
                                    "(.*?)" +//6 type
                                    "(=)" + //7
                                    "(.*?)" +//8
                                    "(;)" //9
                            , Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

                    matcher = pattern.matcher(text.getText());
                    if (matcher.find()) {
                        int start = matcher.start(2) + text.getOffset() - 1;
                        start = Math.max(0, start);
                        int end = matcher.end(9) + text.getOffset();

                        editable.getText().delete(start, end);

                        declareVar(text,
                                constant.getName(),  //name
                                constant.getRuntimeType(null).declType.toString(), //type
                                constant.toCode());//initialization value
                    }
                }
            }
        };

    }

    public static AutoFixCommand fixProgramNotFound() {
        return new AutoFixCommand() {
            @Override
            public void execute(EditorView editable) {
                String tabCharacter = editable.getTabCharacter();
                editable.getText().insert(editable.length(), "\nbegin\n" + tabCharacter + "\nend.\n");
                editable.setSelection(editable.length() - "\nend.\n".length());
            }
        };
    }

    @Nullable
    public static AutoFixCommand createCommand(Exception e) {
        if (e instanceof TypeIdentifierExpectException) {
            return fixMissingType((TypeIdentifierExpectException) e);
        } else if (e instanceof UnknownIdentifierException) {
            return fixMissingDefine((UnknownIdentifierException) e);
        } else if (e instanceof UnConvertibleTypeException) {
            return fixUnConvertType((UnConvertibleTypeException) e);
        } else if (e instanceof MissingTokenException) {
            return insertToken((MissingTokenException) e);
        } else if (e instanceof ChangeValueConstantException) {
            return changeConstToVar((ChangeValueConstantException) e);
        } else if (e instanceof GroupingException) {
            return fixGroupException((GroupingException) e);
        } else if (e instanceof MainProgramNotFoundException) {
            return fixProgramNotFound();
        }
        return null;
    }

    /**
     * replace current token by expect token exactly
     *
     * @param current - current token
     * @param expect  - token for replace
     * @param insert  - true if insert, <code>false</code> if replace
     * @param line    - current lineInfo
     * @param column  - start at column of @lineInfo
     */
    public static AutoFixCommand fixExpectToken(final String current, final String expect, final boolean insert, final int line, final int column) {
        return new AutoFixCommand() {
            @Override
            public void execute(EditorView editable) {

                DLog.d(TAG, "fixExpectToken() called with: current = [" + current + "], expect = [" + expect + "], insert = [" + insert + "], line = [" + line + "], column = [" + column + "]");
                //get text in lineInfo
                CharSequence textInLine = getTextInLine(editable, line, column);

                //position from 0 to current token
                int offset = LineUtils.getStartIndexAtLine(editable, line) + column;

                //find token
                Pattern pattern = Pattern.compile("(" + current + ")"); //current token
                Matcher matcher = pattern.matcher(textInLine);
                if (matcher.find()) {

                    int start = matcher.start();
                    int end = matcher.end();

                    //insert or replace other token
                    Editable text = editable.getText();
                    if (!insert) {
                        text.replace(offset + start, offset + start + end, expect);
                    } else {
                        String insert = " " + expect + " ";
                        text.insert(offset + start, insert);
                    }
                    editable.setSelection(offset + start, offset + start + expect.length());
                    editable.showKeyboard();
                }
            }
        };
    }

    /**
     * get text in lineInfo
     */
    private CharSequence getTextInLine(EditorView editable, int line, int column) {
        Editable text = editable.getText();
        Layout layout = editable.getLayout();
        if (layout != null) {
            int lineStart = layout.getLineStart(line);
            int lineEnd = layout.getLineEnd(line);
            lineStart = lineStart + column;
            if (lineStart > text.length()) lineStart = text.length();
            if (lineStart > lineEnd) lineStart = lineEnd;
            return text.subSequence(lineStart, lineEnd);
        }
        return "";
    }
}
