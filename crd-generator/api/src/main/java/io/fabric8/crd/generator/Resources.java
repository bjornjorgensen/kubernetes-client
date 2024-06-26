/*
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.crd.generator;

import io.fabric8.crd.generator.decorator.Decorator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Resources {
  private static final Logger LOGGER = LoggerFactory.getLogger(Resources.class);
  private static final int SORT_ROUND_LIMIT = 10;

  private final KubernetesListBuilder global = new KubernetesListBuilder();
  private final Set<Decorator<?>> globalDecorators = new TreeSet<>();

  /**
   * Get the global builder
   *
   * @return The groups map.
   */
  public KubernetesListBuilder global() {
    return this.global;
  }

  /**
   * Get the Decorator Set.
   * The method is visible for testing purposes.
   *
   * @return the Set of registed Decorators.
   */
  protected Set<Decorator<?>> getDecorators() {
    return globalDecorators;
  }

  /**
   * Add a {@link Decorator}.
   *
   * @param decorator The decorator.
   */
  public void decorate(Decorator<?> decorator) {
    globalDecorators.add(decorator);
  }

  /**
   * Add a resource to all groups.
   *
   * @param metadata the resource to add to this Resources
   */
  public void add(HasMetadata metadata) {
    global.addToItems(metadata);
  }

  /**
   * Generate all resources.
   *
   * @return A map of {@link KubernetesList} by group name.
   */
  public KubernetesList generate() {
    for (Decorator<?> decorator : applyConstraints(globalDecorators)) {
      this.global.accept(decorator);
    }
    return this.global.build();
  }

  public List<Decorator<?>> applyConstraints(Set<Decorator<?>> decorators) {
    Decorator<?>[] array = decorators.toArray(new Decorator<?>[0]);
    // We can't guarantee that `when `decorator a < b and b < c then a < c``.
    // Why?
    // Because our comparators express constraints on particular pairs and can't express the global order.
    // So, in order to be accurate we need to compare each decorator, with ALL OTHER decorators.
    // In other words we need bubble sort.
    // We also might need it more than once. So, we'll do it as many times as we have to, till there are not more transformations.
    // But hey, let's have an upper limit just to prevent infinite loops.
    for (int i = 0; i < SORT_ROUND_LIMIT && bubbleSort(array); i++) {
      LOGGER.debug("Sorting again: {}", i + 1);
    }

    List<Decorator<?>> result = Collections.unmodifiableList(Arrays.asList(array));

    if (LOGGER.isTraceEnabled()) {
      result.forEach(decorator -> LOGGER.trace("{}", decorator));
    }

    return result;
  }

  /**
   * Bubble sort for decorators.
   *
   * @param decorators the {@link Decorator} array to be sorted
   */
  private boolean bubbleSort(Decorator<?>[] decorators) {
    boolean swapped = false;
    int n = decorators.length;
    Decorator<?> temp;
    for (int i = 0; i < n; i++) {
      for (int j = 1; j < (n - i); j++) {
        if (decorators[j].compareTo(decorators[j - 1]) < 0) {
          swapped = true;
          temp = decorators[j - 1];
          decorators[j - 1] = decorators[j];
          decorators[j] = temp;
        }
      }
    }
    return swapped;
  }
}
