package ticketingsystem;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;
import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class TicketingDS implements TicketingSystem {

    private final int ROUTE_NUM;
    private final int COACH_NUM;
    private final int SEAT_NUM;
    private final int STATION_NUM;
    private final int THREAD_NUM;

    private ConcurrentSkipListSet<Long> soldTickIds;

    public static void main(String[] args) {

    }

    protected AtomicLong currTid = new AtomicLong(1);

    public enum ImplType {
        One(ImplOne.class),
        Two(ImplTwo.class),
        Three(ImplThree.class),
        Four(ImplFour.class);
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
            switchImplType(ImplType.One);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void switchImplType(ImplType type) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        this.actualImpl = type.implClass.getConstructor(TicketingDS.class).newInstance(this);
        this.currTid = new AtomicLong(1);
        this.soldTickIds = new ConcurrentSkipListSet<>();
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

    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        Ticket boughtTicket = actualImpl.buyTicket(passenger, route, departure, arrival);
        if (boughtTicket != null) {
            soldTickIds.add(boughtTicket.tid);
        }
        return boughtTicket;
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        return actualImpl.inquiry(route, departure, arrival);
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        if (!soldTickIds.remove(ticket.tid)) {
            // 无效票
            return false;
        }
        return actualImpl.refundTicket(ticket);
    }

    /**
     * ImplWithStationOnTop，抽象类
     * 数据结构：按照车次分别建立数据结构 Route(1,2,3,4,5):
     * 每个站点建立数据 station(1,2,3,4,5,6,7,8,9,10)：
     * 使用位向量，涵盖所有车厢的所有座位，1表示座位被所占用：i.e. [1, 1, 1, 0, 1, 0, 1]
     * 车厢概念被消除，仅保留座位概念。通过取模方式计算车厢号码。
     * 并未实现购买和退票方法，由子类实现。
     */
    abstract class ImplWithStationOnTop implements TicketingSystem {
        protected final BitSet[][] data;

        protected final ThreadLocalRandom random = ThreadLocalRandom.current();

        // status == 0 刚刚写完
        // status == -1 正在写
        protected final static int S_JUST_WROTE = 0;
        protected final static int S_WRITING = -1;
        protected final int[] status;
        protected final VarHandle statusVH;

        public ImplWithStationOnTop() {
            data = new BitSet[ROUTE_NUM][STATION_NUM];
            status = new int[ROUTE_NUM];
            for (int i = 0; i < ROUTE_NUM; i++) {
                for (int j = 0; j < STATION_NUM; j++) {
                    data[i][j] = new BitSet(SEAT_NUM * COACH_NUM);
                }
                status[i] = S_JUST_WROTE;
            }
            statusVH = MethodHandles.arrayElementVarHandle(int[].class);
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

        @Override
        public int inquiry(int route, int departure, int arrival) {
            // 这一步保证了内存可见性
            statusVH.getVolatile(this.status, route - 1);
            if (isParamsInvalid(route, departure, arrival)) {
                return -1;
            }
            BitSet[] station2seats = data[route - 1];
            BitSet seatsMerged = (BitSet) station2seats[departure - 1].clone();
            for (int i = departure; i <= arrival - 1; i++) {
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
            for (int i = departure; i <= arrival - 1; i++) {
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
    class ImplOne extends ImplWithStationOnTop {
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
                statusVH.setVolatile(this.status, route - 1, S_WRITING);
                // status[route - 1] = S_WRITING;
                seatIdx = doBuyTicket(station2seats, departure, arrival);
                // status[route - 1] = 0;
                statusVH.setVolatile(this.status, route - 1, S_JUST_WROTE);
            }
            if (seatIdx == -1) {
                return null;
            }
            CoachSeatPair p = new CoachSeatPair(seatIdx);
            return buildTicket(currTid.getAndIncrement(), passenger, route, p.coach, p.seat, departure, arrival);
        }

        @Override
        public boolean refundTicket(Ticket ticket) {
            if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
                return false;
            }
            BitSet[] station2seats;
            synchronized (station2seats = data[ticket.route - 1]) {
                // status[ticket.route - 1] = -1;
                statusVH.setVolatile(this.status, ticket.route - 1, S_WRITING);
                boolean res = doRefundTicket(station2seats, ticket);
                // status[ticket.route - 1] = 0;
                statusVH.setVolatile(this.status, ticket.route - 1, S_JUST_WROTE);
                return res;
            }
        }
    }

    /**
     * ImplTwo数据存储结构与ImplOne一致，继承了ImplWithStationOnTop。
     * 购买和退票使用route级别的ReentrantLock锁，将车次级别的购票和退票行为进行可线性化。
     * 查询速度较快，写入速度较慢
     */
    class ImplTwo extends ImplWithStationOnTop {
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
                statusVH.setVolatile(this.status, route - 1, S_WRITING);
                // status[route - 1] = S_WRITING;
                BitSet[] station2seats = data[route - 1];
                seatIdx = doBuyTicket(station2seats, departure, arrival);
            } finally {
                statusVH.setVolatile(this.status, route - 1, S_JUST_WROTE);
                // status[route - 1] = S_JUST_WROTE;
                lock.unlock();
            }
            if (seatIdx == -1) {
                return null;
            }
            CoachSeatPair p = new CoachSeatPair(seatIdx);
            return buildTicket(currTid.getAndIncrement(), passenger, route, p.coach, p.seat, departure, arrival);
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
                statusVH.setVolatile(this.status, ticket.route - 1, S_WRITING);
                // status[ticket.route - 1] = -1;
                return doRefundTicket(station2seats, ticket);
            } finally {
                statusVH.setVolatile(this.status, ticket.route - 1, S_JUST_WROTE);
                // status[ticket.route - 1] = 0;
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
    abstract class ImplUsingOneArray implements TicketingSystem {
        /**
         * vh 使用笔记：
         * getOpaque(x) 确保读取内存中的x的数据，避免读cache。
         * getAcquire(x)通常与setRelease(x)搭配 setAcquire(x)通常与getRelease(x)搭配。
         * getAcquire保证在该行代码之后的代码不会重排序到这行之前执行。
         * setRelease保证在该行代码之前的代码不会重排序到这行之后执行。
         * getVolatile 分别保证这行代码的前面与后面的代码不会重排序到后面与前面。
         */
        protected VarHandle vh;
        protected long[] longArray;
        protected int[] intArray;
        protected boolean useLong;
        private final ThreadLocalRandom random = ThreadLocalRandom.current();

        protected class Seat implements Comparable<Seat> {
            private final int seatIdx;
            private final int availableStationsCount;

            private int route;
            private int coach;
            private int seat;

            Seat(int seatIdx, int availableStationsCount) {
                this.seatIdx = seatIdx;
                this.availableStationsCount = availableStationsCount;
            }

            Seat(int route, int coach, int seat) {
                this.route = route;
                this.coach = coach;
                this.seat = seat;
                this.seatIdx = (COACH_NUM * SEAT_NUM * (route - 1)) + (SEAT_NUM * (coach - 1)) + getSeat() - 1;
                this.availableStationsCount = 0;
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

            public int getAvailableStationsCount() {
                return availableStationsCount;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Seat seat = (Seat) o;
                return getSeatIdx() == seat.getSeatIdx();
            }

            @Override
            public int hashCode() {
                return Objects.hash(getSeatIdx());
            }

            @Override
            public int compareTo(Seat o) {
                int ac1 = getAvailableStationsCount();
                int ac2 = o.getAvailableStationsCount();
                if (ac1 > ac2) {
                    return 1;
                } else if (ac1 == ac2) {
                    return getSeatIdx() - o.getSeatIdx();
                } else {
                    return -1;
                }
            }
        }

        public ImplUsingOneArray() {
            initUseLong();
            initDataArray();
        }

        protected void initUseLong() {
            if (STATION_NUM <= 32) {
                useLong = false;
            } else if (STATION_NUM <= 64) {
                useLong = true;
            } else {
                System.out.println("in ImplThree STATION_NUM cannot bigger than 64");
                assert false;
            }
        }

        protected void initDataArray() {
            if (useLong) {
                longArray = new long[ROUTE_NUM * COACH_NUM * SEAT_NUM];
                vh = MethodHandles.arrayElementVarHandle(long[].class);
            } else {
                intArray = new int[ROUTE_NUM * COACH_NUM * SEAT_NUM];
                vh = MethodHandles.arrayElementVarHandle(int[].class);
            }
        }

        protected boolean setOccupiedInverted(int route, int seatIdx, int departure, int arrival, boolean occupied) {
            long expectedSeatInfo, newSeatInfo;
            do {
                expectedSeatInfo = readSeatInfoOpaque(seatIdx);
                // TODO 是否能满足，有余票时一定能买到票的需求？
                if (!checkSeatInfo(expectedSeatInfo, departure, arrival, !occupied)) {
                    // 从departure到arrival站区间内，有与occupied相反的位数，即
                    // 若occupied为true，即我们想将departure到arrival的站点全部设置为true，首先需要确保这些站点当前为false。
                    // 若有invert，则认为当前座位已被占，无法购买。
                    return false;
                }
                newSeatInfo = expectedSeatInfo;
                for (int i = departure - 1; i <= arrival - 1; i++) {
                    if (occupied) {
                        // 置1
                        newSeatInfo = newSeatInfo | (0x1L << i);
                    } else {
                        // 置0
                        newSeatInfo = newSeatInfo & ~(0x1L << i);
                    }
                }
            } while (useLong ?
                    !vh.compareAndSet(longArray, seatIdx, expectedSeatInfo, newSeatInfo) :
                    !vh.compareAndSet(intArray, seatIdx, (int) expectedSeatInfo, (int) newSeatInfo));
            return true;
        }

        protected boolean checkSeatInfo(long seatInfo, int departure, int arrival, boolean occupied) {
            long flag = occupied ? 1 : 0;
            for (int i = departure - 1; i <= arrival - 1; i++) {
                if (flag != (0x1 & (seatInfo >> i))) {
                    return false;
                }
            }
            return true;
        }

        private int getAvailableStationsCount(long seatInfo) {
            int count = 0;
            for (int i = 0; i <= STATION_NUM - 1; i++) {
                if (0 == (0x1 & (seatInfo >> i))) {
                    count++;
                }
            }
            return count;
        }

        private int getSeatIdx(int route, int coach, int seat) {
            return (route - 1) * (COACH_NUM * SEAT_NUM) + (coach - 1) * SEAT_NUM + seat - 1;
        }

        protected long readSeatInfoPlain(int seatIdx) {
            if (useLong) {
                return longArray[seatIdx];
            } else {
                return intArray[seatIdx];
            }
        }

        @SuppressWarnings("all")
        protected long readSeatInfoOpaque(int seatIdx) {
            if (useLong) {
                return (long) vh.getOpaque(longArray, seatIdx);
            } else {
                return (int) vh.getOpaque(intArray, seatIdx);
            }
        }

        @Override
        public int inquiry(int route, int departure, int arrival) {
            if (isParamsInvalid(route, departure, arrival)) {
                return -1;
            }
            int res = 0;
            for (int i = (route - 1) * COACH_NUM * SEAT_NUM; i < route * COACH_NUM * SEAT_NUM; i++) {
                if (checkSeatInfo(readSeatInfoOpaque(i), departure, arrival, false)) {
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
            return setOccupiedInverted(ticket.route, s.getSeatIdx(), ticket.departure, ticket.arrival, false);
        }
    }

    /**
     * 继承ImplThree，只更改buyTicket逻辑
     * 不依赖ConcurrentSkipListSet，用随机的方式找可以买票的座位。
     */
    class ImplThree extends ImplUsingOneArray {
        public ImplThree() {
            super.initUseLong();
            super.initDataArray();
        }

        // 随机找，不依赖ConcurrentSkipListSet
        @Override
        public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
            if (isParamsInvalid(route, departure, arrival)) {
                return null;
            }
            int left = (route - 1) * COACH_NUM * SEAT_NUM;
            int right = route * COACH_NUM * SEAT_NUM - 1;
            int startIdx = super.random.nextInt(left, right + 1);
            int currIdx = startIdx;
            boolean buyResult = false;
            while (currIdx >= left) {
                if (checkSeatInfo(readSeatInfoPlain(currIdx), departure, arrival, true)) {
                    currIdx--;
                    continue;
                }
                buyResult = setOccupiedInverted(route, currIdx, departure, arrival, true);
                if (buyResult) {
                    break;
                }
                currIdx--;
            }
            if (!buyResult) {
                currIdx = startIdx + 1;
                while (currIdx <= right) {
                    if (checkSeatInfo(readSeatInfoPlain(currIdx), departure, arrival, true)) {
                        currIdx++;
                        continue;
                    }
                    buyResult = setOccupiedInverted(route, currIdx, departure, arrival, true);
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
            Seat s = new Seat(currIdx, 0);
            return buildTicket(currTid.getAndIncrement(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);
        }
    }

    /**
     * 使用Integer[]数组存储seatIndices
     * 在买票和退票时，针对座位使用synchronized进行加锁。
     */
    class ImplFour extends ImplThree {
        private final Integer[] seatIndices;

        public ImplFour() {
            super();
            seatIndices = new Integer[ROUTE_NUM * COACH_NUM * SEAT_NUM];
            for (int i = 0; i < seatIndices.length; i++) {
                seatIndices[i] = i;
            }
        }

        @Override
        protected boolean setOccupiedInverted(int route, int seatIdx, int departure, int arrival, boolean occupied) {
            long expectedSeatInfo, newSeatInfo;
            synchronized (seatIndices[seatIdx]) {
                expectedSeatInfo = readSeatInfoOpaque(seatIdx);
                if (!checkSeatInfo(expectedSeatInfo, departure, arrival, !occupied)) {
                    return false;
                }
                newSeatInfo = expectedSeatInfo;
                for (int i = departure - 1; i <= arrival - 1; i++) {
                    if (occupied) {
                        // 置1
                        newSeatInfo = newSeatInfo | (0x1L << i);
                    } else {
                        // 置0
                        newSeatInfo = newSeatInfo & ~(0x1L << i);
                    }
                }

                if (useLong) {
                    vh.setOpaque(longArray, seatIdx, newSeatInfo);
                } else {
                    vh.setOpaque(intArray, seatIdx, (int) newSeatInfo);
                }
            }
            return true;
        }
    }
}
