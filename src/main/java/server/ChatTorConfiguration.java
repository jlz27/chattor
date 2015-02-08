package server;

import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.Configuration;

public final class ChatTorConfiguration extends Configuration {

  @NotEmpty
  private String ip;
  
  @NotNull
  private int port;
  
  @JsonProperty
  public void setIp(String ip) {
    this.ip = ip;
  }
  
  @JsonProperty
  public void setPort(int port) {
    this.port = port;
  }
  
  @JsonProperty
  public String getIp() {
    return ip;
  }
  
  @JsonProperty
  public int getPort() {
    return port;
  }
}
