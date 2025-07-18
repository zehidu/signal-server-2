/*
 * Copyright 2013-2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.websocket.auth;

public class InvalidCredentialsException extends Exception {

  public InvalidCredentialsException() {
    super(null, null, true, false);
  }
}
