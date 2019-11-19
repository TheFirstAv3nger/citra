package org.citra.emu.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.citra.emu.NativeLibrary;
import org.citra.emu.R;

public class KeyboardDialog extends DialogFragment {
    private static final String ARG_MAX_LENGTH = "max_length";
    private static final String ARG_INPUT_HINT = "hint";
    private static final String ARG_INPUT_BUTTON0 = "button0";
    private static final String ARG_INPUT_BUTTON1 = "button1";
    private static final String ARG_INPUT_BUTTON2 = "button2";

    private static final int EVENT_FINISH = -1;
    private static final int EVENT_BUTTON0 = 0;
    private static final int EVENT_BUTTON1 = 1;
    private static final int EVENT_BUTTON2 = 2;

    public static KeyboardDialog newInstance(int maxLength, String hint, String button0,
                                             String button1, String button2) {
        KeyboardDialog fragment = new KeyboardDialog();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_MAX_LENGTH, maxLength);
        arguments.putString(ARG_INPUT_HINT, hint);
        arguments.putString(ARG_INPUT_BUTTON0, button0);
        arguments.putString(ARG_INPUT_BUTTON1, button1);
        arguments.putString(ARG_INPUT_BUTTON2, button2);
        fragment.setArguments(arguments);
        return fragment;
    }

    private boolean mIsProcessed = false;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        int maxLength = args.getInt(ARG_MAX_LENGTH, -1);
        String hint = args.getString(ARG_INPUT_HINT, "");
        String text0 = args.getString(ARG_INPUT_BUTTON0, "");
        String text1 = args.getString(ARG_INPUT_BUTTON1, "");
        String text2 = args.getString(ARG_INPUT_BUTTON2, "");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        ViewGroup contents =
            (ViewGroup)getActivity().getLayoutInflater().inflate(R.layout.dialog_input_box, null);

        final EditText inputBox = contents.findViewById(R.id.text_input);
        if (maxLength > 0) {
            inputBox.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
        }
        if (!hint.isEmpty()) {
            inputBox.setText(hint);
        }

        TextView textInfo = contents.findViewById(R.id.max_length_info);
        if (maxLength > 0) {
            textInfo.setText(getString(R.string.input_text_max_length, maxLength));
        } else {
            textInfo.setVisibility(View.GONE);
        }

        Button button0 = contents.findViewById(R.id.button0);
        if (!text0.isEmpty()) {
            button0.setText(text0);
            button0.setOnClickListener(view -> {
                mIsProcessed = true;
                NativeLibrary.KeyboardEvent(EVENT_BUTTON0, inputBox.getText().toString());
                dismiss();
            });
        } else {
            button0.setVisibility(View.GONE);
        }

        Button button1 = contents.findViewById(R.id.button1);
        if (!text1.isEmpty()) {
            button1.setText(text1);
            button1.setOnClickListener(view -> {
                mIsProcessed = true;
                NativeLibrary.KeyboardEvent(EVENT_BUTTON1, inputBox.getText().toString());
                dismiss();
            });
        } else {
            button1.setVisibility(View.GONE);
        }

        Button button2 = contents.findViewById(R.id.button2);
        if (!text2.isEmpty()) {
            button2.setText(text2);
            button2.setOnClickListener(view -> {
                mIsProcessed = true;
                NativeLibrary.KeyboardEvent(EVENT_BUTTON2, inputBox.getText().toString());
                dismiss();
            });
        } else {
            button2.setVisibility(View.GONE);
        }

        builder.setOnDismissListener(dialog -> {
            if (!mIsProcessed)
                NativeLibrary.KeyboardEvent(EVENT_FINISH, inputBox.getText().toString());
        });

        builder.setView(contents);
        return builder.create();
    }
}
