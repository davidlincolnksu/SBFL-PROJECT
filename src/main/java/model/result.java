// 2026-05-03
package model;

public class Result {
    public String method;
    public double score;
    public boolean failCovered;

    public Result(String method, double score, boolean failCovered) {
        this.method = method;
        this.score = score;
        this.failCovered = failCovered;
    }
}
