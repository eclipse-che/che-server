/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.azure.devops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Azure DevOps Server user's identity. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureDevOpsServerUserIdentity {
  private String accountName;
  private String mailAddress;

  public String getAccountName() {
    return accountName;
  }

  @JsonProperty("AccountName")
  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getMailAddress() {
    return mailAddress;
  }

  @JsonProperty("MailAddress")
  public void setMailAddress(String mailAddress) {
    this.mailAddress = mailAddress;
  }

  @Override
  public String toString() {
    return "AzureDevOpsServerUserIdentity{"
        + "accountName='"
        + accountName
        + '\''
        + ", mailAddress='"
        + mailAddress
        + '\''
        + '}';
  }
}
