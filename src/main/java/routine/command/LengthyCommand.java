package routine.command;

public class LengthyCommand extends RoutineCommand {
    private int duration;

    public LengthyCommand(String devID, String newState, int duration) {
        super(devID, newState);
        this.duration = duration;
    }

    public LengthyCommand(String devID, String newState, String property, int duration) {
        super(devID, newState, property);
        this.duration = duration;
    }

    @Override
    public int getLength() {
        return duration;
    }
}
