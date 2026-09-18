package org.example.model;

import org.example.util.DailyPatternDetector;

public class BreakoutCandidate {

    public String symbol;
    public String pattern;

    public double close;
    public double support;
    public double resistance;

    public double distanceToResistancePct;
    public double volumeRatio;
    public double confidence;

    public double patternScore;
    public double resistanceScore;
    public double supportScore;
    public double proximityScore;
    public double volumeScore;
    public double targetSpaceScore;

    public double totalScore;

    public double target3;
    public double target4;

    public String assessment;

    public BreakoutCandidate(String symbol, DailyPatternDetector.PatternResult p, double close, double distanceToResistancePct, double patternScore, double resistanceScore, double supportScore, double proximityScore, double volumeScore, double targetSpaceScore, double totalScore, double target3, double target4, String assessment) {

        this.symbol = symbol;
        this.pattern = p.pattern;

        this.close = close;
        this.support = p.support;
        this.resistance = p.resistance;

        this.distanceToResistancePct = distanceToResistancePct;
        this.volumeRatio = p.volumeRatio;
        this.confidence = p.confidence;

        this.patternScore = patternScore;
        this.resistanceScore = resistanceScore;
        this.supportScore = supportScore;
        this.proximityScore = proximityScore;
        this.volumeScore = volumeScore;
        this.targetSpaceScore = targetSpaceScore;

        this.totalScore = totalScore;

        this.target3 = target3;
        this.target4 = target4;

        this.assessment = assessment;
    }

}
