package eu.amadeos.games.town.domain;

/**
 * The computer as a player: it does the steps of the {@link Plan} one after another, one every few
 * seconds, as a player would with the mouse. A step it cannot pay for yet waits until the money is there;
 * a step that no longer fits the town is left out. The title screen shows a town growing this way, and the
 * tests play the plan to see that the rules let a town grow.
 */
public final class Planner {
    private final Plan plan;
    private int next;
    private double timer;

    public Planner(Plan plan) {
        this.plan = plan;
    }

    // --8<-- [start:update]
    public void update(Game game, double seconds) {
        timer += seconds;
        while (timer >= plan.every() && next < plan.steps().size()) {
            timer -= plan.every();
            Game.Result result = apply(game, plan.steps().get(next));
            if (result == Game.Result.MONEY) return;
            next++;
        }
    }
    // --8<-- [end:update]

    /** Does one step of a plan, as the player would. */
    public static Game.Result apply(Game game, Plan.Step step) {
        return switch (step.action()) {
            case "road" -> game.road(Game.path(step.col(), step.row(), step.col2(), step.row2()));
            case "line" -> game.line(Game.path(step.col(), step.row(), step.col2(), step.row2()));
            case "zone" -> game.zone(Zone.ofLetter(step.what().charAt(0)), step.col(), step.row(), step.col2(), step.row2());
            case "build" -> game.build(game.rules().building(step.what()), step.col(), step.row());
            case "bulldoze" -> game.bulldoze(step.col(), step.row(), step.col2(), step.row2());
            default -> Game.Result.NOTHING;
        };
    }

    /** Whether every step is done. */
    public boolean done() {
        return next >= plan.steps().size();
    }

    /** How many steps are done. */
    public int steps() {
        return next;
    }
}
