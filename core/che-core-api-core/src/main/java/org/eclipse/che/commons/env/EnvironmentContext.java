/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.commons.env;

import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.concurrent.ThreadLocalPropagateContext;
import org.eclipse.che.commons.subject.Subject;

/**
 * Defines a component that holds variables of type {@link ThreadLocal} whose value is required by
 * the component to work normally and cannot be recovered. This component is mainly used when we
 * want to do a task asynchronously, in that case to ensure that the task will be executed in the
 * same conditions as if it would be executed synchronously we need to transfer the thread context
 * from the original thread to the executor thread.
 */
public class EnvironmentContext {

  /** ThreadLocal keeper for EnvironmentContext. */
  private static ThreadLocal<EnvironmentContext> current =
      ThreadLocal.withInitial(EnvironmentContext::new);

  static {
    ThreadLocalPropagateContext.addThreadLocal(current);
  }

  public static EnvironmentContext getCurrent() {
    return current.get();
  }

  public static void setCurrent(EnvironmentContext environment) {
    current.set(environment);
  }

  public static void reset() {
    current.remove();
  }

  private Subject subject;
  private String namespaceName;

  public EnvironmentContext() {}

  public EnvironmentContext(EnvironmentContext other) {
    setSubject(other.getSubject());
    setNamespaceName(other.getNamespaceName());
  }

  /** Returns subject or {@link Subject#ANONYMOUS} in case when subject is null. */
  public Subject getSubject() {
    return subject == null ? Subject.ANONYMOUS : subject;
  }

  /** Sets subject. */
  public void setSubject(Subject subject) {
    this.subject = subject;
  }

  /**
   * Returns the namespace the current task acts in, or {@code null} when it does not say.
   *
   * <p>A request is served in the namespace its user resolves to, so nothing has to name it. A
   * background job of the Che server works through the namespaces of other users, whose names it
   * already knows and cannot resolve from a subject of its own, and names the one it is on here.
   */
  @Nullable
  public String getNamespaceName() {
    return namespaceName;
  }

  /** Sets the namespace the current task acts in. */
  public void setNamespaceName(@Nullable String namespaceName) {
    this.namespaceName = namespaceName;
  }
}
