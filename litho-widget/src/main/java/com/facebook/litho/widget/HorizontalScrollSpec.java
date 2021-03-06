/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho.widget;

import static com.facebook.litho.SizeSpec.EXACTLY;
import static com.facebook.litho.SizeSpec.UNSPECIFIED;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.util.Pools.SynchronizedPool;
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.ComponentLayout;
import com.facebook.litho.ComponentTree;
import com.facebook.litho.LithoView;
import com.facebook.litho.Output;
import com.facebook.litho.R;
import com.facebook.litho.Size;
import com.facebook.litho.SizeSpec;
import com.facebook.litho.StateValue;
import com.facebook.litho.annotations.FromBoundsDefined;
import com.facebook.litho.annotations.FromMeasure;
import com.facebook.litho.annotations.MountSpec;
import com.facebook.litho.annotations.OnBoundsDefined;
import com.facebook.litho.annotations.OnCreateInitialState;
import com.facebook.litho.annotations.OnCreateMountContent;
import com.facebook.litho.annotations.OnLoadStyle;
import com.facebook.litho.annotations.OnMeasure;
import com.facebook.litho.annotations.OnMount;
import com.facebook.litho.annotations.OnUnmount;
import com.facebook.litho.annotations.Prop;
import com.facebook.litho.annotations.PropDefault;
import com.facebook.litho.annotations.ResType;
import com.facebook.litho.annotations.State;
import com.facebook.yoga.YogaDirection;

/**
 * A component that wraps another component and allow it to be horizontally scrollable. It's
 * analogous to a {@link android.widget.HorizontalScrollView}.
 *
 * @uidocs https://fburl.com/HorizontalScroll:e378
 */
@MountSpec
class HorizontalScrollSpec {

  private static final int LAST_SCROLL_POSITION_UNSET = -1;

  @PropDefault static final boolean scrollbarEnabled = true;

  private static final SynchronizedPool<Size> sSizePool =
      new SynchronizedPool<>(2);

  @OnLoadStyle
  static void onLoadStyle(
      ComponentContext c,
      Output<Boolean> scrollbarEnabled) {

    final TypedArray a = c.obtainStyledAttributes(
        R.styleable.HorizontalScroll,
        0);

    for (int i = 0, size = a.getIndexCount(); i < size; i++) {
      final int attr = a.getIndex(i);

      if (attr == R.styleable.HorizontalScroll_android_scrollbars) {
        scrollbarEnabled.set(a.getInt(attr, 0) != 0);
      }
    }

    a.recycle();
  }

  @OnMeasure
  static void onMeasure(
      ComponentContext context,
      ComponentLayout layout,
      int widthSpec,
      int heightSpec,
      Size size,
      @Prop Component contentProps,
      Output<Integer> measuredComponentWidth,
      Output<Integer> measuredComponentHeight) {

    final int measuredWidth;
    final int measuredHeight;

    final Size contentSize = acquireSize();

    // Measure the component with undefined width spec, as the contents of the
    // hscroll have unlimited horizontal space.
    contentProps.measure(context, SizeSpec.makeSizeSpec(0, UNSPECIFIED), heightSpec, contentSize);

    measuredWidth = contentSize.width;
    measuredHeight = contentSize.height;

    releaseSize(contentSize);

    measuredComponentWidth.set(measuredWidth);
    measuredComponentHeight.set(measuredHeight);

    // If size constraints were not explicitly defined, just fallback to the
    // component dimensions instead.
    size.width = SizeSpec.getMode(widthSpec) == UNSPECIFIED
        ? measuredWidth
        : SizeSpec.getSize(widthSpec);
    size.height = measuredHeight;
  }

  @OnBoundsDefined
  static void onBoundsDefined(
      ComponentContext context,
      ComponentLayout layout,
      @Prop Component contentProps,
      @FromMeasure Integer measuredComponentWidth,
      @FromMeasure Integer measuredComponentHeight,
      Output<Integer> componentWidth,
      Output<Integer> componentHeight,
      Output<YogaDirection> layoutDirection) {

    // If onMeasure() has been called, this means the content component already
    // has a defined size, no need to calculate it again.
    if (measuredComponentWidth != null && measuredComponentHeight != null) {
      componentWidth.set(measuredComponentWidth);
      componentHeight.set(measuredComponentHeight);
    } else {
      final int measuredWidth;
      final int measuredHeight;

      Size contentSize = acquireSize();
      contentProps.measure(
          context,
          SizeSpec.makeSizeSpec(0, UNSPECIFIED),
          SizeSpec.makeSizeSpec(layout.getHeight(), EXACTLY),
          contentSize);

      measuredWidth = contentSize.width;
      measuredHeight = contentSize.height;

      releaseSize(contentSize);

      componentWidth.set(measuredWidth);
      componentHeight.set(measuredHeight);
    }

    layoutDirection.set(layout.getResolvedLayoutDirection());
  }

