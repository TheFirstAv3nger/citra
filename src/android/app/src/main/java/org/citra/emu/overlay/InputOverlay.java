package org.citra.emu.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import java.util.ArrayList;
import org.citra.emu.NativeLibrary.ButtonType;
import org.citra.emu.R;

public final class InputOverlay extends SurfaceView implements View.OnTouchListener {
    public static final String PREF_CONTROLLER_INIT = "InitOverlay";
    public static final String PREF_JOYSTICK_RELATIVE = "JoystickRelative";
    public static final String PREF_CONTROLLER_SCALE = "ControllerScale";
    public static final String PREF_CONTROLLER_ALPHA = "ControllerAlpha";

    public static int sControllerScale = 50;
    public static int sControllerAlpha = 100;
    public static boolean sJoystickRelative = true;
    public static boolean sEmulateMotionByTouch = false;

    private final ArrayList<InputOverlayButton> mButtons = new ArrayList<>();
    private final ArrayList<InputOverlayDpad> mDpads = new ArrayList<>();
    private final ArrayList<InputOverlayJoystick> mJoysticks = new ArrayList<>();
    private boolean mIsLandscape = false;
    private boolean mInEditMode = false;
    private InputOverlayButton mButtonBeingConfigured;
    private InputOverlayDpad mDpadBeingConfigured;
    private InputOverlayJoystick mJoystickBeingConfigured;
    private InputOverlayPointer mOverlayPointer = null;

    private SharedPreferences mPreferences;

    public InputOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnTouchListener(this);
        setWillNotDraw(false);
        requestFocus();

        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (!mPreferences.getBoolean(PREF_CONTROLLER_INIT, false)) {
            defaultOverlay();
        }
        sJoystickRelative = mPreferences.getBoolean(InputOverlay.PREF_JOYSTICK_RELATIVE, true);
        sControllerScale = mPreferences.getInt(InputOverlay.PREF_CONTROLLER_SCALE, 50);
        sControllerAlpha = mPreferences.getInt(InputOverlay.PREF_CONTROLLER_ALPHA, 100);

