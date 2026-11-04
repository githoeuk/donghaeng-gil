package io.demo.companion;
public class RelayTest {
 private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 private static void rejects(String url,boolean debug){try{RelayEndpoint.validate(url,debug);throw new AssertionError(url);}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  check(RelayEndpoint.validate("https://relay.example/",false).equals("https://relay.example"));
  check(RelayEndpoint.validate("http://10.0.2.2:8787",true).equals("http://10.0.2.2:8787"));
  rejects("http://10.0.2.2:8787",false);rejects("http://example.com",true);rejects("https://user:password@example.com",false);rejects("https://example.com/path",false);rejects("https://example.com?secret=1",false);
  JourneyState s=new JourneyState();check(JourneyPublisher.current(s,10000,50000)==null);
  s.connected=true;s.locationPermission=true;s.locationEnabled=true;s.gps(37.4,127.1,5,10000);s.navi(37.4,127.1,"current address",10000);s.destination("shop","detail",37.5,127.2,600,5000,10000,50000);
  SharingModels.Vehicle v=JourneyPublisher.current(s,11000,51000);check(v!=null);check(v.remainingSeconds==599);check(v.destinationName.equals("shop"));check(v.address.equals("current address"));
  check(JourneyPublisher.current(s,26000,66000)==null);
  s.locationPermission=false;check(JourneyPublisher.current(s,11000,51000)==null);s.locationPermission=true;
  s.gpsAccuracy=200;check(JourneyPublisher.current(s,11000,51000)==null);s.gpsAccuracy=5;
  s.naviLatitude=38;check(JourneyPublisher.current(s,11000,51000).remainingSeconds==-1);s.naviLatitude=37.4;
  s.endRoute(false);v=JourneyPublisher.current(s,11000,51000);check(v.destinationName.isEmpty());check(v.remainingSeconds==-1);
  check(JourneyPublisher.current(s,9000,49000)==null);
  System.out.println("Passed "+checks+" relay publisher/endpoint checks.");
 }
}
