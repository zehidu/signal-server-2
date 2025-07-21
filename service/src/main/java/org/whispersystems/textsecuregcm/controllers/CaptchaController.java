/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/v1/captcha")
@Tag(name = "Captcha")
public class CaptchaController {

  @GET
  @Path("/registration/generate.html")
  @Produces(MediaType.TEXT_HTML)
  @Operation(
      summary = "Get registration captcha HTML page",
      description = "Provides HTML page for registration captcha verification"
  )
  @ApiResponse(responseCode = "200", description = "HTML captcha page for registration")
  public Response getRegistrationCaptcha(@Context ContainerRequestContext requestContext,
                                       @QueryParam("session") String sessionId) {
    // Generate a Signal-compatible captcha token using the noop provider
    // Format: provider.sitekey.action.token
    String uniqueId = UUID.randomUUID().toString().replace("-", "");
    String captchaToken = "noop.noop.registration." + uniqueId;
    
    StringBuilder htmlBuilder = new StringBuilder();
    htmlBuilder.append("<!DOCTYPE html>");
    htmlBuilder.append("<html lang=\"en\">");
    htmlBuilder.append("<head>");
    htmlBuilder.append("<meta charset=\"UTF-8\">");
    htmlBuilder.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
    htmlBuilder.append("<title>Signal Registration - Human Verification</title>");
    htmlBuilder.append("<style>");
    htmlBuilder.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 400px; margin: 0 auto; padding: 20px; background: #f8f9fa; color: #333; }");
    htmlBuilder.append(".container { text-align: center; background: white; border-radius: 12px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }");
    htmlBuilder.append(".logo { width: 60px; height: 60px; background: #2090ea; border-radius: 50%; margin: 0 auto 20px; display: flex; align-items: center; justify-content: center; color: white; font-size: 24px; font-weight: bold; }");
    htmlBuilder.append("h2 { color: #2090ea; margin-bottom: 10px; font-size: 24px; }");
    htmlBuilder.append(".subtitle { color: #666; margin-bottom: 30px; font-size: 16px; }");
    htmlBuilder.append(".challenge { background: #f8f9fa; padding: 25px; margin: 20px 0; border-radius: 8px; border: 1px solid #e9ecef; }");
    htmlBuilder.append(".verify-btn { background: #2090ea; color: white; padding: 12px 30px; border: none; border-radius: 25px; cursor: pointer; font-size: 16px; font-weight: 500; transition: background 0.2s; width: 100%; max-width: 200px; }");
    htmlBuilder.append(".verify-btn:hover { background: #1a7bc7; }");
    htmlBuilder.append(".success { color: #28a745; font-weight: 500; margin-top: 15px; }");
    htmlBuilder.append(".instructions { color: #666; font-size: 14px; margin-bottom: 20px; }");
    htmlBuilder.append("</style>");
    htmlBuilder.append("</head>");
    htmlBuilder.append("<body>");
    htmlBuilder.append("<div class=\"container\">");
    htmlBuilder.append("<div class=\"logo\">S</div>");
    htmlBuilder.append("<h2>Human Verification</h2>");
    htmlBuilder.append("<p class=\"subtitle\">Complete verification to continue with Signal registration</p>");
    htmlBuilder.append("<div class=\"challenge\">");
    htmlBuilder.append("<p class=\"instructions\">To verify you're human, please click the button below:</p>");
    htmlBuilder.append("<button class=\"verify-btn\" onclick=\"verifyHuman()\">Verify I'm Human</button>");
    htmlBuilder.append("<div id=\"result\"></div>");
    htmlBuilder.append("</div>");
    htmlBuilder.append("<p style=\"font-size: 12px; color: #999; margin-top: 20px;\">This verification helps protect Signal from automated abuse.</p>");
    htmlBuilder.append("</div>");
    htmlBuilder.append("<script>");
    htmlBuilder.append("const CAPTCHA_TOKEN = '").append(captchaToken).append("';");
    htmlBuilder.append("function verifyHuman() {");
    htmlBuilder.append("const button = document.querySelector('.verify-btn');");
    htmlBuilder.append("const result = document.getElementById('result');");
    htmlBuilder.append("button.disabled = true;");
    htmlBuilder.append("button.textContent = 'Verifying...';");
    htmlBuilder.append("fetch('/v1/captcha/registration/verify', {");
    htmlBuilder.append("method: 'POST',");
    htmlBuilder.append("headers: { 'Content-Type': 'application/json' },");
    htmlBuilder.append("body: JSON.stringify({ token: CAPTCHA_TOKEN, timestamp: Date.now() })");
    htmlBuilder.append("}).then(response => response.json()).then(data => {");
    htmlBuilder.append("if (data.success) {");
    htmlBuilder.append("result.innerHTML = '<p class=\"success\">✓ Verification successful!</p>';");
    htmlBuilder.append("button.style.display = 'none';");
    htmlBuilder.append("setTimeout(() => { window.location.href = 'signalcaptcha://' + CAPTCHA_TOKEN; }, 1000);");
    htmlBuilder.append("} else {");
    htmlBuilder.append("result.innerHTML = '<p style=\"color: red;\">Verification failed. Please try again.</p>';");
    htmlBuilder.append("button.disabled = false;");
    htmlBuilder.append("button.textContent = 'Verify I\\'m Human';");
    htmlBuilder.append("}");
    htmlBuilder.append("}).catch(error => {");
    htmlBuilder.append("console.error('Verification error:', error);");
    htmlBuilder.append("result.innerHTML = '<p style=\"color: red;\">Network error. Please try again.</p>';");
    htmlBuilder.append("button.disabled = false;");
    htmlBuilder.append("button.textContent = 'Verify I\\'m Human';");
    htmlBuilder.append("});");
    htmlBuilder.append("}");
    htmlBuilder.append("</script>");
    htmlBuilder.append("</body>");
    htmlBuilder.append("</html>");
    
    return Response.ok(htmlBuilder.toString(), MediaType.TEXT_HTML).build();
  }

  @POST
  @Path("/registration/verify")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Verify captcha token",
      description = "Verifies the captcha token submitted by the HTML page"
  )
  @ApiResponse(responseCode = "200", description = "Captcha verification result")
  public Response verifyCaptcha(@Context ContainerRequestContext requestContext, String requestBody) {
    try {
      if (requestBody != null && requestBody.contains("token") && requestBody.contains("timestamp")) {
        String response = "{" +
            "\"success\": true," +
            "\"message\": \"Captcha verification successful\"," +
            "\"timestamp\": " + System.currentTimeMillis() +
            "}";
        return Response.ok(response, MediaType.APPLICATION_JSON).build();
      } else {
        String errorResponse = "{" +
            "\"success\": false," +
            "\"message\": \"Invalid captcha token format\"," +
            "\"timestamp\": " + System.currentTimeMillis() +
            "}";
        return Response.status(400).entity(errorResponse).build();
      }
    } catch (Exception e) {
      String errorResponse = "{" +
          "\"success\": false," +
          "\"message\": \"Captcha verification failed\"," +
          "\"timestamp\": " + System.currentTimeMillis() +
          "}";
      return Response.status(500).entity(errorResponse).build();
    }
  }
}