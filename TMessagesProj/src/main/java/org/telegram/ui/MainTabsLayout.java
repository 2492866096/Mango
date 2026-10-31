package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;

import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.glass.GlassTabView;

import me.vkryl.android.animator.ListAnimator;

public class MainTabsLayout extends AnimatedLinearLayout {

    public interface ReorderListener {
        void onOrderChanged(int[] order);
        void onLongPressWithoutMove(View tab);
    }

    private final ArrayList<View> orderedTabs = new ArrayList<>();
    private final ArrayList<View> orderBeforeDrag = new ArrayList<>();
    private ReorderListener reorderListener;
    private View draggedTab;
    private boolean dragMoved;
    private boolean childTouchCancelled;
    private float touchX, startX, draggedOffsetX;
    private int pointerId = -1;

    public void setReorderListener(ReorderListener listener) {
        reorderListener = listener;
    }

    /** Child indices are stable identities; priorities control only their visual order. */
    public void setTabOrder(int[] order) {
        if (draggedTab != null) return;
        orderedTabs.clear();
        for (int id : order) {
            if (id >= 0 && id < getChildCount()) {
                View tab = getChildAt(id);
                if (!orderedTabs.contains(tab)) orderedTabs.add(tab);
            }
        }
        for (int id = 0; id < getChildCount(); id++) {
            View tab = getChildAt(id);
            if (!orderedTabs.contains(tab)) orderedTabs.add(tab);
        }
        applyTabPriorities();
    }

    private void applyTabPriorities() {
        for (int i = 0; i < orderedTabs.size(); i++) setPriority(orderedTabs.get(i), i);
        requestLayout();
    }

