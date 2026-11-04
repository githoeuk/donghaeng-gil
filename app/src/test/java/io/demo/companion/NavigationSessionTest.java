package io.demo.companion;

public final class NavigationSessionTest {
    private static int checks;
    private static void expect(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void reply(NavigationSession session, long token, JourneyState state, String name) {
        if (session.acceptsDestination(token)) state.destination(name,"주소",37.4,127.1,600,3000,1000,1000000);
    }
    public static void main(String[] args) {
        JourneyState state = new JourneyState(); NavigationSession session = new NavigationSession();
        long first = session.open(state); reply(session,first,state,"old");
        expect(state.hasDestination(),"initial response");
        // Same transition used by source restart(): invalidate old listener, open a new one.
        session.close(); long changed = session.open(state);
        expect(!state.hasDestination(),"destination change clears visible old route");
        reply(session,first,state,"late old"); expect(!state.hasDestination(),"late old destination dropped");
        expect(!session.accepts(first),"old recent-address callback dropped too");
        reply(session,changed,state,"new"); expect(state.destinationName.equals("new"),"new listener response accepted");
        reply(session,first,state,"late old again"); expect(state.destinationName.equals("new"),"late old cannot overwrite new");
        session.end(changed,state,false); reply(session,changed,state,"after cancel");
        expect(!state.hasDestination(),"cancelled response cannot revive route");
        expect(!session.acceptsDestination(changed),"cancel blocks address-only response");
        session.driving(changed,state); reply(session,changed,state,"delayed driving");
        expect(!state.hasDestination(),"delayed driving status cannot undo cancellation");
        session.close(); long restarted = session.open(state); session.idle(restarted,state);
        reply(session,restarted,state,"new after initial idle");
        expect(state.hasDestination(),"initial idle does not permanently block route data");
        session.end(restarted,state,true); session.idle(restarted,state); session.driving(restarted,state);
        reply(session,restarted,state,"after arrival"); expect(state.arrived && !state.hasDestination(),"arrival survives late idle and route data");
        session.close(); long reconnect = session.open(state);
        session.end(restarted,state,false); expect(!state.routeEnded,"old cancellation cannot terminate reconnected session");
        reply(session,reconnect,state,"reconnected"); expect(state.hasDestination(),"reconnection accepts new data");
        session.idle(reconnect,state); reply(session,reconnect,state,"after idle");
        expect(!state.hasDestination(),"idle after established route blocks delayed route reply");
        session.close(); expect(!session.accepts(reconnect),"stopped source drops all callbacks");
        System.out.println("Passed " + checks + " navigation-session checks.");
    }
}
