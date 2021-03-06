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

package com.duy.pascal.ui.themefont.adapter;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.duy.pascal.interperter.linenumber.LineInfo;
import com.duy.pascal.ui.R;
import com.duy.pascal.ui.code.CodeSample;
import com.duy.pascal.ui.editor.view.EditorView;
import com.duy.pascal.ui.purchase.Premium;
import com.duy.pascal.ui.themefont.fragments.ThemeFragment;
import com.duy.pascal.ui.themefont.model.CodeTheme;
import com.duy.pascal.ui.themefont.themes.ThemeManager;
import com.duy.pascal.ui.themefont.themes.database.ThemeDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.CodeThemeHolder> {
    private ArrayList<CodeTheme> mThemes = new ArrayList<>();
    private LayoutInflater mInflater;
    private Activity mContext;

    @Nullable
    private ThemeFragment.OnThemeSelectListener mOnThemeSelectListener;
    private ThemeDatabase mDatabase;

    public ThemeAdapter(Activity context) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        loadTheme(context);
        mDatabase = new ThemeDatabase(context);
    }

    private void loadTheme(Context context) {
        HashMap<String, CodeTheme> all = ThemeManager.getAll(context);
        mThemes.clear();
        for (Map.Entry<String, CodeTheme> entry : all.entrySet()) {
            mThemes.add(entry.getValue());
        }
        Collections.sort(mThemes, new Comparator<CodeTheme>() {
            @Override
            public int compare(CodeTheme codeTheme, CodeTheme t1) {
                if (codeTheme.isPremium() || t1.isPremium()) {
                    if (codeTheme.isPremium() && t1.isPremium()) {
                        return codeTheme.getName().compareTo(t1.getName());
                    }
                    if (codeTheme.isPremium()) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
                return codeTheme.getName().compareTo(t1.getName());
            }
        });
    }

    public void clear() {
        mThemes.clear();
        notifyDataSetChanged();
    }


    @Override
    public CodeThemeHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.list_item_theme, parent, false);
        return new CodeThemeHolder(view);
    }

    @Override
    public void onBindViewHolder(CodeThemeHolder holder, final int position) {
        final CodeTheme entry = mThemes.get(position);
        if (entry.isBuiltin()) {
            holder.imgDelete.setVisibility(View.GONE);
        } else {
            holder.imgDelete.setVisibility(View.VISIBLE);
            holder.imgDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mDatabase.delete(entry);
                    remove(position);
                    Toast.makeText(mContext, R.string.deleted, Toast.LENGTH_SHORT).show();
                }
            });
        }
        holder.editorView.setLineError(new LineInfo(3, 0, ""));
        holder.editorView.setCodeTheme(entry);
        holder.editorView.setTextHighlighted(CodeSample.DEMO_THEME);
        holder.txtTitle.setText(entry.getName());

        if (entry.isPremium() && !Premium.isPremiumUser(mContext)) {
            holder.btnSelect.setText(R.string.premium_version);
        } else {
            holder.btnSelect.setText(R.string.select);
        }
        holder.btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnThemeSelectListener != null) {
                    mOnThemeSelectListener.onThemeSelected(entry);
                }
            }
        });
    }

    private void remove(int pos) {
        mThemes.remove(pos);
        notifyItemRemoved(pos);
    }

    @Override
    public int getItemCount() {
        return mThemes.size();
    }

    @Nullable
    public ThemeFragment.OnThemeSelectListener getOnThemeSelectListener() {
        return mOnThemeSelectListener;
    }

    public void setOnThemeSelectListener(@Nullable ThemeFragment.OnThemeSelectListener onThemeSelectListener) {
        this.mOnThemeSelectListener = onThemeSelectListener;
    }

    public void reload(Context context) {
        mThemes.clear();
        loadTheme(context);
        notifyDataSetChanged();
    }

    static class CodeThemeHolder extends RecyclerView.ViewHolder {
        View imgDelete;
        EditorView editorView;
        TextView txtTitle;
        Button btnSelect;

        CodeThemeHolder(View itemView) {
            super(itemView);
            editorView = itemView.findViewById(R.id.editor_view);
            txtTitle = itemView.findViewById(R.id.txt_name);
            btnSelect = itemView.findViewById(R.id.btn_select);
            imgDelete = itemView.findViewById(R.id.img_delete);
        }
    }

}