    public boolean startReordering(View tab) {
        if (tab.getParent() != this || !isViewVisible(tab)) return false;
        // Accessibility invokes long-click without a touch stream.
        if (pointerId == -1) {
            if (reorderListener != null) reorderListener.onLongPressWithoutMove(tab);
            return true;
        }
        draggedTab = tab;
        startX = touchX;
        draggedOffsetX = touchX - tab.getX();
        dragMoved = childTouchCancelled = false;
        orderBeforeDrag.clear();
        orderBeforeDrag.addAll(orderedTabs);
        tab.setTranslationZ(dp(8));
        tab.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pointerId = event.getPointerId(0);
            touchX = event.getX();
        }
        int pointerIndex = event.findPointerIndex(pointerId);
        if (pointerIndex >= 0) touchX = event.getX(pointerIndex);
        if (draggedTab != null) {
            if (!childTouchCancelled) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                childTouchCancelled = true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && pointerIndex >= 0) {
                if (Math.abs(touchX - startX) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) dragMoved = true;
                if (dragMoved) moveDraggedTab();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                finishReordering(false);
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                    || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointerId) {
                finishReordering(true);
            }
            return true;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) pointerId = -1;
        return handled;
    }

    private void moveDraggedTab() {
        int from = orderedTabs.indexOf(draggedTab);
        int target = from;
        float left = getPaddingLeft();
        // Use final layout positions, not animated positions, to avoid oscillation.
        for (int i = 0; i < orderedTabs.size(); i++) {
            View tab = orderedTabs.get(i);
            if (!isViewVisible(tab)) continue;
            float center = left + tab.getMeasuredWidth() / 2f;
            if (i < from && touchX < center) { target = i; break; }
            if (i > from && touchX > center) target = i;
            left += tab.getMeasuredWidth();
        }
        if (target != from) {
            orderedTabs.remove(from);
            orderedTabs.add(target, draggedTab);
            applyTabPriorities();
        }
        applyDragTransform();
    }

    private void applyDragTransform() {
        if (draggedTab == null || !dragMoved) return;
        float left = Math.max(getPaddingLeft(), Math.min(getWidth() - getPaddingRight() - draggedTab.getWidth(), touchX - draggedOffsetX));
        draggedTab.setTranslationX(left - draggedTab.getLeft());
        draggedTab.setScaleX(1.08f);
        draggedTab.setScaleY(1.08f);
    }

    private void finishReordering(boolean cancelled) {
        View tab = draggedTab;
        boolean moved = dragMoved;
        draggedTab = null;
        pointerId = -1;
        if (tab != null) {
            tab.setTranslationZ(0);
            tab.setScaleX(1);
            tab.setScaleY(1);
        }
        if (cancelled) {
            orderedTabs.clear();
            orderedTabs.addAll(orderBeforeDrag);
        }
        // A drag inside the original slot may not change ListAnimator's target,
        // so explicitly return the tab to its slot instead of waiting for a frame.
        if (tab != null) {
            float left = getPaddingLeft();
            for (View candidate : orderedTabs) {
                if (!isViewVisible(candidate)) continue;
                if (candidate == tab) {
                    tab.setTranslationX(left - tab.getLeft());
                    break;
                }
                left += candidate.getMeasuredWidth();
            }
        }
        applyTabPriorities();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        if (!cancelled && reorderListener != null) {
            if (moved) {
                int[] order = new int[orderedTabs.size()];
                for (int i = 0; i < order.length; i++) order[i] = indexOfChild(orderedTabs.get(i));
                reorderListener.onOrderChanged(order);
            } else {
                reorderListener.onLongPressWithoutMove(tab);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (draggedTab != null) finishReordering(true);
        super.onDetachedFromWindow();
    }

    public MainTabsLayout(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = height - getPaddingTop() - getPaddingBottom();

        measureTabTexts();

        if (visibleChildCount == 0) {
            setMeasuredDimension(getPaddingLeft() + getPaddingRight(), height);
            return;
        }

        final int maxTotalWidthForTabs = width - getPaddingLeft() - getPaddingRight();
        final int minTotalWidthForTabs = Math.min(dp(320), maxTotalWidthForTabs);
        final int tabPadding = dp(16);

        final int minTabTextWidthIfEq = (minTotalWidthForTabs / visibleChildCount) - tabPadding * 2;
        final int maxTabTextWidthIfEq = (maxTotalWidthForTabs / visibleChildCount) - tabPadding * 2;


        float totalWidth = 0;
        int totalWeight = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = tabsTextWidthWithMargin[a] = 0;
                tabsWeight[a] = 0;
                continue;
            }

            final float w = tabsTextWidth[a];
            if (w > maxTabTextWidthIfEq) {
                tabsTextWidthWithMargin[a] = tabsTextWidth[a] + dp(13) * 2;
            } else {
                tabsTextWidthWithMargin[a] = tabsTextWidth[a] + dp(16) * 2;
            }
            tabsWeight[a] = tabsTextWidthWithMargin[a] > (maxTabTextWidthIfEq + dp(16) * 2) ? 0 : 1;

            totalWidth += tabsTextWidthWithMargin[a];
            totalWeight += tabsWeight[a];
        }

        if (totalWeight == 0) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsWeight[a] = isViewVisible(getChildAt(a)) ? 1 : 0;
            }
            totalWeight = visibleChildCount;
        }

        if (totalWidth > maxTotalWidthForTabs) {
            final float m = maxTotalWidthForTabs / totalWidth;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] *= m;
            }
        } else if (totalWidth < minTotalWidthForTabs) {
            final float growW = minTotalWidthForTabs - totalWidth;
            final float growP = growW / totalWeight;

            //boolean needStage2 = false;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final float maxGrow = maxTabTextWidthIfEq - tabsTextWidthWithMargin[a];
                //if (tabsWeight[a] > 0 && growP * tabsWeight[a] > maxGrow) {
                //    needStage2 = true;
                //    tabsTextWidthWithMargin[a] = maxTabTextWidthIfEq;
                //} else {
                    tabsTextWidthWithMargin[a] += growP * tabsWeight[a];
                //}
            }

            /*if (needStage2) {
                totalWidth = 0;
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    totalWidth += tabsTextWidthWithMargin[a];
                }

                final float m = minTotalWidthForTabs / totalWidth;
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    tabsTextWidthWithMargin[a] *= m;
                }
            }*/
        }

        int l = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            if (!isViewVisible(getChildAt(a))) {
                continue;
            }

            tabsWidth[a] = Math.round(tabsTextWidthWithMargin[a]);
            tabsLeftPos[a] = l;
            l += tabsWidth[a];
        }

        setMeasuredDimension(l + getPaddingLeft() + getPaddingRight(), height);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.measure(
                MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }





