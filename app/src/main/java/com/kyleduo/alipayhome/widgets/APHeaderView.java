package com.kyleduo.alipayhome.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.kyleduo.alipayhome.R;
import com.kyleduo.alipayhome.widgets.support.ATHeaderBehavior;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * Created by kyleduo on 2017/7/11.
 */
@CoordinatorLayout.DefaultBehavior(APHeaderView.Behavior.class)
//@CoordinatorLayout.DefaultBehavior(ATHeaderBehavior.class)
public class APHeaderView extends ViewGroup {
    private static final int PENDING_ACTION_COLLAPSED = 0x0001;
    private static final int PENDING_ACTION_EXPANDED = 0x0010;
    private static final int PENDING_ACTION_ANIMATED = 0x0010;
    /**
     * title
     */
    private View mBar;
    /**
     * mid
     */
    private View mSnapView;
    private List<View> mScrollableViews;
    private List<OnOffsetChangeListener> mOnOffsetChangeListeners;
    private OnHeaderFlingUnConsumedListener mOnHeaderFlingUnConsumedListener;

    private int mPendingAction;

    public APHeaderView(@NonNull Context context) {
        this(context, null);
    }

    public APHeaderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public View getBar() {
        return mBar;
    }

    public View getSnapView() {
        return mSnapView;
    }

    public List<View> getScrollableViews() {
        return mScrollableViews;
    }

    /**
     * @return snap+linearlayout 的高度
     */
    public int getScrollRange() {
        int range = mSnapView.getMeasuredHeight();
        if (mScrollableViews != null) {
            for (View sv : mScrollableViews) {
                range += sv.getMeasuredHeight();
            }
        }
        return range;
    }

    private int getSnapRange() {
        return mSnapView.getHeight();
    }

    /**
     * 自动收起snapView的offset阈值
     *
     * @return
     */
    private int getCollapseSnapOffset() {
        return mSnapView.getHeight();
    }

    public Behavior getBehavior() {
        LayoutParams lp = getLayoutParams();
        if (lp != null && lp instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams clp = (CoordinatorLayout.LayoutParams) lp;
            CoordinatorLayout.Behavior b = clp.getBehavior();
            if (b instanceof Behavior) {
                return (Behavior) b;
            }
            return null;
        }
        return null;
    }

    public void setExpanded(boolean expanded) {
        mPendingAction = expanded ? PENDING_ACTION_EXPANDED : PENDING_ACTION_COLLAPSED;
        requestLayout();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final int childCount = getChildCount();
        if (childCount < 2) {
            throw new IllegalStateException("Child count must >= 2");
        }
        mBar = findViewById(R.id.alipay_bar);
        mSnapView = findViewById(R.id.alipay_snap);
        mScrollableViews = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            if (v != mBar && v != mSnapView) {
                mScrollableViews.add(v);
            }
        }
        mBar.bringToFront();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int height = 0;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View c = getChildAt(i);
            measureChildren(widthMeasureSpec,heightMeasureSpec);
            height += c.getMeasuredHeight();
        }
        setMeasuredDimension(widthSize,height);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(super.generateDefaultLayoutParams());
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(super.generateLayoutParams(attrs));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childTop = getPaddingTop();
        int childLeft = getPaddingLeft();
        mBar.layout(childLeft, childTop, childLeft + mBar.getMeasuredWidth(), childTop + mBar.getMeasuredHeight());
        childTop += mBar.getMeasuredHeight();

        mSnapView.layout(childLeft, childTop, childLeft + mSnapView.getMeasuredWidth(), childTop + mSnapView.getMeasuredHeight());

        childTop += mSnapView.getMeasuredHeight();
        // 那就剩一个LinearLayout 了
        for (View sv : mScrollableViews) {
            sv.layout(childLeft, childTop, childLeft + sv.getMeasuredWidth(), childTop + sv.getMeasuredHeight());
            childTop += sv.getMeasuredHeight();
        }
    }

    public void addOnOffsetChangeListener(OnOffsetChangeListener listener) {
        if (mOnOffsetChangeListeners == null) {
            mOnOffsetChangeListeners = new ArrayList<>();
        }
        if (mOnOffsetChangeListeners.contains(listener)) {
            return;
        }
        mOnOffsetChangeListeners.add(listener);
    }

    public void removeOnOffsetChangeListener(OnOffsetChangeListener listener) {
        if (mOnOffsetChangeListeners == null || mOnOffsetChangeListeners.size() == 0) {
            return;
        }
        if (mOnOffsetChangeListeners.contains(listener)) {
            mOnOffsetChangeListeners.remove(listener);
        }
    }

    private void dispatchOffsetChange(int offset) {
        if (mOnOffsetChangeListeners != null) {
            for (OnOffsetChangeListener listener : mOnOffsetChangeListeners) {
                listener.onOffsetChanged(this, offset);
            }
        }
    }

    public void setOnHeaderFlingUnConsumedListener(OnHeaderFlingUnConsumedListener onHeaderFlingUnConsumedListener) {
        mOnHeaderFlingUnConsumedListener = onHeaderFlingUnConsumedListener;
    }

    public interface OnOffsetChangeListener {
        void onOffsetChanged(APHeaderView header, int currOffset);
    }

    public interface OnHeaderFlingUnConsumedListener {
        int onFlingUnConsumed(APHeaderView header, int targetOffset, int unconsumed);
    }

    public static class Behavior extends ATHeaderBehavior<APHeaderView> {
        private ValueAnimator mOffsetAnimator;
        private int mTempFlingDispatchConsumed;

        private boolean mSkipNestedPreScroll;
        private WeakReference<View> mLastNestedScrollingChildRef;
        private boolean mWasFlung;
        private boolean mShouldDispatchFling;
        private int mTempFlingMinOffset;
        private int mTempFlingMaxOffset;


        @Override
        protected boolean canDragView(APHeaderView view) {
            return true;
        }

        /**
         * @return filing 状态下的最小移动距离
         */
        @Override
        protected int getScrollRangeForDragFling(APHeaderView view) {
            return view.getScrollRange();
        }

        /**
         * 重写（覆盖）这个方法最重要的目的是两个listener的调用,这两个接口都在 mBar 和 mSnap 中调用.
         * @param parent
         * @param header
         * @param newOffset mScroller.getCurrY();
         * @param minOffset 没啥用
         * @param maxOffset 没啥用
         * @return
         */
        @Override
        public int setHeaderTopBottomOffset(CoordinatorLayout parent, APHeaderView header, int newOffset, int minOffset, int maxOffset) {
            final int curOffset = getTopAndBottomOffset();
            final int min;
            final int max;
            if (mShouldDispatchFling) {
                min = Math.max(mTempFlingMinOffset, minOffset);
                max = Math.min(mTempFlingMaxOffset, maxOffset);
            } else {
                min = minOffset;
                max = maxOffset;
            }
            int consumed = super.setHeaderTopBottomOffset(parent, header, newOffset, min, max);
            // consumed 的符号和 dy 相反
            header.dispatchOffsetChange(getTopAndBottomOffset());

            int delta = 0;

            if (mShouldDispatchFling && header.mOnHeaderFlingUnConsumedListener != null) {
                int unconsumedY = newOffset - curOffset + consumed - mTempFlingDispatchConsumed;
                if (unconsumedY != 0) {
                    delta = header.mOnHeaderFlingUnConsumedListener.onFlingUnConsumed(header, newOffset, unconsumedY);
                }
                mTempFlingDispatchConsumed += -delta;


            }

            return consumed + delta;
        }
    }
}

