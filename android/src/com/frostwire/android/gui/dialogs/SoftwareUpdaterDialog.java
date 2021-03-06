/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.platform.Platforms;
import com.frostwire.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.frostwire.android.util.SystemUtils.hasNougatOrNewer;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SoftwareUpdaterDialog extends AbstractDialog {

    public SoftwareUpdaterDialog() {
        super(R.layout.dialog_default_update);
    }

    public static SoftwareUpdaterDialog newInstance(
            Map<String, String> updateMessages, List<String> changelog) {
        SoftwareUpdaterDialog dlg = new SoftwareUpdaterDialog();

        Bundle args = new Bundle();
        args.putSerializable("updateMessages", new HashMap<>(updateMessages));
        args.putStringArrayList("changelog", new ArrayList<>(changelog));
        dlg.setArguments(args);

        return dlg;
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
        Bundle args = getArguments();
        @SuppressWarnings("unchecked")
        HashMap<String, String> updateMessages = (HashMap<String, String>) args.getSerializable("updateMessages");
        ArrayList<String> changelog = args.getStringArrayList("changelog");

        String message = StringUtils.getLocaleString(updateMessages, getString(R.string.update_message));

        TextView title = findView(dlg, R.id.dialog_default_update_title);
        title.setText(R.string.update_title);

        TextView text = findView(dlg, R.id.dialog_default_update_text);
        text.setText(message);

        final ListView listview = findView(dlg, R.id.dialog_default_update_list_view);
        String[] values = new String[changelog.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = String.valueOf(Html.fromHtml("&#8226; " + changelog.get(i)));
        }

        final ArrayAdapter adapter = new ArrayAdapter<>(getActivity(),
                R.layout.dialog_update_bullet,
                R.id.dialog_update_bullets_checked_text_view,
                values);
        listview.setAdapter(adapter);

        // Set the save button action
        Button noButton = findView(dlg, R.id.dialog_default_update_button_no);
        noButton.setText(R.string.cancel);

        Button yesButton = findView(dlg, R.id.dialog_default_update_button_yes);
        yesButton.setText(android.R.string.ok);
        yesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Engine.instance().stopServices(false);
                // since Nougat, a naked file path can't be put directly inside
                // an intent
                boolean useFileProvider = hasNougatOrNewer();
                UIUtils.openFile(getActivity(), getUpdateApk().getAbsolutePath(),
                        Constants.MIME_TYPE_ANDROID_PACKAGE_ARCHIVE, useFileProvider);
                dismiss();
            }
        });
        noButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    private static File getUpdateApk() {
        return Platforms.get().systemPaths().update();
    }
}
