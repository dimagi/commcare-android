package org.commcare.views.connect;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.commcare.dalvik.R;
import org.commcare.utils.KeyboardHelper;

public class NumericCodeView extends LinearLayout {

    private int digitCount = 4;
    private int borderColor = Color.BLACK;
    private int errorBorderColor = Color.RED;
    private int borderRadius = 5;
    private int borderWidth = 2;
    private int textColor = Color.BLACK;
    private int errorTextColor = Color.RED;
    private float textSize = 10;
    private boolean passwordMode = false;
    private boolean passwordVisible = false;
    private String[] actualValues;
    private int lastEditedIndex = -1;
    private CodeCompleteListener codeCompleteListener;
    private OnCodeChangedListener codeChangedListener;
    private OnEnterKeyPressedListener enterKeyPressedListener;
    private boolean isErrorState = false;
    private boolean isSelfUpdate = false;

    public NumericCodeView(Context context) {
        super(context);
        init(null);
    }

    public NumericCodeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);

        // Read attributes from XML
        if (attrs != null) {
            try (TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.NumericCodeView)) {
                digitCount = typedArray.getInt(R.styleable.NumericCodeView_codeViewDigitCount, digitCount);
                borderColor = typedArray.getColor(R.styleable.NumericCodeView_codeViewBorderColor, borderColor);
                errorBorderColor = typedArray.getColor(R.styleable.NumericCodeView_codeViewErrorBorderColor, errorBorderColor);
                borderRadius = typedArray.getDimensionPixelSize(R.styleable.NumericCodeView_codeViewBorderRadius, borderRadius);
                borderWidth = typedArray.getDimensionPixelSize(R.styleable.NumericCodeView_codeViewBorderWidth, borderWidth);
                textColor = typedArray.getColor(R.styleable.NumericCodeView_codeViewTextColor, textColor);
                errorTextColor = typedArray.getColor(R.styleable.NumericCodeView_codeViewErrorTextColor, errorTextColor);
                textSize = typedArray.getDimension(R.styleable.NumericCodeView_codeViewTextSize, textSize);
                passwordMode = typedArray.getBoolean(R.styleable.NumericCodeView_codeViewPasswordMode, false);
            }
        }

        actualValues = new String[digitCount];
        for (int i = 0; i < digitCount; i++) {
            actualValues[i] = "";
        }

        createCodeFields();
    }

    private void createCodeFields() {
        removeAllViews();
        for (int i = 0; i < digitCount; i++) {
            EditText codeEditText = createCodeEditText(i);
            addView(codeEditText);
        }
    }

    private EditText createCodeEditText(int index) {
        EditText editText = new EditText(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1.0f
        );
        params.setMargins(8, 8, 8, 8);
        editText.setLayoutParams(params);
        editText.setGravity(Gravity.CENTER);
        editText.setTextColor(isErrorState ? errorTextColor : textColor);
        editText.setTextSize(textSize);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setBackground(createBackgroundDrawable());
        editText.setId(index);

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && index > 0 && ((EditText) getChildAt(index)).getText().length() == 0) {
                // Move to the first empty edit text if it's focused and empty
                for (int i = 0; i < digitCount; i++) {
                    if (actualValues[i].isEmpty()) {
                        getChildAt(i).requestFocus();
                        break;
                    }
                }
            } else if (!hasFocus && passwordMode && !passwordVisible) {
                // When focus leaves, check if it moved outside the entire view
                post(() -> {
                    if (!NumericCodeView.this.hasFocus()) {
                        lastEditedIndex = -1;
                        refreshAllDisplays();
                    }
                });
            }
        });

        editText.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (enterKeyPressedListener != null) {
                    enterKeyPressedListener.onEnterKeyPressed();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (actualValues[index].isEmpty() && index > 0) {
                    // Move to previous field and clear it
                    actualValues[index - 1] = "";
                    lastEditedIndex = -1;
                    isSelfUpdate = true;
                    EditText previousEditText = (EditText) getChildAt(index - 1);
                    previousEditText.setText("");
                    isSelfUpdate = false;
                    previousEditText.requestFocus();
                    notifyCodeChanged();
                } else {
                    // Clear current field
                    actualValues[index] = "";
                    lastEditedIndex = -1;
                    isSelfUpdate = true;
                    editText.setText("");
                    isSelfUpdate = false;
                    notifyCodeChanged();
                }
                return true;
            }
            return false;
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isSelfUpdate) {
                    return;
                }

                setErrorState(false);

                String input = s.toString();

                if (input.length() == 1) {
                    if (passwordMode && input.equals("*")) {
                        return;
                    }
                    actualValues[index] = input;
                    lastEditedIndex = index;
                    updateMaskedDisplay();

                    if (index < digitCount - 1) {
                        moveToNextBox(index);
                    } else {
                        checkCodeCompletion();
                    }
                    notifyCodeChanged();
                } else if (input.length() > 1) {
                    // If more than one character, keep only the latest digit
                    char lastChar = input.charAt(input.length() - 1);
                    String text = "";
                    if (Character.isDigit(lastChar)) {
                        text = String.valueOf(lastChar);
                    }
                    actualValues[index] = text;
                    lastEditedIndex = index;

                    isSelfUpdate = true;
                    editText.setText(getDisplayChar(index));
                    if (!getDisplayChar(index).isEmpty()) {
                        editText.setSelection(1);
                    }
                    isSelfUpdate = false;

                    if (!text.isEmpty() && index < digitCount - 1) {
                        moveToNextBox(index);
                    }
                    notifyCodeChanged();
                } else if (!actualValues[index].isEmpty()) {
                    actualValues[index] = "";
                    notifyCodeChanged();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isSelfUpdate) return;

                if (s.length() > 1) {
                    isSelfUpdate = true;
                    String display = getDisplayChar(index);
                    editText.setText(display);
                    if (!display.isEmpty()) {
                        editText.setSelection(1);
                    }
                    isSelfUpdate = false;
                }
            }
        });

        return editText;
    }

    private String getDisplayChar(int index) {
        if (actualValues[index].isEmpty()) {
            return "";
        }
        if (!passwordMode || passwordVisible) {
            return actualValues[index];
        }
        // In password mode, show the most recently edited digit, mask others
        if (index == lastEditedIndex) {
            return actualValues[index];
        }
        return "*";
    }

    private void moveToNextBox(int currentIndex) {
        actualValues[currentIndex + 1] = "";
        EditText nextEditText = (EditText) getChildAt(currentIndex + 1);
        isSelfUpdate = true;
        nextEditText.setText("");
        isSelfUpdate = false;
        nextEditText.requestFocus();
    }

    private void updateMaskedDisplay() {
        if (!passwordMode) return;

        isSelfUpdate = true;
        for (int i = 0; i < digitCount; i++) {
            EditText editText = (EditText) getChildAt(i);
            String display = getDisplayChar(i);
            if (!editText.getText().toString().equals(display)) {
                editText.setText(display);
                if (!display.isEmpty() && editText.hasFocus()) {
                    editText.setSelection(1);
                }
            }
        }
        isSelfUpdate = false;
    }

    private void notifyCodeChanged() {
        if (codeChangedListener != null) {
            codeChangedListener.onCodeChanged(getCodeValue());
        }
    }

    public String getCodeValue() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < digitCount; i++) {
            code.append(actualValues[i]);
        }
        return code.toString();
    }

    private GradientDrawable createBackgroundDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(borderRadius);
        drawable.setStroke(borderWidth, isErrorState ? errorBorderColor : borderColor);
        return drawable;
    }

    private void checkCodeCompletion() {
        String code = getCodeValue();
        if (code.length() == digitCount && codeCompleteListener != null) {
            codeCompleteListener.onCodeComplete(code);
        }
    }

    public void setCodeCompleteListener(CodeCompleteListener listener) {
        this.codeCompleteListener = listener;
    }

    public void setOnCodeChangedListener(OnCodeChangedListener listener) {
        this.codeChangedListener = listener;
    }

    public void setErrorState(boolean isError) {
        this.isErrorState = isError;
        updateUi();
    }

    private void updateUi() {
        for (int i = 0; i < getChildCount(); i++) {
            EditText editText = (EditText) getChildAt(i);
            editText.setTextColor(isErrorState ? errorTextColor : textColor);
            editText.setBackground(createBackgroundDrawable());
        }
    }

    public void setPasswordVisible(boolean visible) {
        this.passwordVisible = visible;
        refreshAllDisplays();
    }

    public boolean isPasswordVisible() {
        return passwordVisible;
    }

    private void refreshAllDisplays() {
        isSelfUpdate = true;
        for (int i = 0; i < digitCount; i++) {
            EditText editText = (EditText) getChildAt(i);
            String display = getDisplayChar(i);
            if (!editText.getText().toString().equals(display)) {
                editText.setText(display);
                if (!display.isEmpty() && editText.hasFocus()) {
                    editText.setSelection(1);
                }
            }
        }
        isSelfUpdate = false;
    }

    public void clearCode() {
        lastEditedIndex = -1;
        for (int i = 0; i < digitCount; i++) {
            actualValues[i] = "";
        }
        isSelfUpdate = true;
        for (int i = 0; i < digitCount; i++) {
            ((EditText) getChildAt(i)).setText("");
        }
        isSelfUpdate = false;
        if (getChildCount() > 0) {
            getChildAt(0).requestFocus();
        }
        notifyCodeChanged();
    }

    public void requestFocus(Activity activity) {
        if (getChildCount() > 0) {
            KeyboardHelper.showKeyboardOnInput(activity, getChildAt(0));
        }
    }

    public interface CodeCompleteListener {
        void onCodeComplete(String code);
    }

    public interface OnCodeChangedListener {
        void onCodeChanged(String code);
    }

    public interface OnEnterKeyPressedListener {
        void onEnterKeyPressed();
    }

    public void setOnEnterKeyPressedListener(OnEnterKeyPressedListener listener) {
        this.enterKeyPressedListener = listener;
    }

    public void setCode(String code) {
        if (code.length() > digitCount) {
            throw new IllegalArgumentException("Code length exceeds the digit count");
        }

        lastEditedIndex = -1;
        for (int i = 0; i < digitCount; i++) {
            EditText editText = (EditText) getChildAt(i);
            if (i < code.length()) {
                actualValues[i] = String.valueOf(code.charAt(i));
                isSelfUpdate = true;
                editText.setText(getDisplayChar(i));
                isSelfUpdate = false;
            } else {
                actualValues[i] = "";
                isSelfUpdate = true;
                editText.setText("");
                isSelfUpdate = false;
            }
            editText.clearFocus();
        }
        notifyCodeChanged();
        checkCodeCompletion();
    }
}
