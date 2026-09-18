package org.example;

import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import org.example.collector.NseRssCollector;
import org.example.collector.NseSecurityLoader;
import org.example.collector.StockEventCollector;
import org.example.collector.SymbolResolver;
import org.example.repository.DB;
import org.example.repository.StockEventDao;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GetKeyRatios {
    public static void main(String[] args) throws Exception, KiteException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        NseRssCollector collector = new NseRssCollector("");
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.of(2026, 8, 20);
        LocalDate yesterday = today;

        DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

        String fromDate =
            yesterday.format(formatter);

        String toDate =
            today.format(formatter);
        StockEventDao dao = new StockEventDao(DB.get());
        StockEventCollector stockEventCollector = new StockEventCollector(collector, dao);
        stockEventCollector.collect(fromDate,toDate);


        //scheduler.scheduleAtFixedRate(stockEventCollector::collect, 0, 1, TimeUnit.MINUTES);
    }
}
