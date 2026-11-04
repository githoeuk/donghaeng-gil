package io.demo.companion;
public final class SharingLifetimeTest {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("lifetime check "+checks);}
    public static void main(String[] args){
        SharingLifetime life=new SharingLifetime();
        check(!life.retaining());check(life.arm(false,true,true,true,1000)<0);
        check(life.arm(true,false,true,true,1000)<0);check(life.arm(true,true,false,true,1000)<0);
        check(life.arm(true,true,true,false,1000)<0);check(!life.retaining());
        long first=life.arm(true,true,true,true,1000);check(first>0);check(life.retaining());
        check(!life.begin(first+1,true,true,1100));check(life.phase()==SharingLifetime.Phase.ARMED);
        check(life.begin(first,true,true,1100));check(life.phase()==SharingLifetime.Phase.STARTING);
        check(life.arm(true,true,true,true,1200)<0);check(!life.expired(11100));check(life.expired(11101));
        life.confirmed();check(life.phase()==SharingLifetime.Phase.ACTIVE);check(!life.expired(11101));
        check(life.expired(7201100));check(life.expired(1099));
        life.stop();check(!life.retaining());life.confirmed();check(!life.retaining());
        long next=life.arm(true,true,true,true,20000);check(next!=first);
        check(!life.begin(first,true,true,20100));check(life.begin(next,true,true,20100));
        life.stop();next=life.arm(true,true,true,true,30000);check(!life.begin(next,false,true,30100));check(!life.retaining());
        next=life.arm(true,true,true,true,40000);check(!life.begin(next,true,false,40100));check(!life.retaining());
        next=life.arm(true,true,true,true,50000);check(!life.begin(next,true,true,53001));check(!life.retaining());
        next=life.arm(true,true,true,true,60000);life.stop();check(!life.begin(next,true,true,60100));
        check(!new SharingLifetime().retaining());
        System.out.println("Passed "+checks+" sharing-lifetime checks.");
    }
}
