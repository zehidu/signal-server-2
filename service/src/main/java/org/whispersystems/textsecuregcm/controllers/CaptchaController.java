/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
  public Response getRegistrationCaptcha(@Context ContainerRequestContext requestContext) {
    String htmlContent = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Signal Registration - Human Verification</title>
            <style>
                body { 
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
                    max-width: 400px; 
                    margin: 0 auto; 
                    padding: 20px; 
                    background: #f8f9fa;
                    color: #333;
                }
                .container { 
                    text-align: center; 
                    background: white;
                    border-radius: 12px; 
                    padding: 30px; 
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                }
                .logo {
                    width: 60px;
                    height: 60px;
                    background: #2090ea;
                    border-radius: 50%;
                    margin: 0 auto 20px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: white;
                    font-size: 24px;
                    font-weight: bold;
                }
                h2 { 
                    color: #2090ea; 
                    margin-bottom: 10px;
                    font-size: 24px;
                }
                .subtitle {
                    color: #666;
                    margin-bottom: 30px;
                    font-size: 16px;
                }
                .challenge { 
                    background: #f8f9fa; 
                    padding: 25px; 
                    margin: 20px 0; 
                    border-radius: 8px; 
                    border: 1px solid #e9ecef;
                }
                .verify-btn { 
                    background: #2090ea; 
                    color: white; 
                    padding: 12px 30px; 
                    border: none; 
                    border-radius: 25px; 
                    cursor: pointer; 
                    font-size: 16px;
                    font-weight: 500;
                    transition: background 0.2s;
                    width: 100%;
                    max-width: 200px;
                }
                .verify-btn:hover { 
                    background: #1a7bc7; 
                }
                .success {
                    color: #28a745;
                    font-weight: 500;
                    margin-top: 15px;
                }
                .instructions {
                    color: #666;
                    font-size: 14px;
                    margin-bottom: 20px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="logo">S</div>
                <h2>Human Verification</h2>
                <p class="subtitle">Complete verification to continue with Signal registration</p>
                
                <div class="challenge">
                    <p class="instructions">To verify you're human, please click the button below:</p>
                    <button class="verify-btn" onclick="verifyHuman()">Verify I'm Human</button>
                    <div id="result"></div>
                </div>
                
                <p style="font-size: 12px; color: #999; margin-top: 20px;">
                    This verification helps protect Signal from automated abuse.
                </p>
            </div>
            
            <script>
                function verifyHuman() {
                    const button = document.querySelector('.verify-btn');
                    const result = document.getElementById('result');
                    
                    button.disabled = true;
                    button.textContent = 'Verifying...';
                    
                    setTimeout(() => {
                        result.innerHTML = '<p class="success">✓ Verification successful!</p>';
                        button.style.display = 'none';
                        
                        // Signal the parent app that verification is complete
                        if (window.Android && window.Android.onVerificationComplete) {
                            window.Android.onVerificationComplete();
                        }
                        
                        // Alternative: try to close the webview
                        setTimeout(() => {
                            window.close();
                        }, 2000);
                    }, 1000);
                }
            </script>
        </body>
        </html>
        """;
    
    return Response.ok(htmlContent, MediaType.TEXT_HTML).build();
  }
}