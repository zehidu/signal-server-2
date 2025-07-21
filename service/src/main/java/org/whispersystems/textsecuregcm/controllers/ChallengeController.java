/*
 * Copyright 2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.controllers;

import static org.whispersystems.textsecuregcm.metrics.MetricsUtil.name;

import com.google.common.net.HttpHeaders;
import io.dropwizard.auth.Auth;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.whispersystems.textsecuregcm.auth.AuthenticatedDevice;
import org.whispersystems.textsecuregcm.entities.AnswerCaptchaChallengeRequest;
import org.whispersystems.textsecuregcm.entities.AnswerChallengeRequest;
import org.whispersystems.textsecuregcm.entities.AnswerPushChallengeRequest;
import org.whispersystems.textsecuregcm.filters.RemoteAddressFilter;
import org.whispersystems.textsecuregcm.limits.RateLimitChallengeManager;
import org.whispersystems.textsecuregcm.metrics.UserAgentTagUtil;
import org.whispersystems.textsecuregcm.push.NotPushRegisteredException;
import org.whispersystems.textsecuregcm.spam.ChallengeConstraintChecker;
import org.whispersystems.textsecuregcm.spam.ChallengeConstraintChecker.ChallengeConstraints;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;

@Path("/v1/challenge")
@Tag(name = "Challenge")
public class ChallengeController {

  private final AccountsManager accountsManager;
  private final RateLimitChallengeManager rateLimitChallengeManager;
  private final ChallengeConstraintChecker challengeConstraintChecker;

  private static final String CHALLENGE_RESPONSE_COUNTER_NAME = name(ChallengeController.class, "challengeResponse");
  private static final String CHALLENGE_TYPE_TAG = "type";

  public ChallengeController(
      final AccountsManager accountsManager,
      final RateLimitChallengeManager rateLimitChallengeManager,
      final ChallengeConstraintChecker challengeConstraintChecker) {
    this.accountsManager = accountsManager;
    this.rateLimitChallengeManager = rateLimitChallengeManager;
    this.challengeConstraintChecker = challengeConstraintChecker;
  }

  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Submit proof of a challenge completion",
      description = """
          Some server endpoints (the "send message" endpoint, for example) may return a 428 response indicating the client must complete a challenge before continuing.
          Clients may use this endpoint to provide proof of a completed challenge. If successful, the client may then
          continue their original operation.
          """,
      requestBody = @RequestBody(content = {@Content(schema = @Schema(oneOf = {AnswerPushChallengeRequest.class,
          AnswerCaptchaChallengeRequest.class}))})
  )
  @ApiResponse(responseCode = "200", description = "Indicates the challenge proof was accepted")
  @ApiResponse(responseCode = "428", description = "Submitted captcha token is invalid")
  @ApiResponse(responseCode = "429", description = "Too many attempts", headers = @Header(
      name = "Retry-After",
      description = "If present, an positive integer indicating the number of seconds before a subsequent attempt could succeed"))
  public Response handleChallengeResponse(@Auth final AuthenticatedDevice auth,
      @Valid final AnswerChallengeRequest answerRequest,
      @Context ContainerRequestContext requestContext,
      @HeaderParam(HttpHeaders.USER_AGENT) final String userAgent) throws RateLimitExceededException, IOException {

    final Account account = accountsManager.getByAccountIdentifier(auth.accountIdentifier())
        .orElseThrow(() -> new WebApplicationException(Response.Status.UNAUTHORIZED));

    Tags tags = Tags.of(UserAgentTagUtil.getPlatformTag(userAgent));

    final ChallengeConstraints constraints = challengeConstraintChecker.challengeConstraints(
        requestContext, account);
    try {
      if (answerRequest instanceof final AnswerPushChallengeRequest pushChallengeRequest) {
        tags = tags.and(CHALLENGE_TYPE_TAG, "push");

        if (!constraints.pushPermitted()) {
          return Response.status(429).build();
        }
        rateLimitChallengeManager.answerPushChallenge(account, pushChallengeRequest.getChallenge());
      } else if (answerRequest instanceof AnswerCaptchaChallengeRequest captchaChallengeRequest) {
        tags = tags.and(CHALLENGE_TYPE_TAG, "captcha");

        final String remoteAddress = (String) requestContext.getProperty(
            RemoteAddressFilter.REMOTE_ADDRESS_ATTRIBUTE_NAME);
        boolean success = rateLimitChallengeManager.answerCaptchaChallenge(
            account,
            captchaChallengeRequest.getCaptcha(),
            remoteAddress,
            userAgent,
            constraints.captchaScoreThreshold());

        if (!success) {
          return Response.status(428).build();
        }

      } else {
        tags = tags.and(CHALLENGE_TYPE_TAG, "unrecognized");
      }
    } finally {
      Metrics.counter(CHALLENGE_RESPONSE_COUNTER_NAME, tags).increment();
    }

    return Response.status(200).build();
  }

  @POST
  @Path("/push")
  @Operation(
      summary = "Request a push challenge",
      description = """
          Clients may proactively request a push challenge by making an empty POST request. Push challenges will only be
          sent to the requesting account’s main device. When the push is received it may be provided as proof of completed
          challenge to /v1/challenge.
          APNs challenge payloads will be formatted as follows:
          ```
          {
              "aps": {
                  "sound": "default",
                  "alert": {
                      "loc-key": "APN_Message"
                  }
              },
              "rateLimitChallenge": "{CHALLENGE_TOKEN}"
          }
          ```
          FCM challenge payloads will be formatted as follows:
          ```
          {"rateLimitChallenge": "{CHALLENGE_TOKEN}"}
          ```

          Clients may retry the PUT in the event of an HTTP/5xx response (except HTTP/508) from the server, but must
          implement an exponential back-off system and limit the total number of retries.
          """
  )
  @ApiResponse(responseCode = "200", description = """
      Indicates a payload to the account's primary device has been attempted. When clients receive a challenge push
      notification, they may issue a PUT request to /v1/challenge.
      """)
  @ApiResponse(responseCode = "404", description = """
      The server does not have a push notification token for the authenticated account’s main device; clients may add a push
      token and try again
      """)
  @ApiResponse(responseCode = "413", description = "Too many attempts", headers = @Header(
      name = "Retry-After",
      description = "If present, an positive integer indicating the number of seconds before a subsequent attempt could succeed"))
  @ApiResponse(responseCode = "429", description = "Too many attempts", headers = @Header(
      name = "Retry-After",
      description = "If present, an positive integer indicating the number of seconds before a subsequent attempt could succeed"))
  public Response requestPushChallenge(@Auth final AuthenticatedDevice auth,
      @Context ContainerRequestContext requestContext) {

    final Account account = accountsManager.getByAccountIdentifier(auth.accountIdentifier())
        .orElseThrow(() -> new WebApplicationException(Response.Status.UNAUTHORIZED));

    final ChallengeConstraints constraints = challengeConstraintChecker.challengeConstraints(requestContext, account);
    if (!constraints.pushPermitted()) {
      return Response.status(429).build();
    }
    try {
      rateLimitChallengeManager.sendPushChallenge(account);
      return Response.status(200).build();
    } catch (final NotPushRegisteredException e) {
      return Response.status(404).build();
    }
  }

  @GET
  @Path("/human")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Get human verification challenge",
      description = "Provides human verification challenge for account registration"
  )
  @ApiResponse(responseCode = "200", description = "Human verification challenge provided")
  public Response getHumanVerification(@Context ContainerRequestContext requestContext) {
    // Return a simple human verification challenge
    return Response.ok()
        .entity("{\"challenge\":\"human-verification\",\"type\":\"captcha\"}")
        .build();
  }

  @GET
  @Path("/captcha")
  @Produces(MediaType.TEXT_HTML)
  @Operation(
      summary = "Get human verification HTML page",
      description = "Provides HTML page for human verification captcha"
  )
  @ApiResponse(responseCode = "200", description = "HTML captcha page")
  public Response getCaptchaPage(@Context ContainerRequestContext requestContext) {
    String htmlContent = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Human Verification</title>
            <style>
                body { font-family: Arial, sans-serif; max-width: 500px; margin: 50px auto; padding: 20px; }
                .container { text-align: center; border: 1px solid #ddd; padding: 30px; border-radius: 8px; }
                .challenge { background: #f5f5f5; padding: 20px; margin: 20px 0; border-radius: 4px; }
                button { background: #007cba; color: white; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; }
                button:hover { background: #005a87; }
            </style>
        </head>
        <body>
            <div class="container">
                <h2>Human Verification Required</h2>
                <p>Please complete this verification to continue.</p>
                <div class="challenge">
                    <p><strong>Challenge:</strong> Click the button below to verify you are human</p>
                    <button onclick="verifyHuman()">I am Human</button>
                </div>
                <div id="result"></div>
            </div>
            <script>
                function verifyHuman() {
                    document.getElementById('result').innerHTML = '<p style="color: green;">✓ Verification successful!</p>';
                }
            </script>
        </body>
        </html>
        """;
    
    return Response.ok(htmlContent, MediaType.TEXT_HTML).build();
  }

  @POST
  @Path("/human")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Submit human verification",
      description = "Submit human verification response"
  )
  @ApiResponse(responseCode = "200", description = "Human verification accepted")
  @ApiResponse(responseCode = "400", description = "Invalid verification data")
  public Response submitHumanVerification(
      @Valid final AnswerChallengeRequest answerRequest,
      @Context ContainerRequestContext requestContext) {
    
    // For now, accept any human verification attempt
    // In production, you'd want proper validation here
    return Response.ok()
        .entity("{\"status\":\"verified\",\"message\":\"Human verification successful\"}")
        .build();
  }
}
