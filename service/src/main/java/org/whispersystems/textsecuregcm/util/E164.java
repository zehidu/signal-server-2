/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.util;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.Optional;

/**
 * Constraint annotation that requires annotated entity
 * to hold (or return) a string value that is a valid E164-normalized phone number.
 */
@Target({ FIELD, PARAMETER, METHOD })
@Retention(RUNTIME)
@Constraint(validatedBy = {
    E164.Validator.class,
    E164.OptionalValidator.class
})
@Documented
public @interface E164 {

  String message() default "value is not a valid E164 number or email address";

  Class<?>[] groups() default { };

  Class<? extends Payload>[] payload() default { };

  class Validator implements ConstraintValidator<E164, String> {

    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {
      if (Objects.isNull(value)) {
        return true;
      }
      if (!value.startsWith("+")) {
        return false;
      }
      try {
        Util.requireNormalizedNumber(value);
      } catch (final ImpossiblePhoneNumberException | NonNormalizedPhoneNumberException e) {
        return false;
      }
      return true;
    }
  }

  class OptionalValidator implements ConstraintValidator<E164, Optional<String>> {

    @Override
public boolean isValid(final String value, final ConstraintValidatorContext context) {
  if (Objects.isNull(value)) {
    return true;
  }
  
  // Check if it's an email address
  if (isEmailAddress(value)) {
    return isValidEmail(value);
  }
  
  // Original phone number validation
  if (!value.startsWith("+")) {
    return false;
  }
  try {
    Util.requireNormalizedNumber(value);
  } catch (final ImpossiblePhoneNumberException | NonNormalizedPhoneNumberException e) {
    return false;
  }
  return true;
}

private boolean isEmailAddress(final String value) {
  return value != null && value.contains("@") && value.contains(".");
}

private boolean isValidEmail(final String email) {
  // Basic email validation
  if (email == null || email.trim().isEmpty()) {
    return false;
  }
  
  String trimmed = email.trim();
  
  // Must contain exactly one @
  long atCount = trimmed.chars().filter(ch -> ch == '@').count();
  if (atCount != 1) {
    return false;
  }
  
  // Split by @
  String[] parts = trimmed.split("@");
  if (parts.length != 2) {
    return false;
  }
  
  String localPart = parts[0];
  String domainPart = parts[1];
  
  // Local part validation
  if (localPart.isEmpty() || localPart.length() > 64) {
    return false;
  }
  
  // Domain part validation
  if (domainPart.isEmpty() || domainPart.length() > 255) {
    return false;
  }
  
  // Domain must contain at least one dot
  if (!domainPart.contains(".")) {
    return false;
  }
  
  // Basic character validation
  if (!localPart.matches("[a-zA-Z0-9._%+-]+") || !domainPart.matches("[a-zA-Z0-9.-]+")) {
    return false;
  }
  
  return true;
}

  }
}