/*
        if (biggestTabTextWidth > maxTabTextWidthIfEq) {
            setMeasuredDimension(0, 0);

        } else {
            // all tabs have equal width

            final int tabWidth = (Math.max(biggestTabTextWidth, minTabTextWidthIfEq) + tabPadding * 2);
            final int measuredWidth = tabWidth * visibleChildCount + getPaddingLeft() + getPaddingRight();
            setMeasuredDimension(measuredWidth, height);

            int index = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);

                child.measure(
                    MeasureSpec.makeMeasureSpec(tabWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));

                if (child.getVisibility() != View.VISIBLE) {
                    continue;
                }

                tabsLeftPos[a] = (tabWidth * index);
                index++;
            }
        }
 */
        calculateTotalSizesAfterMeasure();
    }

    /*
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        visibleHolders.clear();
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            final Holder holder = viewHolders.get(view);

            final int top = getPaddingTop();
            final int left = getPaddingLeft() + tabsLeftPos[a];
            view.layout(left, top, left + view.getMeasuredWidth(), top + view.getMeasuredHeight());

            if (view.getVisibility() == VISIBLE && holder != null && holder.isVisible) {
                visibleHolders.add(holder);
                holder.hasInAnimator = true;

                Log.i("LIST_DEBUG", "show item: " + a);
            }
        }

        listAnimator.reset(visibleHolders, true);
        checkViewsVisibility();
    }
    */



    public interface Tab {
        float measureTextWidth();
    }



    // fills tabsTextWidth[] and return visible child count;

    private float[] tabsTextWidth;
    private float[] tabsTextWidthWithMargin;
    private int[] tabsWeight;
    private int[] tabsWidth;

    private int[] tabsLeftPos;


    private int visibleChildCount;
    private int biggestTabTextWidth;

    private void measureTabTexts() {
        final int childCount = getChildCount();
        if (tabsTextWidth == null || tabsTextWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsLeftPos = new int[childCount];
            tabsWidth = new int[childCount];
        }

        float maxTabWidthF = 0;
        int index = 0;

        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = -1;
                continue;
            }

            final float tabWidth;
            if (child instanceof MainTabsLayout.Tab) {
                tabWidth = ((MainTabsLayout.Tab) child).measureTextWidth();
            } else {
                tabWidth = 0;
            }

            tabsTextWidth[a] = tabWidth;
            maxTabWidthF = Math.max(maxTabWidthF, tabWidth);
            index++;
        }

        biggestTabTextWidth = (int) Math.ceil(maxTabWidthF);
        visibleChildCount = index;
    }

    @Override
    protected void setChildVisibilityFactor(View view, float factor) {
        final float s = lerp(0.7f, 1f, factor);
        view.setAlpha(factor);
        view.setScaleX(s);
        view.setScaleY(s);
        if (view == draggedTab) applyDragTransform();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkVisualWidth();
    }

    @Override
    protected void onItemsChanged() {
        super.onItemsChanged();
        checkVisualWidth();
    }

    private void checkVisualWidth() {
        for (int a = 0, N = getEntriesCount(); a < N; a++) {
            final ListAnimator.Entry<Holder> entry = getEntry(a);
            final float width = entry.getRectF().width();
            ((GlassTabView) entry.item.view).setVisualWidth(width);
        }
    }
}
