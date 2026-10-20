package org.example.dailyjob;



import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public class HistoricalBhavcopyLoader {

    public static void loadLast52Weeks()
            throws Exception {

        LocalDate today = LocalDate.now();

        loadRange(today.minusDays(3), today);
    }

    /** Loads bhavcopy for every weekday from {@code from} to {@code to}, both inclusive. */
    public static void loadRange(LocalDate from, LocalDate to)
            throws Exception {

        BhavCopyService service =
                new BhavCopyService();

        int totalInserted = 0;

        for (LocalDate date = from;
             !date.isAfter(to);
             date = date.plusDays(1)) {

            // Skip weekends
            if (date.getDayOfWeek() ==
                    DayOfWeek.SATURDAY
                    ||
                    date.getDayOfWeek() ==
                            DayOfWeek.SUNDAY) {

                continue;
            }

            try {

                System.out.println(
                        "Loading: " + date
                );

                List<BhavcopyRecord> list =
                        service.fetchBhavcopy(date);

                if (list.isEmpty()) {

                    System.out.println(
                            "No data for " + date
                    );

                    continue;
                }

                StockDataDao.insertBatch(list);

                totalInserted += list.size();

                System.out.println(
                        "Inserted: "
                                + list.size()
                );

                // Small delay to avoid NSE blocking
                Thread.sleep(1000);

            } catch (Exception e) {

                System.err.println(
                        "Failed date: " + date
                );

                e.printStackTrace();
            }
        }

        System.out.println(
                "TOTAL INSERTED = "
                        + totalInserted
        );
    }

    public static void reloadDate(
            LocalDate date
    ) throws Exception {

        BhavCopyService service =
                new BhavCopyService();

        List<BhavcopyRecord> list =
                service.fetchBhavcopy(date);

        StockDataDao.insertBatch(list);

        System.out.println(
                "Reloaded: " + date
        );
    }
}
