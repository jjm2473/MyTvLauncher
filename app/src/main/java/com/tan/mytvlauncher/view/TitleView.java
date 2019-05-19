package com.tan.mytvlauncher.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.v17.leanback.widget.TitleViewAdapter;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tan.mytvlauncher.R;

public class TitleView extends LinearLayout implements TitleViewAdapter.Provider {
    private TextView title;
    private ImageView badge;

    public TitleView(Context context) {
        super(context);
    }

    public TitleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TitleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public TitleViewAdapter getTitleViewAdapter() {
        return new TitleViewAdapter() {
            @Override
            public View getSearchAffordanceView() {
                return null;
            }

            @Override
            public void setTitle(CharSequence titleText) {
                title.setText(titleText);
            }

            @Override
            public CharSequence getTitle() {
                return title.getText();
            }

            @Override
            public void setBadgeDrawable(Drawable drawable) {
                badge.setImageDrawable(drawable);
                if (drawable == null) {
                    badge.setVisibility(GONE);
                } else {
                    badge.setVisibility(VISIBLE);
                }
            }

            @Override
            public Drawable getBadgeDrawable() {
                return badge.getDrawable();
            }
        };
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        title = (TextView) this.findViewById(R.id.title_text);
        badge = (ImageView) this.findViewById(R.id.title_badge);
    }
}
