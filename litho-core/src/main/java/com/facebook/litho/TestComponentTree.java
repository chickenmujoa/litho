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

package com.facebook.litho;

import android.os.Looper;
import android.support.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link ComponentTree} for testing purposes. Leverages test classes to create component layouts
 * and exposes additional information useful for testing.
 */
public class TestComponentTree extends ComponentTree {

  public static Builder create(ComponentContext context, Component root) {
    return new Builder(context, root);
  }

  private TestComponentTree(ComponentTree.Builder builder) {
    super(builder);
  }

  public List<Component> getSubComponents() {
    return extractSubComponents(getMainThreadLayoutState().getDiffTree());
  }

  @Override
  protected LayoutState calculateLayoutState(
      @Nullable Object lock,
      ComponentContext context,
      Component root,
      int widthSpec,
      int heightSpec,
      boolean diffingEnabled,
      @Nullable LayoutState previousLayoutState,
      TreeProps treeProps,
      @LayoutState.CalculateLayoutSource int source,
      String extraAttribution) {
    return LayoutState.calculate(
        new TestComponentContext(
            ComponentContext.withComponentTree(new TestComponentContext(context), this),
            new StateHandler()),
        root,
        mId,
        widthSpec,
        heightSpec,
        diffingEnabled,
        previousLayoutState,
        false /* canPrefetchDisplayLists */,
        false /* canCacheDrawingDisplayLists */,
        true /* clipChildren */,
        false /* persistInternalNodeTree */,
        source,
        extraAttribution);
  }

  private static List<Component> extractSubComponents(DiffNode root) {
    if (root == null) {
      return Collections.emptyList();
    }

    final List<Component> output = new ArrayList<>();

    if (root.getChildCount() == 0) {
      if (root.getComponent() != null && root.getComponent() instanceof TestComponent) {
        TestComponent testSubcomponent = (TestComponent) root.getComponent();
        output.add(testSubcomponent.getWrappedComponent());
      }

      return output;
    }

    for (DiffNode child : root.getChildren()) {
      output.addAll(extractSubComponents(child));
    }

    return output;
  }

  public static class Builder extends ComponentTree.Builder {

    private Builder(ComponentContext context, Component root) {
      super(context, root);
    }

    @Override
    public Builder incrementalMount(boolean isEnabled) {
      return (Builder) super.incrementalMount(isEnabled);
    }

    @Override
    public Builder layoutDiffing(boolean enabled) {
      return (Builder) super.layoutDiffing(enabled);
    }

    @Override
    public Builder layoutThreadLooper(Looper looper) {
      return (Builder) super.layoutThreadLooper(looper);
    }

    @Override
    public Builder layoutLock(Object layoutLock) {
      return (Builder) super.layoutLock(layoutLock);
    }

    @Override
    public TestComponentTree build() {

      return new TestComponentTree(this);
    }
  }
}