  @OnCreateMountContent
  static HorizontalScrollLithoView onCreateMountContent(Context c) {
    return new HorizontalScrollLithoView(c);
  }

  @OnMount
  static void onMount(
      final ComponentContext context,
      final HorizontalScrollLithoView horizontalScrollLithoView,
      @Prop Component contentProps,
      @Prop(optional = true, resType = ResType.BOOL) boolean scrollbarEnabled,
      @Prop(optional = true) HorizontalScrollEventsController eventsController,
      @State(canUpdateLazily = true) final int lastScrollPosition,
      @FromBoundsDefined int componentWidth,
      @FromBoundsDefined int componentHeight,
      @FromBoundsDefined final YogaDirection layoutDirection) {

    horizontalScrollLithoView.setHorizontalScrollBarEnabled(scrollbarEnabled);
    horizontalScrollLithoView.mount(contentProps, componentWidth, componentHeight);
    final ViewTreeObserver viewTreeObserver = horizontalScrollLithoView.getViewTreeObserver();
    viewTreeObserver.addOnPreDrawListener(
        new ViewTreeObserver.OnPreDrawListener() {
          @Override
          public boolean onPreDraw() {
            horizontalScrollLithoView.getViewTreeObserver().removeOnPreDrawListener(this);

            if (lastScrollPosition == LAST_SCROLL_POSITION_UNSET) {
              if (layoutDirection == YogaDirection.RTL) {
                horizontalScrollLithoView.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
              }
              HorizontalScroll.lazyUpdateLastScrollPosition(
                  context, horizontalScrollLithoView.getScrollX());
            } else {
              horizontalScrollLithoView.setScrollX(lastScrollPosition);
            }

            return true;
          }
        });
    viewTreeObserver.addOnScrollChangedListener(
        new ViewTreeObserver.OnScrollChangedListener() {
          @Override
          public void onScrollChanged() {
            HorizontalScroll.lazyUpdateLastScrollPosition(
                context, horizontalScrollLithoView.getScrollX());
          }
        });

    if (eventsController != null) {
      eventsController.setScrollableView(horizontalScrollLithoView);
    }
  }

  @OnUnmount
  static void onUnmount(
      ComponentContext context,
      HorizontalScrollLithoView mountedView,
      @Prop(optional = true) HorizontalScrollEventsController eventsController) {

    mountedView.unmount();

    if (eventsController != null) {
      eventsController.setScrollableView(null);
    }
  }

  @OnCreateInitialState
  static void onCreateInitialState(
      ComponentContext c,
      StateValue<Integer> lastScrollPosition,
      @Prop(optional = true) Integer initialScrollPosition) {
    lastScrollPosition.set(
        initialScrollPosition == null ? LAST_SCROLL_POSITION_UNSET : initialScrollPosition);
  }

  static class HorizontalScrollLithoView extends HorizontalScrollView {
    private final LithoView mLithoView;

    private int mComponentWidth;
    private int mComponentHeight;

    public HorizontalScrollLithoView(Context context) {
      super(context);
      mLithoView = new LithoView(context);
      addView(mLithoView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      // The hosting component view always matches the component size. This will
      // ensure that there will never be a size-mismatch between the view and the
      // component-based content, which would trigger a layout pass in the
      // UI thread.
      mLithoView.measure(
          MeasureSpec.makeMeasureSpec(mComponentWidth, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(mComponentHeight, MeasureSpec.EXACTLY));

      // The mounted view always gets exact dimensions from the framework.
      setMeasuredDimension(
          MeasureSpec.getSize(widthMeasureSpec),
          MeasureSpec.getSize(heightMeasureSpec));
    }

    void mount(Component component, int width, int height) {
      if (mLithoView.getComponentTree() == null) {
        mLithoView.setComponentTree(
            ComponentTree.create(mLithoView.getComponentContext(), component)
                .incrementalMount(false)
                .build());
      } else {
        mLithoView.setComponent(component);
      }
      mComponentWidth = width;
      mComponentHeight = height;
    }

    void unmount() {
      // Clear all component-related state from the view.
      mLithoView.unbind();
      mComponentWidth = 0;
      mComponentHeight = 0;
    }
  }

  private static Size acquireSize() {
    Size size = sSizePool.acquire();
    if (size == null) {
      size = new Size();
    }

    return size;
  }

  private static void releaseSize(Size size) {
    sSizePool.release(size);
  }
}
