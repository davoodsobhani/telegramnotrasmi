package ir.mehdi.ui.Components;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;

import ir.mehdi.messenger.AndroidUtilities;

public class CorrectlyMeasuringTextView extends TextView{

    public CorrectlyMeasuringTextView(Context context) {
        super(context);

        setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf")); // telegraf
    }

    public CorrectlyMeasuringTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf")); // telegraf
    }

    public CorrectlyMeasuringTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf")); // telegraf
    }

    public void onMeasure(int wms, int hms) {
        super.onMeasure(wms, hms);
        try {
            Layout l = getLayout();
            if (l.getLineCount() <= 1) return;
            int maxw = 0;
            for (int i = l.getLineCount() - 1; i >= 0; --i) {
                maxw = Math.max(maxw, Math.round(l.getPaint().measureText(getText(), l.getLineStart(i), l.getLineEnd(i))));
            }
            super.onMeasure(Math.min(maxw + getPaddingLeft() + getPaddingRight(), getMeasuredWidth()) | MeasureSpec.EXACTLY, getMeasuredHeight() | MeasureSpec.EXACTLY);
        } catch (Exception x) {
        }
    }
}