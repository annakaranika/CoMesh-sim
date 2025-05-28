package routine;

public class Accumulator {
    private String device;
    private int elapsedMs;
    private int deviceCount;

    public Accumulator(String unparsedAccumulator) {
        String accContents = unparsedAccumulator.substring(unparsedAccumulator.indexOf("(") + 1);
        accContents = accContents.substring(0, accContents.indexOf(")"));
        String[] accContentsArray = accContents.split(", ");
        this.device = accContentsArray[0];
        this.elapsedMs = Integer.parseInt(accContentsArray[1]);
        this.deviceCount = Integer.parseInt(accContentsArray[2]);
    }

    public Accumulator(String device, int elapsedMs, int deviceCount) {
        this.device = device;
        this.elapsedMs = elapsedMs;
        this.deviceCount = deviceCount;
    }

    public String getDevice() {
        return device;
    }

    public int getDeviceCount() {
        return deviceCount;
    }

    public int getElapsedMs() {
        return elapsedMs;
    }
}