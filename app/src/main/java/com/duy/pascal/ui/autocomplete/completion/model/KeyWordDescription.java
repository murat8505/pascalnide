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

package com.duy.pascal.ui.autocomplete.completion.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.duy.pascal.interperter.declaration.Name;
import com.duy.pascal.ui.editor.view.CodeSuggestsEditText;

/**
 * Created by Duy on 11/1/2017.
 */

public class KeyWordDescription extends DescriptionImpl {
    public KeyWordDescription(@NonNull String name, @Nullable String description) {
        super(KIND_KEYWORD, Name.create(name), description, null);
    }

    @Override
    public String getDisplayText() {
        return name.getOriginName() + " " + CodeSuggestsEditText.CURSOR;
    }
}
