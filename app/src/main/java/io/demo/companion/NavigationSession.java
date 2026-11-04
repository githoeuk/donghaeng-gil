package io.demo.companion;

/** Listener lifetime, not an SDK request ID. Access only on the UI thread. */
public final class NavigationSession {
    private long generation;
    private boolean active, terminal;
    public long open(JourneyState state) {
        active = true; terminal = false; generation++;
        state.beginRoute(); state.clearDestination(false); state.destinationKnown = false;
        return generation;
    }
    public void close() { active = false; generation++; }
    public boolean accepts(long token) { return active && generation == token; }
    public boolean acceptsDestination(long token) { return accepts(token) && !terminal; }
    public void end(long token, JourneyState state, boolean arrived) {
        if (!accepts(token)) return;
        terminal = true; state.endRoute(arrived);
    }
    public void driving(long token, JourneyState state) {
        // A delayed driving status cannot undo cancellation/arrival.
        if (accepts(token) && !terminal) state.beginRoute();
    }
    public void idle(long token, JourneyState state) {
        if (!accepts(token) || terminal) return;
        // Idle can be the initial status before the first route response.
        if (state.hasDestination()) end(token, state, false); else state.clearDestination(false);
    }
}
