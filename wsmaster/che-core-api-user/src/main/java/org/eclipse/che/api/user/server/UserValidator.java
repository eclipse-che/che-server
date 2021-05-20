/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.user.server;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import javax.inject.Inject;
import org.eclipse.che.account.spi.AccountValidator;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.user.User;

/**
 * Utils for username validation and normalization.
 *
 * @author Mihail Kuznyetsov
 * @author Yevhenii Voevodin
 * @author Sergii Leschenko
 */
public class UserValidator {
  @VisibleForTesting static final String GENERATED_NAME_PREFIX = "username";

  private final AccountValidator accountValidator;

  @Inject
  public UserValidator(AccountValidator accountValidator) {
    this.accountValidator = accountValidator;
  }

  /**
   * Checks whether given user is valid.
   *
   * @param user user to check
   * @throws BadRequestException when user is not valid
   */
  public void checkUser(User user) throws BadRequestException {
    if (user == null) {
      throw new BadRequestException("User required");
    }
    if (isNullOrEmpty(user.getName())) {
      throw new BadRequestException("User name required");
    }
    if (!isValidName(user.getName())) {
      throw new BadRequestException(
          "Username may only contain alphanumeric characters or single hyphens inside");
    }
    if (isNullOrEmpty(user.getEmail())) {
      throw new BadRequestException("User email required");
    }
    if (user.getPassword() != null) {
      checkPassword(user.getPassword());
    }
  }

  /**
   * Checks whether password is ok.
   *
   * @param password password to check
   * @throws BadRequestException when password is not valid
   */
  public void checkPassword(String password) throws BadRequestException {
    if (password == null) {
      throw new BadRequestException("Password required");
    }
    if (password.length() < 8) {
      throw new BadRequestException("Password should contain at least 8 characters");
    }
    int numOfLetters = 0;
    int numOfDigits = 0;
    for (char passwordChar : password.toCharArray()) {
      if (Character.isDigit(passwordChar)) {
        numOfDigits++;
      } else if (Character.isLetter(passwordChar)) {
        numOfLetters++;
      }
    }
    if (numOfDigits == 0 || numOfLetters == 0) {
      throw new BadRequestException("Password should contain letters and digits");
    }
  }

  /**
   * Validate name, if it doesn't contain illegal characters
   *
   * @param name username
   * @return true if valid name, false otherwise
   */
  public boolean isValidName(String name) {
    return accountValidator.isValidName(name);
  }

  /**
   * Remove illegal characters from username, to make it URL-friendly. If all characters are
   * illegal, return automatically generated username. Also ensures username is unique, if not, adds
   * digits to it's end.
   *
   * @param name username
   * @return username without illegal characters
   */
  public String normalizeUserName(String name) throws ServerException {
    return accountValidator.normalizeAccountName(name, GENERATED_NAME_PREFIX);
  }
}
