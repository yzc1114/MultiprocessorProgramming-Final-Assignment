package ticketingsystem;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Test {

    private static final int INQUIRY_RATIO = 70;
    private static final int PURCHASE_RATIO = 20;
    private static final int REFUND_RATIO = 10;

    private static final int ROUTE_NUM = 10;
    private static final int COACH_NUM = 8;
    private static final int SEAT_NUM = 100;
    private static final int STATION_NUM = 10;
    private static final int FUNC_CALL_COUNT = 100000;

    private static int THREAD_NUM = 1;

    private static final int REPEAT_MULTI_THREAD_TEST_COUNT = 10;

    private static final boolean PRINT_BUY_INFO = false;

    private static TicketingDS tds;
    private static Map<String, TicketConsumerStatistics> thread2Statistics;

    public static void main(String[] args) throws InterruptedException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        // implFundamentalTests();
        doVariousThreadNumMultiThreadTest();
    }

    private static void doVariousThreadNumMultiThreadTest() throws InterruptedException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
        int[] THREAD_NUMS = new int[]{4, 8, 16, 32, 64};
//        int[] THREAD_NUMS = new int[]{1};
        for (int thread_num : THREAD_NUMS) {
            System.out.println("-------------- START TEST THREAD NUM = " + thread_num + " --------------");
            System.out.println("Thread Num = " + thread_num);
            THREAD_NUM = thread_num;
            tds = new TicketingDS(ROUTE_NUM, COACH_NUM, SEAT_NUM, STATION_NUM, THREAD_NUM);
//            repeatDoMultiThreadTest(TicketingDS.ImplType.Seven, REPEAT_MULTI_THREAD_TEST_COUNT);
            for (TicketingDS.ImplType value : TicketingDS.ImplType.values()) {
                repeatDoMultiThreadTest(value, REPEAT_MULTI_THREAD_TEST_COUNT);
            }
            System.out.println("--------------- END TEST THREAD NUM = " + thread_num + " ---------------\n\n");
        }
        tds.switchImplType(TicketingDS.ImplType.One);
    }

    private static void implFundamentalTests() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        tds = new TicketingDS(ROUTE_NUM, COACH_NUM, SEAT_NUM, STATION_NUM, 1);
        for (TicketingDS.ImplType value : TicketingDS.ImplType.values()) {
            tds.switchImplType(value);
            doFundamentalTest();
        }
        tds.switchImplType(TicketingDS.ImplType.One);
    }

    private static void doFundamentalTest() {
        int ticketsNum = tds.inquiry(1, 5, 7);
        assert ticketsNum == SEAT_NUM * COACH_NUM;
        Ticket ticket = tds.buyTicket("p1", 1, 5, 7);
        assert ticket != null;
        ticketsNum = tds.inquiry(1, 5, 7);
        assert ticketsNum == SEAT_NUM * COACH_NUM - 1;
        Ticket ticket2 = tds.buyTicket("p1", 1, 3, 7);
        assert ticket2 != null;
        ticketsNum = tds.inquiry(1, 5, 7);
        assert ticketsNum == SEAT_NUM * COACH_NUM - 2;
        assert tds.refundTicket(ticket);
        ticketsNum = tds.inquiry(1, 4, 7);
        assert ticketsNum == SEAT_NUM * COACH_NUM - 1;
    }

    private static class TicketConsumerStatistics {
        private int tryBuyCount = 0;
        private int tryInqCount = 0;
        private int tryRefCount = 0;
        private long buyExecTime = 0;
        private long inqExecTime = 0;
        private long refExecTime = 0;
        private int funcCallCount = 0;
        private long fullExecTime = 0;
        private long threadStartTime = 0;
        private long threadEndTime = 0;

        public int getTryBuyCount() {
            return tryBuyCount;
        }

        public int getTryInqCount() {
            return tryInqCount;
        }

        public int getTryRefCount() {
            return tryRefCount;
        }

        public long getBuyExecTime() {
            return buyExecTime;
        }

        public long getInqExecTime() {
            return inqExecTime;
        }

        public long getRefExecTime() {
            return refExecTime;
        }

        public int getFuncCallCount() {
            return funcCallCount;
        }

        public long getFullExecTime() {
            return fullExecTime;
        }

        public long getThreadStartTime() {
            return threadStartTime;
        }

        public long getThreadEndTime() {
            return threadEndTime;
        }

        public void add(int tryBuyCount, int tryInqCount, int tryRefCount, long buyExecTime, long inqExecTime, long refExecTime, long threadStartTime, long threadEndTime) {
            TicketConsumerStatistics s = this;
            s.tryBuyCount += tryBuyCount;
            s.tryInqCount += tryInqCount;
            s.tryRefCount += tryRefCount;
            s.buyExecTime += buyExecTime;
            s.inqExecTime += inqExecTime;
            s.refExecTime += refExecTime;
            s.funcCallCount += tryBuyCount + tryInqCount + tryRefCount;
            s.fullExecTime += buyExecTime + inqExecTime + refExecTime;
            s.threadStartTime = threadStartTime;
            s.threadEndTime = threadEndTime;
        }

        public void nextEpoch() {
            this.threadStartTime = 0;
            this.threadEndTime = 0;
        }
    }

    @SuppressWarnings("all")
    private static void repeatDoMultiThreadTest(TicketingDS.ImplType implType, int repeatCount) throws InterruptedException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        thread2Statistics = new ConcurrentHashMap<>();
        for (int i = 0; i < THREAD_NUM; i++) {
            thread2Statistics.put(String.valueOf(i + 1), new TicketConsumerStatistics());
        }
        long[] fullElapsedTimes = new long[repeatCount];
        long[] fullElapsedTimesInner = new long[repeatCount];
        for (int i = 0; i < repeatCount; i++) {
            tds.switchImplType(implType);
            Thread[] threads = new Thread[THREAD_NUM];
            for (int j = 0; j < THREAD_NUM; j++) {
                Thread t = new Thread(new TicketConsumerRunner(), String.valueOf(j + 1));
                threads[j] = t;
            }
            long start = System.nanoTime();
            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
            long fullElapsedTime = System.nanoTime() - start;
            fullElapsedTimes[i] = fullElapsedTime;
            long innerElapseMinStart = thread2Statistics.values().stream().mapToLong(TicketConsumerStatistics::getThreadStartTime).min().getAsLong();
            long innerElapseMaxEnd = thread2Statistics.values().stream().mapToLong(TicketConsumerStatistics::getThreadEndTime).max().getAsLong();
            fullElapsedTimesInner[i] = innerElapseMaxEnd - innerElapseMinStart;
        }

        long maxFullElapsedTimePerEpoch = Arrays.stream(fullElapsedTimes).max().getAsLong();
        long minFullElapsedTimePerEpoch = Arrays.stream(fullElapsedTimes).min().getAsLong();
        long avgFullElapsedTimePerEpoch = (long) Arrays.stream(fullElapsedTimes).average().getAsDouble();
        Arrays.sort(fullElapsedTimes);
        long midFullElapsedTimePerEpoch = fullElapsedTimes[repeatCount / 2];

        long maxFullElapsedTimeInnerPerEpoch = Arrays.stream(fullElapsedTimesInner).max().getAsLong();
        long minFullElapsedTimeInnerPerEpoch = Arrays.stream(fullElapsedTimesInner).min().getAsLong();
        long avgFullElapsedTimeInnerPerEpoch = (long) Arrays.stream(fullElapsedTimesInner).average().getAsDouble();
        Arrays.sort(fullElapsedTimesInner);
        long midFullElapsedTimeInnerPerEpoch = fullElapsedTimesInner[repeatCount / 2];

        long avgExecNanoTime = (long) thread2Statistics.values().stream().mapToLong(TicketConsumerStatistics::getFullExecTime).average().getAsDouble();
        long fullBuyExecNanoTime = thread2Statistics.values().stream().mapToLong(TicketConsumerStatistics::getBuyExecTime).sum();
        long fullInqExecNanoTime = thread2Statistics.values().stream().mapToLong(TicketConsumerStatistics::getInqExecTime).sum();
        long fullRefExecNanoTime = thread2Statistics.values().stream().mapToLong(TicketConsumerStatistics::getRefExecTime).sum();

        int fullFuncCallCount = thread2Statistics.values().stream().mapToInt(TicketConsumerStatistics::getFuncCallCount).sum();
        int avgFuncCallCount = (int) (thread2Statistics.values().stream().mapToInt(TicketConsumerStatistics::getFuncCallCount).average().getAsDouble());
        int fullBuyFuncCallCount = thread2Statistics.values().stream().mapToInt(TicketConsumerStatistics::getTryBuyCount).sum();
        int fullInqFuncCallCount = thread2Statistics.values().stream().mapToInt(TicketConsumerStatistics::getTryInqCount).sum();
        int fullRefFuncCallCount = thread2Statistics.values().stream().mapToInt(TicketConsumerStatistics::getTryRefCount).sum();

        double best_throughPut_nano = (1. * fullFuncCallCount / repeatCount) / minFullElapsedTimePerEpoch;
        double avg_throughPut_nano = (1. * fullFuncCallCount / repeatCount) / avgFullElapsedTimePerEpoch;
        double best_throughPut_inner_nano = (1. * fullFuncCallCount / repeatCount) / minFullElapsedTimeInnerPerEpoch;
        double avg_throughPut_inner_nano = (1. * fullFuncCallCount / repeatCount) / avgFullElapsedTimeInnerPerEpoch;
        System.out.println("====================================================================");
        System.out.printf("%s class repeat multi thread test finished.\n", tds.getImplClass().getSimpleName());
        System.out.println("repeat test count: " + repeatCount);
        System.out.printf("minFullElapsedTimePerEpoch = %d, maxFullElapsedTimePerEpoch = %d\navgFullElapsedTimePerEpoch = %d, midFullElapsedTimePerEpoch = %d\n", minFullElapsedTimePerEpoch, maxFullElapsedTimePerEpoch, avgFullElapsedTimePerEpoch, midFullElapsedTimePerEpoch);
        System.out.printf("minFullElapsedTimeInnerPerEpoch = %d, maxFullElapsedTimeInnerPerEpoch = %d\navgFullElapsedTimeInnerPerEpoch = %d, midFullElapsedTimeInnerPerEpoch = %d\n", minFullElapsedTimeInnerPerEpoch, maxFullElapsedTimeInnerPerEpoch, avgFullElapsedTimeInnerPerEpoch, midFullElapsedTimeInnerPerEpoch);
        System.out.printf("fullFuncCallCount = %d, avgFuncCallCount = %d\n", fullFuncCallCount, avgFuncCallCount);
        System.out.println("best_throughPut(func/nano) = " + best_throughPut_nano);
        System.out.println("avg_throughPut(func/nano) = " + avg_throughPut_nano);
        System.out.println("best_throughPut_inner(func/nano) = " + best_throughPut_nano);
        System.out.println("avg_throughPut_inner(func/nano) = " + avg_throughPut_inner_nano);
        System.out.println("inquiry average exec time = " + 1. * fullInqExecNanoTime / fullInqFuncCallCount);
        System.out.println("buyTicket average exec time = " + 1. * fullBuyExecNanoTime / fullBuyFuncCallCount);
        System.out.println("refundTicket average exec time = " + 1. * fullRefExecNanoTime / fullRefFuncCallCount);
        System.out.println("====================================================================\n");
    }


    private static class TicketConsumerRunner implements Runnable {
        @Override
        public void run() {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            // statistics
            int buyFailedCount = 0;
            int tryBuyCount = 0;
            int tryInqCount = 0;
            int tryRefCount = 0;
            long buyExecTime = 0;
            long inqExecTime = 0;
            long refExecTime = 0;

            String threadName = Thread.currentThread().getName();
            int allTicketCount = ROUTE_NUM * COACH_NUM * SEAT_NUM;
            List<Ticket> holdTickets = new ArrayList<>(allTicketCount);
            long start = System.nanoTime();
            for (int i = 0; i < FUNC_CALL_COUNT; i++) {
                int n = random.nextInt(100);
                if (n < REFUND_RATIO && holdTickets.size() > 0) {
                    tryRefCount++;
                    long preTime = System.nanoTime();
                    boolean refundResult = tds.refundTicket(holdTickets.remove(holdTickets.size() - 1));
                    long postTime = System.nanoTime();
                    refExecTime += postTime - preTime;
                    if (!refundResult) {
                        System.out.println("refund failed, plz debug");
                        System.exit(-1);
                    }
                } else if (n < PURCHASE_RATIO + REFUND_RATIO) {
                    tryBuyCount++;
                    int routeNum = random.nextInt(ROUTE_NUM) + 1;
                    int departure = random.nextInt(STATION_NUM - 1) + 1;
                    int arrival = departure + random.nextInt(STATION_NUM - departure) + 1;
                    long preTime = System.nanoTime();
                    Ticket t = tds.buyTicket(threadName, routeNum, departure, arrival);
                    long postTime = System.nanoTime();
                    buyExecTime += postTime - preTime;
                    if (t != null) {
                        holdTickets.add(t);
                    } else {
                        // System.out.println("buyTicket failed");
                        buyFailedCount++;
                    }
                } else {
                    tryInqCount++;
                    int routeNum = random.nextInt(ROUTE_NUM) + 1;
                    int departure = random.nextInt(STATION_NUM - 1) + 1;
                    int arrival = departure + random.nextInt(STATION_NUM - departure) + 1;
                    long preTime = System.nanoTime();
                    tds.inquiry(routeNum, departure, arrival);
                    long postTime = System.nanoTime();
                    inqExecTime += postTime - preTime;
                }
            }
            long end = System.nanoTime();
            thread2Statistics.get(Thread.currentThread().getName()).add(tryBuyCount, tryInqCount, tryRefCount, buyExecTime, inqExecTime, refExecTime, start, end);
            if (PRINT_BUY_INFO) {
                System.out.println(Thread.currentThread().getName() + " try buy count: " + tryBuyCount);
                if (buyFailedCount > 0) {
                    System.out.println(Thread.currentThread().getName() + " buy failed count: " + buyFailedCount);
                }
            }
        }
    }
}
