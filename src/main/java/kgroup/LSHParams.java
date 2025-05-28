package kgroup;

public class LSHParams {
    private int seed = 0;
    private  int k = 2;
    private  int l = 3;
    private  int r = 4;
    private  double mean = 0.;
    private  double std = 2.0;
    private double randomness = 0.2;

    public LSHParams() {;}

    public LSHParams(int seed, int k, int l, int r, double randomness) {
        this.seed = seed;
        this.k = k;
        this.l = l;
        this.r = r;
        this.randomness = randomness;
    }

    public LSHParams(int k, int l, int r, double mean, double std, double randomness) {
        this.k = k;
        this.l = l;
        this.r = r;
        this.mean = mean;
        this.std = std;
        this.randomness = randomness;
    }

    public LSHParams(int seed, int k, int l, int r, double mean, double std, double randomness) {
        this.seed = seed;
        this.k = k;
        this.l = l;
        this.r = r;
        this.mean = mean;
        this.std = std;
        this.randomness = randomness;
    }

    public int getK() {return this.k;}

    public int getL() {return this.l;}

    public int getR() {
        return this.r;
    }
    public double getMean() {return this.mean;}
    public double getStd() {return this.std;}

    public double getRandomness() {
        return this.randomness;
    }

    public void setRandomness(double randomness) {
        this.randomness = randomness;
    }

    public void setK(int k) {this.k = k;}
    public void setL(int l) {this.l = l;}
    public void setR(int r) {this.r = r;}
    public void setMean(double mean) {this.mean = mean;}
    public void setStd(double std) {this.std = std;}

    public void setSeed(int seed) {
        this.seed = seed;
    }

    public int getSeed() {
        return seed;
    }
}
