package ticketingsystem;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class TicketingDS implements TicketingSystem {

    private final int ROUTE_NUM;
    private final int COACH_NUM;
    private final int SEAT_NUM;
    private final int STATION_NUM;
    private final int THREAD_NUM;

    private ThreadLocal<HashSet<Long>> soldTickIds;
    private ThreadLocal<Integer> currTid;
    private AtomicInteger nextTidRegion;
    private ThreadLocal<Integer> currTidRegion;
    private static final int tidRegionSpan = 10_000_000;

    public static void main(String[] args) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    }

    public enum ImplType {
        One(ImplOne.class),
        Two(ImplTwo.class),
        Three(ImplThree.class),
        Four(ImplFour.class),
        Five(ImplFive.class),
        Six(ImplSix.class),
        Seven(ImplSeven.class);
        private final Class<? extends TicketingSystem> implClass;

        ImplType(Class<? extends TicketingSystem> implClass) {
            this.implClass = implClass;
        }
    }

    private TicketingSystem actualImpl;

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        ROUTE_NUM = routenum;
        COACH_NUM = coachnum;
        SEAT_NUM = seatnum;
        STATION_NUM = stationnum;
        THREAD_NUM = threadnum;
        if (ROUTE_NUM <= 0 || COACH_NUM <= 0 || SEAT_NUM <= 0 || STATION_NUM <= 0 || THREAD_NUM <= 0) {
            System.out.println("TicketingDS init params are invalid, plz check your input");
            assert false;
        }
        // TODO 这里指定默认实现方式。
        try {
            switchImplType(ImplType.Three);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void switchImplType(ImplType type) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        this.actualImpl = type.implClass.getConstructor(TicketingDS.class).newInstance(this);
        this.soldTickIds = new ThreadLocal<>();
        this.currTid = new ThreadLocal<>();
        this.nextTidRegion = new AtomicInteger();
        this.currTidRegion = new ThreadLocal<>();
    }

    public Class<? extends TicketingSystem> getImplClass() {
        return actualImpl.getClass();
    }

    protected boolean isParamsInvalid(int routeNum, int departure, int arrival) {
        return !checkRoute(routeNum) || !checkDepartureArrival(departure, arrival);
    }

    private boolean checkRoute(int routeNum) {
        return routeNum >= 1 && routeNum <= ROUTE_NUM;
    }

    private boolean checkDepartureArrival(int departure, int arrival) {
        return departure >= 1
                && departure <= STATION_NUM
                && arrival >= 1
                && arrival <= STATION_NUM
                && arrival > departure;
    }

    private boolean checkCoachSeatNum(int coachNum, int seatNum) {
        return coachNum >= 1 && coachNum <= COACH_NUM
                && seatNum >= 1 && seatNum <= SEAT_NUM;
    }

    private static Ticket buildTicket(long tid,
                                      String passenger,
                                      int route,
                                      int coach,
                                      int seat,
                                      int departure,
                                      int arrival) {
        Ticket t = new Ticket();
        t.tid = tid;
        t.passenger = passenger;
        t.route = route;
        t.coach = coach;
        t.seat = seat;
        t.departure = departure;
        t.arrival = arrival;
        return t;
    }

    private void initThreadLocal() {
        this.soldTickIds.set(new HashSet<>());
        this.currTid.set(0);
        this.currTidRegion.set(0);
    }

    protected int getCurrThreadNextTid() {
        if (this.currTid.get() == null) {
            initThreadLocal();
            this.currTidRegion.set(nextTidRegion.getAndIncrement());
            this.currTid.set(this.currTidRegion.get() * tidRegionSpan);
        }
        int nextTid = this.currTid.get() + 1;
        if (nextTid >= (this.currTidRegion.get() + 1) * tidRegionSpan) {
            this.currTidRegion.set(nextTidRegion.getAndIncrement());
            nextTid = this.currTidRegion.get() * tidRegionSpan;
        }
        this.currTid.set(nextTid);
        return nextTid;
    }

    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        Ticket boughtTicket = actualImpl.buyTicket(passenger, route, departure, arrival);
        if (boughtTicket != null) {
            if (this.soldTickIds.get() == null) {
                initThreadLocal();
            }
            soldTickIds.get().add(boughtTicket.tid);
        }
        return boughtTicket;
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        return actualImpl.inquiry(route, departure, arrival);
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        if (soldTickIds.get() == null) {
            initThreadLocal();
        }
        if (!soldTickIds.get().remove(ticket.tid)) {
            // 无效票
            return false;
        }
        return actualImpl.refundTicket(ticket);
    }

    private abstract class ImplCommon implements TicketingSystem {
        protected final static int S_JUST_WROTE = 0;
        protected final int[] status;
        protected final VarHandle statusVH;

        public ImplCommon() {
            status = new int[ROUTE_NUM];
            for (int i = 0; i < ROUTE_NUM; i++) {
                status[i] = S_JUST_WROTE;
            }
            statusVH = MethodHandles.arrayElementVarHandle(int[].class);
        }

        public void readStatus(int route) {
            statusVH.getVolatile(this.status, route - 1);
        }

        public void writeStatus(int route) {
            statusVH.setVolatile(this.status, route - 1, S_JUST_WROTE);
        }

        protected class Seat {
            private final int seatIdx;

            private int route;
            private int coach;
            private int seat;

            Seat(int seatIdx) {
                this.seatIdx = seatIdx;
            }

            Seat(int route, int coach, int seat) {
                this.route = route;
                this.coach = coach;
                this.seat = seat;
                this.seatIdx = (COACH_NUM * SEAT_NUM * (route - 1)) + (SEAT_NUM * (coach - 1)) + getSeat() - 1;
            }

            public int getRoute() {
                if (this.route == 0) {
                    this.route = (getSeatIdx() / (COACH_NUM * SEAT_NUM)) + 1;
                }
                return this.route;
            }

            public int getCoach() {
                if (this.coach == 0) {
                    this.coach = (getSeatIdx() - (COACH_NUM * SEAT_NUM * (getRoute() - 1))) / SEAT_NUM + 1;
                }
                return this.coach;
            }

            public int getSeat() {
                if (this.seat == 0) {
                    this.seat = (getSeatIdx() - (COACH_NUM * SEAT_NUM * (route - 1)) - (SEAT_NUM * (coach - 1))) + 1;
                }
                return this.seat;
            }

            public int getSeatIdx() {
                return this.seatIdx;
            }
        }

        protected class CoachSeatPair {
            int coach;
            int seat;
            int seatIdx;

            public CoachSeatPair(int seatIdx) {
                this.coach = seatIdx / SEAT_NUM + 1;
                this.seat = seatIdx - (coach - 1) * SEAT_NUM;
                this.seatIdx = seatIdx;
            }

            public CoachSeatPair(int coach, int seat) {
                this.coach = coach;
                this.seat = seat;
                this.seatIdx = (coach - 1) * SEAT_NUM + seat;
            }
        }
    }

    /**
     * ImplWithStationOnTop，抽象类
     * 数据结构：按照车次分别建立数据结构 Route(1,2,3,4,5):
     * 每个站点建立数据 station(1,2,3,4,5,6,7,8,9,10)：
     * 使用位向量，涵盖所有车厢的所有座位，1表示座位被所占用：i.e. [1, 1, 1, 0, 1, 0, 1]
     * 车厢概念被消除，仅保留座位概念。通过取模方式计算车厢号码。
     * 并未实现购买和退票方法，由子类实现。
     */
    private abstract class ImplWithStationOnTop extends ImplCommon {
        protected final BitSet[][] data;

        public ImplWithStationOnTop() {
            super();
            data = new BitSet[ROUTE_NUM][STATION_NUM];
            for (int i = 0; i < ROUTE_NUM; i++) {
                for (int j = 0; j < STATION_NUM; j++) {
                    data[i][j] = new BitSet(SEAT_NUM * COACH_NUM);
                }
            }
        }

        @Override
        public int inquiry(int route, int departure, int arrival) {
            // 这一步保证了内存可见性
            readStatus(route);
            if (isParamsInvalid(route, departure, arrival)) {
                return -1;
            }
            BitSet[] station2seats = data[route - 1];
            BitSet seatsMerged = (BitSet) station2seats[departure - 1].clone();
            for (int i = departure - 1; i <= arrival - 1; i++) {
                try {
                    seatsMerged.or(station2seats[i]);
                    if (seatsMerged.size() != station2seats[i].size()) {
                        throw new Exception();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return SEAT_NUM * COACH_NUM - seatsMerged.cardinality();
        }

        protected int doBuyTicket(BitSet[] station2seats, int departure, int arrival) {
            BitSet seatsMerged = (BitSet) station2seats[departure - 1].clone();
            for (int i = departure - 1; i <= arrival - 1; i++) {
                seatsMerged.or(station2seats[i]);
            }
            int seatIdx = seatsMerged.previousClearBit(SEAT_NUM * COACH_NUM - 1);
            if (seatIdx == -1) {
                // 无票
                return seatIdx;
            }
            for (int i = departure - 1; i <= arrival - 1; i++) {
                station2seats[i].set(seatIdx);
            }
            return seatIdx;
        }

        protected boolean doRefundTicket(BitSet[] station2seats, Ticket ticket) {
            // 检查座位所有departure到arrival的站是否全部被占用
            CoachSeatPair p = new CoachSeatPair(ticket.coach, ticket.seat);
            for (int i = ticket.departure - 1; i <= ticket.arrival - 1; i++) {
                if (!station2seats[i].get(p.seatIdx)) {
                    return false;
                }
            }
            for (int i = ticket.departure - 1; i <= ticket.arrival - 1; i++) {
                station2seats[i].set(p.seatIdx, false);
            }
            return true;
        }
    }

    /**
     * ImplOne数据存储结构，继承ImplWithStationOnTop
     * 购买和退票使用route级别的synchronized关键字，将车次级别的购票和退票行为进行可线性化。
     */
    private class ImplOne extends ImplWithStationOnTop {
        public ImplOne() {
            super();
        }

        @Override
        public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
            if (isParamsInvalid(route, departure, arrival)) {
                return null;
            }
            BitSet[] station2seats;
            int seatIdx;
            synchronized (station2seats = data[route - 1]) {
                seatIdx = doBuyTicket(station2seats, departure, arrival);
                writeStatus(route);
            }
            if (seatIdx == -1) {
                return null;
            }
            CoachSeatPair p = new CoachSeatPair(seatIdx);
            return buildTicket(getCurrThreadNextTid(), passenger, route, p.coach, p.seat, departure, arrival);
        }

        @Override
        public boolean refundTicket(Ticket ticket) {
            if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
                return false;
            }
            BitSet[] station2seats;
            synchronized (station2seats = data[ticket.route - 1]) {
                boolean res = doRefundTicket(station2seats, ticket);
                writeStatus(ticket.route);
                return res;
            }
        }
    }

    /**
     * ImplTwo数据存储结构与ImplOne一致，继承了ImplWithStationOnTop。
     * 购买和退票使用route级别的ReentrantLock锁，将车次级别的购票和退票行为进行可线性化。
     * 查询速度较快，写入速度较慢
     */
    private class ImplTwo extends ImplWithStationOnTop {
        private final ReentrantLock[] locks;

        public ImplTwo() {
            super();
            locks = new ReentrantLock[ROUTE_NUM];
            for (int i = 0; i < locks.length; i++) {
                locks[i] = new ReentrantLock();
            }
        }

        @Override
        public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
            if (isParamsInvalid(route, departure, arrival)) {
                return null;
            }
            int seatIdx;
            ReentrantLock lock = locks[route - 1];
            try {
                lock.lock();
                BitSet[] station2seats = data[route - 1];
                seatIdx = doBuyTicket(station2seats, departure, arrival);
            } finally {
                writeStatus(route);
                lock.unlock();
            }
            if (seatIdx == -1) {
                return null;
            }
            CoachSeatPair p = new CoachSeatPair(seatIdx);
            return buildTicket(getCurrThreadNextTid(), passenger, route, p.coach, p.seat, departure, arrival);
        }

        @Override
        public boolean refundTicket(Ticket ticket) {
            if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
                return false;
            }
            BitSet[] station2seats = data[ticket.route - 1];
            ReentrantLock lock = locks[ticket.route - 1];
            try {
                lock.lock();
                return doRefundTicket(station2seats, ticket);
            } finally {
                writeStatus(ticket.route);
                lock.unlock();
            }
        }
    }


    /**
     * ImplUsingOneArray
     * 数据结构：只建立一个超长数组。数组长度为 ROUTE_NUM * COACH_NUM * SEAT_NUM
     * 每个元素为int或long类型，该数字的每个比特表示在某个车站是否有人已经占座。
     * 当车站数量<32时，使用int数组，当车站数量>32且<=64时，使用long数组。
     * <p>
     * 写入数据时，使用setOccupiedInverted方法，默认使用CAS方法写入数据，避免加锁。
     */
    private abstract class ImplUsingOneArray extends ImplCommon {
        /**
         * vh 使用笔记：
         * getOpaque(x) 确保读取内存中的x的数据，避免读cache。
         * getAcquire(x)通常与setRelease(x)搭配 setAcquire(x)通常与getRelease(x)搭配。
         * getAcquire保证在该行代码之后的代码不会重排序到这行之前执行。
         * setRelease保证在该行代码之前的代码不会重排序到这行之后执行。
         * getVolatile 分别保证这行代码的前面与后面的代码不会重排序到后面与前面。
         */
        protected VarHandle vh;
        protected int[] intArray;

        protected int[] stationMasks;
        protected int[][] dep2arrOnesMasks;
        protected int[][] dep2arrZerosMasks;

        public ImplUsingOneArray() {
            super();
            initDataArray();
            initMasks();
        }

        protected void initDataArray() {
            intArray = new int[ROUTE_NUM * COACH_NUM * SEAT_NUM];
            vh = MethodHandles.arrayElementVarHandle(int[].class);
        }

        protected void initMasks() {
            int maxStation = 32;
            stationMasks = new int[maxStation];
            for (int i = 0; i < maxStation; i++) {
                stationMasks[i] = 1 << i;
            }
            dep2arrOnesMasks = new int[maxStation][];
            dep2arrZerosMasks = new int[maxStation][];
            for (int i = 0; i < dep2arrOnesMasks.length; i++) {
                dep2arrOnesMasks[i] = new int[maxStation];
                dep2arrZerosMasks[i] = new int[maxStation];
            }
            for (int i = 0; i < maxStation; i++) {
                for (int j = i + 1; j < maxStation; j++) {
                    int l = j - i;
                    int ones = ((~0) >>> (maxStation - l - 1));
                    ones <<= i;
                    dep2arrOnesMasks[i][j] = ones;
                    dep2arrZerosMasks[i][j] = ~ones;
                }
            }
        }

        protected boolean setOccupiedInverted(int route, int seatIdx, int departure, int arrival, boolean occupied, boolean tryWithLoop) {
            int expectedSeatInfo, newSeatInfo;
            for (; ; ) {
                expectedSeatInfo = readSeatInfoPlain(seatIdx);
                if (!checkSeatInfo(expectedSeatInfo, departure, arrival, !occupied)) {
                    // 从departure到arrival站区间内，有与occupied相反的位数，即
                    // 若occupied为true，即我们想将departure到arrival的站点全部设置为true，首先需要确保这些站点当前为false。
                    // 若有invert，则认为当前座位已被占，无法购买。
                    return false;
                }
                newSeatInfo = expectedSeatInfo;
                if (occupied) {
                    newSeatInfo |= dep2arrOnesMasks[departure - 1][arrival - 1];
                } else {
                    newSeatInfo &= dep2arrZerosMasks[departure - 1][arrival - 1];
                }
                boolean fail = !vh.compareAndSet(intArray, seatIdx, expectedSeatInfo, newSeatInfo);
                if (!fail) {
                    writeStatus(route);
                    return true;
                }
                if (!tryWithLoop) {
                    return false;
                }
            }
        }

        protected boolean checkSeatInfo(int seatInfo, int departure, int arrival, boolean occupied) {
            if (occupied) {
                int mask = dep2arrOnesMasks[departure - 1][arrival - 1];
                return mask == (seatInfo & mask);
            } else {
                int mask = dep2arrOnesMasks[departure - 1][arrival - 1];
                return 0 == (seatInfo & mask);
            }
        }

        protected int readSeatInfoPlain(int seatIdx) {
            return intArray[seatIdx];
        }

        @Override
        public int inquiry(int route, int departure, int arrival) {
            if (isParamsInvalid(route, departure, arrival)) {
                return -1;
            }
            // 保证内存可见性
            readStatus(route);
            int res = 0;
            for (int i = (route - 1) * COACH_NUM * SEAT_NUM; i < route * COACH_NUM * SEAT_NUM; i++) {
                // 保证可见性后使用plain的方式读取即可
                if (checkSeatInfo(readSeatInfoPlain(i), departure, arrival, false)) {
                    // 当前座位idx为i，如果检查从departure 到 arrival之间的位数都为false，即可证明该座位的该区间没有被占用，可以增加余票数量。
                    res++;
                }
            }
            return res;
        }

        @Override
        public boolean refundTicket(Ticket ticket) {
            if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
                return false;
            }
            Seat s = new Seat(ticket.route, ticket.coach, ticket.seat);
            return setOccupiedInverted(ticket.route, s.getSeatIdx(), ticket.departure, ticket.arrival, false, true);
        }
    }

    /**
     * 继承ImplUsingOneArray，只更改buyTicket逻辑
     * 用随机的方式找可以买票的座位。
     */
    private class ImplThree extends ImplUsingOneArray {
        public ImplThree() {
            super();
        }

        // 随机找，不依赖ConcurrentSkipListSet
        @Override
        public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
            readStatus(route);
            if (isParamsInvalid(route, departure, arrival)) {
                return null;
            }
            int left = (route - 1) * COACH_NUM * SEAT_NUM;
            int right = route * COACH_NUM * SEAT_NUM - 1;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int startIdx = random.nextInt(left, right + 1);
            int currIdx = startIdx;
            boolean buyResult = false;
            while (currIdx >= left) {
                buyResult = setOccupiedInverted(route, currIdx, departure, arrival, true, true);
                if (buyResult) {
                    break;
                }
                currIdx--;
            }
            if (!buyResult) {
                currIdx = startIdx + 1;
                while (currIdx <= right) {
                    buyResult = setOccupiedInverted(route, currIdx, departure, arrival, true, true);
                    if (buyResult) {
                        break;
                    }
                    currIdx++;
                }
            }
            if (!buyResult) {
                // 无余票
                return null;
            }
            Seat s = new Seat(currIdx);
            return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);
        }
    }

    /**
     * 在方案三的基础上修改购票策略。
     * 将本线程刚刚退票的那些座位记录下来，认为这些座位空闲下来的概率更高，在接下来购票时首先检查这些座位情况。
     */
    private class ImplFour extends ImplUsingOneArray {
        ThreadLocal<Map<Integer, List<Integer>>> justRefundSeatIdxes;

        public ImplFour() {
            super();
            justRefundSeatIdxes = new ThreadLocal<>();
        }

        @Override
        public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
            if (isParamsInvalid(route, departure, arrival)) {
                return null;
            }
            readStatus(route);
            boolean buyResult = false;
            int currIdx = -1;

            Map<Integer, List<Integer>> mapSeatIdxes = justRefundSeatIdxes.get();
            List<Integer> seatIdxes = mapSeatIdxes == null ? null : mapSeatIdxes.get(route);
            while (seatIdxes != null && seatIdxes.size() > 0) {
                currIdx = seatIdxes.remove(seatIdxes.size() - 1);
                buyResult = setOccupiedInverted(route, currIdx, departure, arrival, true, true);
                if (buyResult) {
                    break;
                }
            }

            int left = (route - 1) * COACH_NUM * SEAT_NUM;
            int right = route * COACH_NUM * SEAT_NUM - 1;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int startIdx = random.nextInt(left, right + 1);
            if (!buyResult) {
                currIdx = startIdx;
                while (currIdx >= left) {
                    buyResult = setOccupiedInverted(route, currIdx, departure, arrival, true, true);
                    if (buyResult) {
                        break;
                    }
                    currIdx--;
                }
            }
            if (!buyResult) {
                currIdx = startIdx + 1;
                while (currIdx <= right) {
                    buyResult = setOccupiedInverted(route, currIdx, departure, arrival, true, true);
                    if (buyResult) {
                        break;
                    }
                    currIdx++;
                }
            }
            if (!buyResult) {
                // 无余票
                return null;
            }
            assert currIdx != -1;
            Seat s = new Seat(currIdx);
            return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);
        }

        @Override
        public boolean refundTicket(Ticket ticket) {
            if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
                return false;
            }
            Seat s = new Seat(ticket.route, ticket.coach, ticket.seat);
            boolean suc = setOccupiedInverted(ticket.route, s.getSeatIdx(), ticket.departure, ticket.arrival, false, true);
            if (suc) {
                if (justRefundSeatIdxes.get() == null) {
                    justRefundSeatIdxes.set(new HashMap<>());
                    for (int i = 0; i < ROUTE_NUM; i++) {
                        justRefundSeatIdxes.get().put(i + 1, new ArrayList<>(COACH_NUM * SEAT_NUM));
                    }
                }
                if (ticket.arrival - ticket.departure >= 5)
                    justRefundSeatIdxes.get().get(s.route).add(s.seatIdx);
            }
            return suc;
        }
    }

    /**
     * 使用boolean[][][]数组代替BitSet的方案。这种方案能够在座位粒度上进行同步。
     * 是ImplOne数据结构的变种。但是由于使用boolean数组，导致不能利用位图的快速合并功能，其查询的复杂度大大提升。效率比较一般。
     */
    private class ImplFive extends ImplCommon {
        protected final boolean[][][] data;

        protected final AtomicBoolean[][] seatFlags;

        public ImplFive() {
            super();
            data = new boolean[ROUTE_NUM][STATION_NUM][COACH_NUM * SEAT_NUM];
            seatFlags = new AtomicBoolean[ROUTE_NUM][COACH_NUM * SEAT_NUM];
            for (AtomicBoolean[] seatFlag : seatFlags) {
                for (int i = 0; i < seatFlag.length; i++) {
                    seatFlag[i] = new AtomicBoolean(false);
                }
            }
        }

        @Override
        public int inquiry(int route, int departure, int arrival) {
            if (isParamsInvalid(route, departure, arrival)) {
                return -1;
            }
            // 保证内存可见性。
            readStatus(route);
            boolean[] seatsMerged = inquiryMergedSeats(route, departure, arrival);
            int zeroCount = 0;
            for (boolean occupied : seatsMerged) {
                if (!occupied) {
                    zeroCount++;
                }
            }
            return zeroCount;
        }

        private boolean[] inquiryMergedSeats(int route, int departure, int arrival) {
            boolean[][] station2seats = data[route - 1];
            boolean[] seatsMerged = new boolean[COACH_NUM * SEAT_NUM];
            for (int i = departure - 1; i <= arrival - 1; i++) {
                boolean[] seats = station2seats[i];
                for (int j = 0; j < seats.length; j++) {
                    seatsMerged[j] = seatsMerged[j] || seats[j];
                }
            }
            return seatsMerged;
        }

        @Override
        public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            readStatus(route);
            try {
                boolean[][] station2seats = data[route - 1];
                while (true) {
                    boolean[] seatsMerged = inquiryMergedSeats(route, departure, arrival);
                    boolean haveFoundAnEmpty = false;
                    int start = random.nextInt(COACH_NUM * SEAT_NUM);
                    int count = 0;
                    findNextEmpty:
                    for (int seatIdx = start; count < COACH_NUM * SEAT_NUM; seatIdx = (seatIdx + 1) % (SEAT_NUM * COACH_NUM)) {
                        count++;
                        if (seatsMerged[seatIdx]) {
                            continue;
                        }
                        // find a empty seat
                        haveFoundAnEmpty = true;
                        for (int j = 0; j < 5; j++) {
                            if (!seatFlags[route - 1][seatIdx].compareAndSet(false, true)) {
                                Thread.sleep(0, random.nextInt(10));
                            } else {
                                // do check again
                                for (int currStation = departure - 1; currStation <= arrival - 1; currStation++) {
                                    if (station2seats[currStation][seatIdx]) {
                                        seatFlags[route - 1][seatIdx].setOpaque(false);
                                        continue findNextEmpty;
                                    }
                                }
                                for (int currStation = departure - 1; currStation <= arrival - 1; currStation++) {
                                    station2seats[currStation][seatIdx] = true;
                                }
                                seatFlags[route - 1][seatIdx].setOpaque(false);
                                CoachSeatPair p = new CoachSeatPair(seatIdx);
                                return buildTicket(getCurrThreadNextTid(), passenger, route, p.coach, p.seat, departure, arrival);
                            }
                        }
                    }
                    if (!haveFoundAnEmpty) {
                        return null;
                    }
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.exit(-1);
                return null;
            } finally {
                writeStatus(route);
            }
        }


        @Override
        public boolean refundTicket(Ticket ticket) {
            boolean[][] station2seats = data[ticket.route - 1];
            // 检查座位所有departure到arrival的站是否全部被占用
            CoachSeatPair p = new CoachSeatPair(ticket.coach, ticket.seat);
            for (int i = ticket.departure - 1; i <= ticket.arrival - 1; i++) {
                if (!station2seats[i][p.seatIdx]) {
                    return false;
                }
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            while (!seatFlags[ticket.route - 1][p.seatIdx].compareAndSet(false, true)) {
                try {
                    Thread.sleep(0, random.nextInt(5));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            for (int i = ticket.departure - 1; i <= ticket.arrival - 1; i++) {
                station2seats[i][p.seatIdx] = false;
            }
            seatFlags[ticket.route - 1][p.seatIdx].setOpaque(false);
            writeStatus(ticket.route);
            return true;
        }
    }

    /**
     * ImplUsingOneArray
     * 数据结构：只建立一个超长数组。数组长度为 ROUTE_NUM * COACH_NUM * SEAT_NUM / (64 / STATION_NUM)
     * 每个元素为long类型，该数字的全部比特按照车站数量进行分组。
     * 如64位的数字，车站有10个，则可以使用低60位表示6个座位的全部车站。
     * <p>
     * 写入数据时，使用setOccupiedInverted方法，默认使用CAS方法写入数据，避免加锁。
     */
    private class ImplSix extends ImplCommon {
        /**
         * vh 使用笔记：
         * getOpaque(x) 确保读取内存中的x的数据，避免读cache。
         * getAcquire(x)通常与setRelease(x)搭配 setAcquire(x)通常与getRelease(x)搭配。
         * getAcquire保证在该行代码之后的代码不会重排序到这行之前执行。
         * setRelease保证在该行代码之前的代码不会重排序到这行之后执行。
         * getVolatile 分别保证这行代码的前面与后面的代码不会重排序到后面与前面。
         */
        protected VarHandle vh;
        protected long[][] longArray;

        protected long[][][] dep2arr2posMasks;

        protected int[][] crowd;

        protected class Seat {
            // 表示该座位在该车次的相对位置。
            private final int seatIdxOnRoute;
            private final int route;
            private int coach;
            private int seat;
            private int seatContainerElemIdx;
            private int seatPosInContainerElem;

            Seat(int route, int seatIdxOnRoute) {
                this.route = route;
                this.seatIdxOnRoute = seatIdxOnRoute;
                getCoach();
                getSeat();
                getSeatPosInContainerElem();
                getSeatContainerElemIdx();
            }

            Seat(int route, int coach, int seat) {
                this.route = route;
                this.coach = coach;
                this.seat = seat;
                this.seatIdxOnRoute = (SEAT_NUM * (coach - 1)) + seat - 1;
                getSeatPosInContainerElem();
                getSeatContainerElemIdx();
            }

            public int getRoute() {
                return this.route;
            }

            public int getCoach() {
                if (this.coach == 0) {
                    this.coach = getSeatIdxOnRoute() / SEAT_NUM + 1;
                }
                return this.coach;
            }

            public int getSeat() {
                if (this.seat == 0) {
                    this.seat = getSeatIdxOnRoute() - (SEAT_NUM * (coach - 1)) + 1;
                }
                return this.seat;
            }

            public int getSeatIdxOnRoute() {
                return this.seatIdxOnRoute;
            }

            public int getSeatContainerElemIdx() {
                if (this.seatContainerElemIdx == 0) {
                    this.seatContainerElemIdx = getSeatIdxOnRoute() / seatsPerLong;
                }
                return this.seatContainerElemIdx;
            }

            public int getSeatPosInContainerElem() {
                if (this.seatPosInContainerElem == 0) {
                    this.seatPosInContainerElem = getSeatIdxOnRoute() % seatsPerLong;
                }
                return this.seatPosInContainerElem;
            }
        }

        // 使用64位存储多个座位的信息，每个座位包含其全部车站的信息。
        protected final int seatsPerLong = 64 / STATION_NUM;

        protected void initMasks() {
            dep2arr2posMasks = new long[STATION_NUM][STATION_NUM][seatsPerLong];
            for (int i = 0; i < dep2arr2posMasks.length; i++) {
                dep2arr2posMasks[i] = new long[STATION_NUM][seatsPerLong];
                for (int j = i + 1; j < dep2arr2posMasks[i].length; j++) {
                    dep2arr2posMasks[i][j] = new long[seatsPerLong];
                }
            }
            for (int i = 0; i < STATION_NUM; i++) {
                for (int j = i + 1; j < STATION_NUM; j++) {
                    for (int k = 0; k < seatsPerLong; k++) {
                        if (k == 0) {
                            int l = j - i;
                            int ones = 0;
                            for (int m = 0; m < STATION_NUM; m++) {
                                ones |= 1 << m;
                            }

                            int onesMask = (ones >> (STATION_NUM - l) - 1) << i;
                            dep2arr2posMasks[i][j][0] = onesMask;
                        } else {
                            dep2arr2posMasks[i][j][k] = dep2arr2posMasks[i][j][k - 1] << STATION_NUM;
                        }
                    }
                }
            }
        }

        public ImplSix() {
            super();
            initDataArray();
            initMasks();
        }

        protected final int partCount = 8;

        protected final int seatsPerPart = COACH_NUM * SEAT_NUM / partCount;

        protected void initDataArray() {
            crowd = new int[ROUTE_NUM][partCount];
            for (int[] ints : crowd) {
                Arrays.fill(ints, seatsPerPart * STATION_NUM);
            }
            longArray = new long[ROUTE_NUM][COACH_NUM * SEAT_NUM / seatsPerLong + 1];
            vh = MethodHandles.arrayElementVarHandle(long[].class);
        }

        // 返回seatIdxOnRoute
        protected int trySetOccupied(int route, int seatContainerElemIdx, int departure, int arrival, boolean tryWithLoop) {
            long expectedSeatInfo, newSeatInfo;
            long[] seatsForRoute = longArray[route - 1];
            for (; ; ) {
                expectedSeatInfo = seatsForRoute[seatContainerElemIdx];
                newSeatInfo = expectedSeatInfo;
                long[] masks = dep2arr2posMasks[departure - 1][arrival - 1];
                boolean found = false;
                int seatPos = -1;
                for (int i = 0; i < masks.length; i++) {
                    if (seatContainerElemIdx == seatsForRoute.length - 1) {
                        if ((seatsForRoute.length - 1) * seatsPerLong + i + 1 > COACH_NUM * SEAT_NUM) {
                            // 数组最后一位，可能会多出一些座位来。防止在他们上购买。
                            break;
                        }
                    }
                    long mask;
                    // ones mask
                    if (0 == (expectedSeatInfo & (mask = masks[i]))) {
                        newSeatInfo |= mask;
                        found = true;
                        seatPos = i;
                        break;
                    }
                }
                if (!found) {
                    return -1;
                }
                boolean fail = !vh.compareAndSet(longArray[route - 1], seatContainerElemIdx, expectedSeatInfo, newSeatInfo);
                if (!fail) {
                    writeStatus(route);
                    return seatContainerElemIdx * seatsPerLong + seatPos;
                }
                if (!tryWithLoop) {
                    return seatContainerElemIdx * seatsPerLong + seatPos;
                }
            }
        }

        protected boolean freeOccupiedForSpecificSeat(int route, int seatContainerElemIdx, int seatPosInContainerElem, int departure, int arrival, boolean tryWithLoop) {
            long expectedSeatInfo, newSeatInfo;
            long[] seatsForRoute = longArray[route - 1];
            for (; ; ) {
                expectedSeatInfo = seatsForRoute[seatContainerElemIdx];
                newSeatInfo = expectedSeatInfo;
                long[] masks = dep2arr2posMasks[departure - 1][arrival - 1];
                long onesMask = masks[seatPosInContainerElem];
                if (onesMask == (expectedSeatInfo & onesMask)) {
                    newSeatInfo &= ~onesMask;
                } else {
                    // 不符合要求
                    return false;
                }
                boolean fail = !vh.compareAndSet(longArray[route - 1], seatContainerElemIdx, expectedSeatInfo, newSeatInfo);
                if (!fail) {
                    writeStatus(route);
                    return true;
                }
                if (!tryWithLoop) {
                    return false;
                }
            }
        }

        protected int getCorrespondSeatCount(long seatInfo, int departure, int arrival, boolean occupied) {
            int count = 0;
            long[] masks = dep2arr2posMasks[departure - 1][arrival - 1];
            if (occupied) {
                for (long mask : masks) {
                    if (mask == (seatInfo & mask)) {
                        count += 1;
                    }
                }
            } else {
                for (long mask : masks) {
                    if (0 == (seatInfo & mask)) {
                        count += 1;
                    }
                }
            }
            return count;
        }

        @Override
        public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
            readStatus(route);
            if (isParamsInvalid(route, departure, arrival)) {
                return null;
            }
            long[] seatsForRoute = longArray[route - 1];

            int[] crowdParts = crowd[route - 1].clone();
            Map<Integer, Integer> m = new HashMap<>();
            for (int i = 0; i < crowdParts.length; i++) {
                m.put(crowdParts[i], i);
            }
            Arrays.sort(crowdParts);

            int boughtSeatIdxOnRoute = -1;
            int boughtInPart = -1;
            for (int i = crowdParts.length - 1; i >= 0; i--) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                int partIdx = m.get(crowdParts[i]);
                int left = partIdx * seatsPerPart / seatsPerLong;
                int right = (partIdx + 1) * seatsPerPart / seatsPerLong;
                if (partIdx == crowdParts.length - 1) {
                    right++;
                }
                int startIdx = random.nextInt(left, Math.min(seatsForRoute.length, right));
                int currIdx = startIdx;
                while (currIdx >= left) {
                    boughtSeatIdxOnRoute = trySetOccupied(route, currIdx, departure, arrival, true);
                    if (boughtSeatIdxOnRoute != -1) {
                        boughtInPart = partIdx;
                        break;
                    }
                    currIdx--;
                }
                if (boughtSeatIdxOnRoute == -1) {
                    currIdx = startIdx + 1;
                    while (currIdx < right) {
                        boughtSeatIdxOnRoute = trySetOccupied(route, currIdx, departure, arrival, true);
                        if (boughtSeatIdxOnRoute != -1) {
                            boughtInPart = partIdx;
                            break;
                        }
                        currIdx++;
                    }
                }
                if (boughtSeatIdxOnRoute != -1) {
                    break;
                }
            }
            if (boughtSeatIdxOnRoute == -1) {
                // 无余票
                return null;
            }
            assert boughtInPart != -1;
            Seat s = new Seat(route, boughtSeatIdxOnRoute);
            crowd[route - 1][boughtInPart] -= (arrival - departure + 1);
            return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);
        }

        @Override
        public int inquiry(int route, int departure, int arrival) {
            if (isParamsInvalid(route, departure, arrival)) {
                return -1;
            }
            // 保证内存可见性
            readStatus(route);
            int res = 0;
            long[] seatsForRoute = longArray[route - 1];
            for (long l : seatsForRoute) {
                res += getCorrespondSeatCount(l, departure, arrival, false);
            }
            res -= seatsPerLong - COACH_NUM * SEAT_NUM % seatsPerLong;
            return res;
        }

        @Override
        public boolean refundTicket(Ticket ticket) {
            if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
                return false;
            }
            Seat seat = new Seat(ticket.route, ticket.coach, ticket.seat);
            boolean suc = freeOccupiedForSpecificSeat(ticket.route, seat.getSeatContainerElemIdx(), seat.getSeatPosInContainerElem(), ticket.departure, ticket.arrival, true);
            crowd[ticket.route - 1][seat.getSeatIdxOnRoute() / (seatsPerPart)] += (ticket.arrival - ticket.departure + 1);
            return suc;
        }
    }

    /**
     * 继承于ImplSix，它的购票策略不使用热点数据的策略，直接采取随机的方式。
     */
    private class ImplSeven extends ImplSix {
        public ImplSeven() {
            super();
        }

        @Override
        public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
            readStatus(route);
            if (isParamsInvalid(route, departure, arrival)) {
                return null;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            long[] seatsForRoute = longArray[route - 1];
            int startIdx = random.nextInt(seatsForRoute.length);

            int currIdx = startIdx;
            int boughtSeatIdxOnRoute = -1;
            while (currIdx >= 0) {
                boughtSeatIdxOnRoute = trySetOccupied(route, currIdx, departure, arrival, true);
                if (boughtSeatIdxOnRoute != -1) {
                    break;
                }
                currIdx--;
            }
            if (boughtSeatIdxOnRoute == -1) {
                currIdx = startIdx + 1;
                while (currIdx < seatsForRoute.length) {
                    boughtSeatIdxOnRoute = trySetOccupied(route, currIdx, departure, arrival, true);
                    if (boughtSeatIdxOnRoute != -1) {
                        break;
                    }
                    currIdx++;
                }
            }
            if (boughtSeatIdxOnRoute == -1) {
                // 无余票
                return null;
            }
            Seat s = new Seat(route, boughtSeatIdxOnRoute);
            return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);
        }

        @Override
        public boolean refundTicket(Ticket ticket) {
            if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
                return false;
            }
            Seat seat = new Seat(ticket.route, ticket.coach, ticket.seat);
            return freeOccupiedForSpecificSeat(ticket.route, seat.getSeatContainerElemIdx(), seat.getSeatPosInContainerElem(), ticket.departure, ticket.arrival, true);
        }

    }

}
