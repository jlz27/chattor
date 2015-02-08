package server;

import resources.TestResource;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public final class ChatTorWebServer extends Application<ChatTorConfiguration> {

  @Override
  public void initialize(Bootstrap<ChatTorConfiguration> arg0) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void run(ChatTorConfiguration configuration, Environment environment) throws Exception {
    TestResource testResource = new TestResource();
    
    environment.jersey().register(testResource);
  }

  public static void main(String[] args) throws Exception {
    new ChatTorWebServer().run(args);
  }
}
