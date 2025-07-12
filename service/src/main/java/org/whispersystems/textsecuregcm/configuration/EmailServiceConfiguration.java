package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class EmailServiceConfiguration {
    
    @JsonProperty
    @NotEmpty
    private String smtpHost = "smtp.gmail.com";
    
    @JsonProperty
    @NotNull
    private Integer smtpPort = 587;
    
    @JsonProperty
    @NotEmpty
    private String username;
    
    @JsonProperty
    @NotEmpty
    private String password;
    
    @JsonProperty
    @NotEmpty
    private String fromAddress;
    
    @JsonProperty
    private boolean enabled = true;
    
    public String getSmtpHost() {
        return smtpHost;
    }
    
    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }
    
    public Integer getSmtpPort() {
        return smtpPort;
    }
    
    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getFromAddress() {
        return fromAddress;
    }
    
    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
