package org.whispersystems.textsecuregcm.email;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final boolean enabled;
    
    public EmailService(String smtpHost, int smtpPort, String username, 
                       String password, String fromAddress, boolean enabled) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.enabled = enabled;
        
        logger.info("EmailService initialized - Host: {}, Port: {}, Enabled: {}", 
                   smtpHost, smtpPort, enabled);
    }
    
    public CompletableFuture<Boolean> sendVerificationCode(String toEmail, String code) {
        if (!enabled) {
            logger.warn("Email service is disabled");
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Sending verification code to: {}", toEmail);
                
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", smtpHost);
                props.put("mail.smtp.port", smtpPort);
                props.put("mail.smtp.ssl.trust", smtpHost);
                
                Session session = Session.getInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });
                
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(fromAddress));
                message.setRecipients(Message.RecipientType.TO, 
                                    InternetAddress.parse(toEmail));
                message.setSubject("Your Signal Verification Code");
                
                String emailBody = "Your Signal verification code is: " + code + 
                                 "\n\nThis code will expire in 10 minutes." +
                                 "\n\nIf you did not request this code, please ignore this email.";
                message.setText(emailBody);
                
                Transport.send(message);
                logger.info("Email sent successfully to: {}", toEmail);
                return true;
                
            } catch (MessagingException e) {
                logger.error("Failed to send email to {}: {}", toEmail, e.getMessage());
                return false;
            }
        });
    }
    
    public void testConnection() {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            
            Session session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
            
            Transport transport = session.getTransport("smtp");
            transport.connect();
            transport.close();
            logger.info("SMTP connection test successful");
            
        } catch (MessagingException e) {
            logger.error("SMTP connection failed: {}", e.getMessage());
        }
    }
    
    public boolean isEmailAddress(String destination) {
        return destination != null && destination.contains("@") && destination.contains(".");
    }
}