        // Load the controls.
        refreshControls();
    }

    private void defaultOverlay() {
        SharedPreferences.Editor sPrefsEditor = mPreferences.edit();
        Resources res = getResources();
        int[][] buttons = new int[][] {
            {ButtonType.N3DS_BUTTON_A, R.integer.BUTTON_A_X, R.integer.BUTTON_A_Y},
            {ButtonType.N3DS_BUTTON_B, R.integer.BUTTON_B_X, R.integer.BUTTON_B_Y},
            {ButtonType.N3DS_BUTTON_X, R.integer.BUTTON_X_X, R.integer.BUTTON_X_Y},
            {ButtonType.N3DS_BUTTON_Y, R.integer.BUTTON_Y_X, R.integer.BUTTON_Y_Y},
            {ButtonType.N3DS_BUTTON_START, R.integer.BUTTON_START_X, R.integer.BUTTON_START_Y},
            {ButtonType.N3DS_BUTTON_SELECT, R.integer.BUTTON_SELECT_X, R.integer.BUTTON_SELECT_Y},
            {ButtonType.N3DS_BUTTON_ZL, R.integer.TRIGGER_L_X, R.integer.TRIGGER_L_Y},
            {ButtonType.N3DS_BUTTON_ZR, R.integer.TRIGGER_R_X, R.integer.TRIGGER_R_Y},
            {ButtonType.N3DS_BUTTON_L, R.integer.TRIGGER_L_X, R.integer.TRIGGER_L_Y},
            {ButtonType.N3DS_BUTTON_R, R.integer.TRIGGER_R_X, R.integer.TRIGGER_R_Y},
            {ButtonType.N3DS_DPAD_UP, R.integer.PAD_MAIN_X, R.integer.PAD_MAIN_Y},
            {ButtonType.N3DS_CPAD_X, R.integer.STICK_MAIN_X, R.integer.STICK_MAIN_Y},
        };

        for (int i = 0; i < buttons.length; ++i) {
            int id = buttons[i][0];
            int x = buttons[i][1];
            int y = buttons[i][2];
            float posX = res.getInteger(x) / 100.0f;
            float posY = res.getInteger(y) / 100.0f;
            sPrefsEditor.putFloat(id + "_X", posX);
            sPrefsEditor.putFloat(id + "_Y", posY);
            sPrefsEditor.putFloat(id + "_XX", posX);
            sPrefsEditor.putFloat(id + "_YY", posY);
        }

        sPrefsEditor.putBoolean(PREF_CONTROLLER_INIT, true);
        sPrefsEditor.apply();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        for (InputOverlayButton button : mButtons) {
            button.onDraw(canvas);
        }

        for (InputOverlayDpad dpad : mDpads) {
            dpad.onDraw(canvas);
        }

        for (InputOverlayJoystick joystick : mJoysticks) {
            joystick.onDraw(canvas);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (isInEditMode()) {
            return onTouchWhileEditing(event);
        }

        boolean isProcessed = false;
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN: {
            int pointerIndex = event.getActionIndex();
            int pointerId = event.getPointerId(pointerIndex);
            float pointerX = event.getX(pointerIndex);
            float pointerY = event.getY(pointerIndex);

            for (InputOverlayJoystick joystick : mJoysticks) {
                if (joystick.getBounds().contains((int)pointerX, (int)pointerY)) {
                    joystick.onPointerDown(pointerId, pointerX, pointerY);
                    isProcessed = true;
                    break;
                }
            }

            for (InputOverlayButton button : mButtons) {
                if (button.getBounds().contains((int)pointerX, (int)pointerY)) {
                    button.onPointerDown(pointerId, pointerX, pointerY);
                    isProcessed = true;
                }
            }

            for (InputOverlayDpad dpad : mDpads) {
                if (dpad.getBounds().contains((int)pointerX, (int)pointerY)) {
                    dpad.onPointerDown(pointerId, pointerX, pointerY);
                    isProcessed = true;
                }
            }

            if (!isProcessed && mOverlayPointer != null && mOverlayPointer.getPointerId() == -1)
                mOverlayPointer.onPointerDown(pointerId, pointerX, pointerY);
            break;
        }
        case MotionEvent.ACTION_MOVE: {
            int pointerCount = event.getPointerCount();
            for (int i = 0; i < pointerCount; ++i) {
                boolean isCaptured = false;
                int pointerId = event.getPointerId(i);
                float pointerX = event.getX(i);
                float pointerY = event.getY(i);

                for (InputOverlayJoystick joystick : mJoysticks) {
                    if (joystick.getPointerId() == pointerId) {
                        joystick.onPointerMove(pointerId, pointerX, pointerY);
                        isCaptured = true;
                        isProcessed = true;
                        break;
                    }
                }
                if (isCaptured)
                    continue;

                for (InputOverlayButton button : mButtons) {
                    if (button.getBounds().contains((int)pointerX, (int)pointerY)) {
                        if (button.getPointerId() == -1) {
                            button.onPointerDown(pointerId, pointerX, pointerY);
                            isProcessed = true;
                        }
                    } else if (button.getPointerId() == pointerId) {
                        button.onPointerUp(pointerId, pointerX, pointerY);
                        isProcessed = true;
                    }
                }

                for (InputOverlayDpad dpad : mDpads) {
                    if (dpad.getPointerId() == pointerId) {
                        dpad.onPointerMove(pointerId, pointerX, pointerY);
                        isProcessed = true;
                    }
                }

                if (mOverlayPointer != null && mOverlayPointer.getPointerId() == pointerId) {
                    mOverlayPointer.onPointerMove(pointerId, pointerX, pointerY);
                }
            }
            break;
        }

        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP: {
            int pointerIndex = event.getActionIndex();
            int pointerId = event.getPointerId(pointerIndex);
            float pointerX = event.getX(pointerIndex);
            float pointerY = event.getY(pointerIndex);

            if (mOverlayPointer != null && mOverlayPointer.getPointerId() == pointerId) {
                mOverlayPointer.onPointerUp(pointerId, pointerX, pointerY);
            }

            for (InputOverlayJoystick joystick : mJoysticks) {
                if (joystick.getPointerId() == pointerId) {
                    joystick.onPointerUp(pointerId, pointerX, pointerY);
                    isProcessed = true;
                    break;
                }
            }

            for (InputOverlayButton button : mButtons) {
                if (button.getPointerId() == pointerId) {
                    button.onPointerUp(pointerId, pointerX, pointerY);
                    isProcessed = true;
                }
            }

            for (InputOverlayDpad dpad : mDpads) {
                if (dpad.getPointerId() == pointerId) {
                    dpad.onPointerUp(pointerId, pointerX, pointerY);
                    isProcessed = true;
                }
            }
            break;
        }

        case MotionEvent.ACTION_CANCEL: {
            isProcessed = true;
            if (mOverlayPointer != null) {
                mOverlayPointer.onPointerUp(0, 0, 0);
            }

            for (InputOverlayJoystick joystick : mJoysticks) {
                joystick.onPointerUp(0, 0, 0);
            }

            for (InputOverlayButton button : mButtons) {
                button.onPointerUp(0, 0, 0);
            }

            for (InputOverlayDpad dpad : mDpads) {
                dpad.onPointerUp(0, 0, 0);
            }
            break;
        }
        }

        if (isProcessed)
            invalidate();

        return true;
    }

    public boolean onTouchWhileEditing(MotionEvent event) {
        int pointerIndex = event.getActionIndex();
        int pointerX = (int)event.getX(pointerIndex);
        int pointerY = (int)event.getY(pointerIndex);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            if (mButtonBeingConfigured != null || mDpadBeingConfigured != null ||
                mJoystickBeingConfigured != null)
                return false;
            for (InputOverlayButton button : mButtons) {
                if (mButtonBeingConfigured == null &&
                    button.getBounds().contains(pointerX, pointerY)) {
                    mButtonBeingConfigured = button;
                    mButtonBeingConfigured.onConfigureBegin(pointerX, pointerY);
                    return true;
                }
            }

            for (InputOverlayDpad dpad : mDpads) {
                if (mDpadBeingConfigured == null && dpad.getBounds().contains(pointerX, pointerY)) {
                    mDpadBeingConfigured = dpad;
                    mDpadBeingConfigured.onConfigureBegin(pointerX, pointerY);
                    return true;
                }
            }

            for (InputOverlayJoystick joystick : mJoysticks) {
                if (mJoystickBeingConfigured == null &&
                    joystick.getBounds().contains(pointerX, pointerY)) {
                    mJoystickBeingConfigured = joystick;
                    mJoystickBeingConfigured.onConfigureBegin(pointerX, pointerY);
                    return true;
                }
            }
            break;
        case MotionEvent.ACTION_MOVE:
            if (mButtonBeingConfigured != null) {
                mButtonBeingConfigured.onConfigureMove(pointerX, pointerY);
                invalidate();
                return true;
            }
            if (mDpadBeingConfigured != null) {
                mDpadBeingConfigured.onConfigureMove(pointerX, pointerY);
                invalidate();
                return true;
            }
            if (mJoystickBeingConfigured != null) {
                mJoystickBeingConfigured.onConfigureMove(pointerX, pointerY);
                invalidate();
                return true;
            }
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            if (mButtonBeingConfigured != null) {
                saveControlPosition(mButtonBeingConfigured.getButtonId(),
                                    mButtonBeingConfigured.getBounds());
                mButtonBeingConfigured = null;
                return true;
            }
            if (mDpadBeingConfigured != null) {
                saveControlPosition(mDpadBeingConfigured.getButtonId(),
                                    mDpadBeingConfigured.getBounds());
                mDpadBeingConfigured = null;
                return true;
            }
            if (mJoystickBeingConfigured != null) {
                saveControlPosition(mJoystickBeingConfigured.getButtonId(),
                                    mJoystickBeingConfigured.getBounds());
                mJoystickBeingConfigured = null;
                return true;
            }
            break;
        }

        return false;
    }

    private void saveControlPosition(int buttonId, Rect bounds) {
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        SharedPreferences.Editor sPrefsEditor = mPreferences.edit();
        float x =
            (bounds.left + (bounds.right - bounds.left) / 2.0f) / dm.widthPixels * 2.0f - 1.0f;
        float y =
            (bounds.top + (bounds.bottom - bounds.top) / 2.0f) / dm.heightPixels * 2.0f - 1.0f;
        sPrefsEditor.putFloat(buttonId + (mIsLandscape ? "_XX" : "_X"), x);
        sPrefsEditor.putFloat(buttonId + (mIsLandscape ? "_YY" : "_Y"), y);
        sPrefsEditor.apply();
    }

    public void refreshControls() {
        // Remove all the overlay buttons
        mIsLandscape =
            getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        mButtons.clear();
        mDpads.clear();
        mJoysticks.clear();

        int i = 0;
        int[][] buttons = new int[][] {
            {ButtonType.N3DS_BUTTON_A, R.drawable.classic_a, R.drawable.classic_a_pressed},
            {ButtonType.N3DS_BUTTON_B, R.drawable.classic_b, R.drawable.classic_b_pressed},
            {ButtonType.N3DS_BUTTON_X, R.drawable.classic_x, R.drawable.classic_x_pressed},
            {ButtonType.N3DS_BUTTON_Y, R.drawable.classic_y, R.drawable.classic_y_pressed},
            {ButtonType.N3DS_BUTTON_START, R.drawable.gcpad_start, R.drawable.gcpad_start_pressed},
            {ButtonType.N3DS_BUTTON_SELECT, R.drawable.wiimote_plus,
             R.drawable.wiimote_plus_pressed},
            {ButtonType.N3DS_BUTTON_L, R.drawable.classic_l, R.drawable.classic_l_pressed},
            {ButtonType.N3DS_BUTTON_R, R.drawable.classic_r, R.drawable.classic_r_pressed},
        };

        for (; i < buttons.length; ++i) {
            int id = buttons[i][0];
            int normal = buttons[i][1];
            int pressed = buttons[i][2];
            mButtons.add(initializeButton(normal, pressed, id));
        }

        // mDpads.add(initializeDpad(ButtonType.N3DS_CPAD_X));
        mDpads.add(initializeDpad(ButtonType.N3DS_DPAD_UP));
        mJoysticks.add(initializeJoystick(ButtonType.N3DS_CPAD_X));
        mOverlayPointer = new InputOverlayPointer();
    }

    private InputOverlayButton initializeButton(int defaultResId, int pressedResId, int buttonId) {
        final Resources res = getResources();
        final DisplayMetrics dm = res.getDisplayMetrics();
        float scale = 0.14f * (sControllerScale + 50) / 100;

        switch (buttonId) {
        case ButtonType.N3DS_BUTTON_L:
        case ButtonType.N3DS_BUTTON_R:
            scale *= 1.7f;
            break;
        case ButtonType.N3DS_BUTTON_ZL:
        case ButtonType.N3DS_BUTTON_ZR:
            scale *= 1.2f;
            break;

        case ButtonType.N3DS_BUTTON_START:
        case ButtonType.N3DS_BUTTON_SELECT:
            scale *= 0.8f;
            break;
        }

        Bitmap defaultBitmap = resizeBitmap(BitmapFactory.decodeResource(res, defaultResId), scale);
        Bitmap pressedBitmap = resizeBitmap(BitmapFactory.decodeResource(res, pressedResId), scale);
        InputOverlayButton overlay =
            new InputOverlayButton(new BitmapDrawable(res, defaultBitmap),
                                   new BitmapDrawable(res, pressedBitmap), buttonId);

        // The X and Y coordinates of the InputOverlayDrawableButton on the InputOverlay.
        // These were set in the input overlay configuration menu.
        float x = mPreferences.getFloat(buttonId + (mIsLandscape ? "_XX" : "_X"), 0f);
        float y = mPreferences.getFloat(buttonId + (mIsLandscape ? "_YY" : "_Y"), 0.5f);

        int width = defaultBitmap.getWidth();
        int height = defaultBitmap.getHeight();
        int drawableX = (int)((dm.widthPixels / 2.0f) * (1.0f + x) - width / 2.0f);
        int drawableY = (int)((dm.heightPixels / 2.0f) * (1.0f + y) - height / 2.0f);
        // Now set the bounds for the InputOverlayDrawableButton.
        // This will dictate where on the screen (and the what the size) the
        // InputOverlayDrawableButton will be.
        overlay.setBounds(new Rect(drawableX, drawableY, drawableX + width, drawableY + height));

        // Need to set the image's position
        overlay.setPosition(drawableX, drawableY);
        overlay.setAlpha((sControllerAlpha * 255) / 100);

        return overlay;
    }

    private InputOverlayDpad initializeDpad(int dpadId) {
        final Resources res = getResources();
        final DisplayMetrics dm = res.getDisplayMetrics();
        final int defaultResId = R.drawable.gcwii_dpad;
        final int pressedOneDirectionResId = R.drawable.gcwii_dpad_pressed_one_direction;
        final int pressedTwoDirectionsResId = R.drawable.gcwii_dpad_pressed_two_directions;
        float scale = 0.3f * (sControllerScale + 50) / 100;

        Bitmap defaultBitmap = resizeBitmap(BitmapFactory.decodeResource(res, defaultResId), scale);
        Bitmap onePressedBitmap =
            resizeBitmap(BitmapFactory.decodeResource(res, pressedOneDirectionResId), scale);
        Bitmap twoPressedBitmap =
            resizeBitmap(BitmapFactory.decodeResource(res, pressedTwoDirectionsResId), scale);
        InputOverlayDpad overlay = new InputOverlayDpad(
            new BitmapDrawable(res, defaultBitmap), new BitmapDrawable(res, onePressedBitmap),
            new BitmapDrawable(res, twoPressedBitmap), dpadId);

        // The X and Y coordinates of the InputOverlayDrawableDpad on the InputOverlay.
        // These were set in the input overlay configuration menu.
        float x = mPreferences.getFloat(dpadId + (mIsLandscape ? "_XX" : "_X"), 0f);
        float y = mPreferences.getFloat(dpadId + (mIsLandscape ? "_YY" : "_Y"), 0.5f);

        int width = defaultBitmap.getWidth();
        int height = defaultBitmap.getHeight();
        int drawableX = (int)((dm.widthPixels / 2.0f) * (1.0f + x) - width / 2.0f);
        int drawableY = (int)((dm.heightPixels / 2.0f) * (1.0f + y) - height / 2.0f);
        // Now set the bounds for the InputOverlayDrawableDpad.
        // This will dictate where on the screen (and the what the size) the
        // InputOverlayDrawableDpad will be.
        overlay.setBounds(new Rect(drawableX, drawableY, drawableX + width, drawableY + height));

        // Need to set the image's position
        overlay.setPosition(drawableX, drawableY);
        overlay.setAlpha((sControllerAlpha * 255) / 100);

        return overlay;
    }

    private InputOverlayJoystick initializeJoystick(int joystick) {
        final Resources res = getResources();
        final DisplayMetrics dm = res.getDisplayMetrics();
        final int resOuter = R.drawable.gcwii_joystick_range;
        final int defaultResInner = R.drawable.gcwii_joystick;
        final int pressedResInner = R.drawable.gcwii_joystick_pressed;
        float scale = 0.275f * (sControllerScale + 50) / 100;

        // Initialize the InputOverlayDrawableJoystick.
        final Bitmap bitmapOuter = resizeBitmap(BitmapFactory.decodeResource(res, resOuter), scale);
        final Bitmap bitmapInnerDefault = BitmapFactory.decodeResource(res, defaultResInner);
        final Bitmap bitmapInnerPressed = BitmapFactory.decodeResource(res, pressedResInner);

        // The X and Y coordinates of the InputOverlayDrawableButton on the InputOverlay.
        // These were set in the input overlay configuration menu.
        float x = mPreferences.getFloat(joystick + (mIsLandscape ? "_XX" : "_X"), -0.3f);
        float y = mPreferences.getFloat(joystick + (mIsLandscape ? "_YY" : "_Y"), 0.3f);

        // Now set the bounds for the InputOverlayDrawableJoystick.
        // This will dictate where on the screen (and the what the size) the
        // InputOverlayDrawableJoystick will be.
        float innerScale = 1.833f;
        int outerSize = bitmapOuter.getWidth();
        int drawableX = (int)((dm.widthPixels / 2.0f) * (1.0f + x) - outerSize / 2.0f);
        int drawableY = (int)((dm.heightPixels / 2.0f) * (1.0f + y) - outerSize / 2.0f);
        Rect outerRect =
            new Rect(drawableX, drawableY, drawableX + outerSize, drawableY + outerSize);
        Rect innerRect =
            new Rect(0, 0, (int)(outerSize / innerScale), (int)(outerSize / innerScale));

        // Send the drawableId to the joystick so it can be referenced when saving control position.
        InputOverlayJoystick overlay = new InputOverlayJoystick(
            new BitmapDrawable(res, bitmapOuter), new BitmapDrawable(res, bitmapOuter),
            new BitmapDrawable(res, bitmapInnerDefault),
            new BitmapDrawable(res, bitmapInnerPressed), outerRect, innerRect, joystick);

        // Need to set the image's position
        overlay.setPosition(drawableX, drawableY);
        overlay.setAlpha((sControllerAlpha * 255) / 100);

        return overlay;
    }

    public Bitmap resizeBitmap(Bitmap bitmap, float scale) {
        // Determine the button size based on the smaller screen dimension.
        // This makes sure the buttons are the same size in both portrait and landscape.
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int dimension = (int)(Math.min(dm.widthPixels, dm.heightPixels) * scale);
        return Bitmap.createScaledBitmap(bitmap, dimension, dimension, true);
    }

    public boolean isInEditMode() {
        return mInEditMode;
    }

    public void setInEditMode(boolean mode) {
        mInEditMode = mode;
    }
}
