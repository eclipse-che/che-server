package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;

public interface NamespaceConfigurator{
    public void configure(NamespaceResolutionContext namespaceResolutionContext) throws InfrastructureException;
}