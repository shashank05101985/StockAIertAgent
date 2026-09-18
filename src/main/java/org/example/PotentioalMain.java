package org.example;

import org.example.model.PotentialStock;
import org.example.repository.DB;
import org.example.repository.PotentialStockRepoistory;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class PotentioalMain {
    public static void main(String[] args) throws SQLException {
        PotentialStockRepoistory repoistory = new PotentialStockRepoistory(DB.get());
        List<PotentialStock> stocks =
            repoistory.findPotentialStocks(
                LocalDate.of(2026, 8, 25)
            );

        for (PotentialStock s : stocks) {

            System.out.printf(
                "%-12s close=%8.2f score=%3.0f ATR%%=%5.2f breakout=%5.2f return5=%6.2f%%%n",
                s.getSymbol(),
                s.getClosePrice(),
                s.getPotentialScore(),
                s.getAtrPct(),
                s.getBreakoutStrength(),
                s.getReturn5d()
            );
        }
    }
}
