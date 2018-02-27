/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package ir.mehdi.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import ir.mehdi.messenger.AndroidUtilities;
import ir.mehdi.messenger.LocaleController;
import ir.mehdi.ui.Components.LayoutHelper;

public class TextColorThemeCell extends FrameLayout {

    private TextView textView;
    private boolean needDivider;
    private int currentColor;
    private float alpha = 1.0f;

    private static Paint colorPaint;

    public TextColorThemeCell(Context context) {
        super(context);

        if (colorPaint == null) {
            colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        textView = new TextView(context);
        textView.setTextColor(0xff212121);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf")); // telegraf
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setPadding(0, 0, 0, AndroidUtilities.dp(3));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 17 : 17 + 36), 0, (LocaleController.isRTL ? 17 + 36 : 17), 0));
    }

    @Override
    public void setAlpha(float value) {
        alpha = value;
        invalidate();
    }

    @Override
    public float getAlpha() {
        return alpha;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setTextAndColor(String text, int color) {
        textView.setText(text);
        currentColor = color;
        setWillNotDraw(!needDivider && currentColor == 0);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentColor != 0) {
            colorPaint.setColor(currentColor);
            colorPaint.setAlpha((int) (255 * alpha));
            canvas.drawCircle(!LocaleController.isRTL ? AndroidUtilities.dp(28) : getMeasuredWidth() - AndroidUtilities.dp(28 + 20), getMeasuredHeight() / 2, AndroidUtilities.dp(10), colorPaint);
        }
    }
}
