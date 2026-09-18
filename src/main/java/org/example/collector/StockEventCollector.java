package org.example.collector;

import org.example.model.StockEvent;
import org.example.news.EventClassifier;
import org.example.repository.StockEventDao;
import org.example.util.HashUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockEventCollector {

    private final NseRssCollector nseCollector;
    private final StockEventDao eventDao;

    public StockEventCollector(NseRssCollector nseCollector, StockEventDao eventDao) {

        this.nseCollector = nseCollector;
        this.eventDao = eventDao;
    }

    public void collect(String fromDate, String toDate) {

        try {

            List<StockEvent> events = nseCollector.fetch(fromDate,toDate);
            Map<String, Integer> symbolCountMap = new HashMap<>();

            for (StockEvent event : events) {
                String symbol = event.getSymbol();

                try {

                    if (symbol == null ) {
                        continue;
                    }
                    EventClassifier.Classification c = EventClassifier.classify(event);

                    event.setEventType(c.eventType());
                    event.setSentiment(c.sentiment());
                    event.setSentimentScore(c.sentimentScore());
                    event.setImportanceScore(c.importanceScore());
                    event.setDocumentSummary(c.summary());

                    String hash = HashUtil.sha256(symbol + "|" + event.getTitle() + "|" + event.getEventTime() + "|" + event.getEventType());

                    event.setContentHash(hash);
                    eventDao.save(event);
                    System.out.println(
                        "EVENT | " + symbol + " | " + event.getEventType() + " | " + event.getSentiment() + " | " + event.getTitle());
                }catch (Exception e)
                {
                    System.err.println("NSE event collection failed for " + event.getTitle() + " symbol :" + symbol);
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {

            System.err.println("NSE event collection failed");
            e.printStackTrace();
        }
    }
}
