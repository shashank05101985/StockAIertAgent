package org.example;

import org.example.analysis.StockFeatureCalculator;
import org.example.repository.DB;
import org.example.repository.StockFeatureRepository;

public class StockFeatureMain {
    public static void main(String[] args) throws Exception {
        StockFeatureRepository repository = new StockFeatureRepository(DB.get());
        StockFeatureCalculator calculator = new StockFeatureCalculator(repository, "NSE");
        calculator.calculate();
    }
}