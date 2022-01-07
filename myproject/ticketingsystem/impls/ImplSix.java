package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ImplUsingOneArray
 * 数据结构：只建立一个超长数组。数组长度为 ROUTE_NUM * COACH_NUM * param.SEAT_NUM / (64 / (STATION_NUM - 1))
 * 每个元素为long类型，该数字的全部比特按照车站数量进行分组。
 * 如64位的数字，车站有10个，则可以使用低63位表示7个座位的全部车站。
 * <p>
 * 写入数据时，使用setOccupiedInverted方法，默认使用CAS方法写入数据，避免加锁。
 */
public class ImplSix extends ImplCommon {
    // 使用64位存储多个座位的信息，每个座位包含其全部车站的信息。
    protected final int seatsPerLong = 64 / (param.STATION_NUM - 1);
    protected final int partCount = param.COACH_NUM;
    protected final int seatsPerPart = param.COACH_NUM * param.SEAT_NUM / partCount;
    protected final int arrayLength = param.COACH_NUM * param.SEAT_NUM / seatsPerLong + 1;
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

    public ImplSix(TicketingDS.TicketingDSParam param) {
        super(param);
        initDataArray();
        initMasks();
    }

    protected void initMasks() {
        dep2arr2posMasks = new long[param.STATION_NUM][param.STATION_NUM][seatsPerLong];
        for (int i = 0; i < dep2arr2posMasks.length; i++) {
            dep2arr2posMasks[i] = new long[param.STATION_NUM][seatsPerLong];
            for (int j = i + 1; j < dep2arr2posMasks[i].length; j++) {
                dep2arr2posMasks[i][j] = new long[seatsPerLong];
            }
        }
        for (int i = 0; i < param.STATION_NUM; i++) {
            for (int j = i + 1; j < param.STATION_NUM; j++) {
                for (int k = 0; k < seatsPerLong; k++) {
                    if (k == 0) {
                        int l = j - i;
                        int ones = 0;
                        for (int m = 0; m < param.STATION_NUM; m++) {
                            ones |= 1 << m;
                        }

                        int onesMask = (ones >> (param.STATION_NUM - l)) << i;
                        dep2arr2posMasks[i][j][0] = onesMask;
                    } else {
                        dep2arr2posMasks[i][j][k] = dep2arr2posMasks[i][j][k - 1] << (param.STATION_NUM - 1);
                    }
                }
            }
        }
    }

    protected void initDataArray() {
        crowd = new int[param.ROUTE_NUM][partCount];
        for (int[] ints : crowd) {
            Arrays.fill(ints, seatsPerPart * param.STATION_NUM);
        }
        longArray = new long[param.ROUTE_NUM][arrayLength];
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
                    if ((seatsForRoute.length - 1) * seatsPerLong + i + 1 > param.COACH_NUM * param.SEAT_NUM) {
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

    protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival) {
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
            int startIdx;
            int rightBound = Math.min(seatsForRoute.length, right);
            if (left > rightBound) {
                System.out.println("left > rightBound");
                System.exit(-1);
            }
            if (left == rightBound) {
                startIdx = left;
            } else {
                startIdx = random.nextInt(left, rightBound);
            }
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

    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        Ticket t = doBuyTicket(passenger, route, departure, arrival);
        if (t == null) {
            readStatus(route);
            t = doBuyTicket(passenger, route, departure, arrival);
        }
        if (t != null) {
            writeStatus(route);
        }
        return t;
    }

    protected int doInquiry(int route, int departure, int arrival) {
        int res = 0;
        long[] seatsForRoute = longArray[route - 1];
        for (long l : seatsForRoute) {
            res += getCorrespondSeatCount(l, departure, arrival, false);
        }
        res -= seatsPerLong - param.COACH_NUM * param.SEAT_NUM % seatsPerLong;
        return res;
    }

    public int inquiry(int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return -1;
        }
        // 保证内存可见性
        readStatus(route);
        return doInquiry(route, departure, arrival);
    }

    protected boolean doRefundTicket(Ticket ticket) {
        if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
            return false;
        }
        Seat seat = new Seat(ticket.route, ticket.coach, ticket.seat);
        boolean suc = freeOccupiedForSpecificSeat(ticket.route, seat.getSeatContainerElemIdx(), seat.getSeatPosInContainerElem(), ticket.departure, ticket.arrival, true);
        crowd[ticket.route - 1][seat.getSeatIdxOnRoute() / (seatsPerPart)] += (ticket.arrival - ticket.departure + 1);
        return suc;
    }

    public boolean refundTicket(Ticket ticket) {
        boolean res = doRefundTicket(ticket);
        if (res) {
            writeStatus(ticket.route);
        }
        return res;
    }

    protected class Seat {
        // 表示该座位在该车次的相对位置。
        protected final int seatIdxOnRoute;
        protected final int route;
        protected int coach;
        protected int seat;
        protected int seatContainerElemIdx;
        protected int seatPosInContainerElem;

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
            this.seatIdxOnRoute = (param.SEAT_NUM * (coach - 1)) + seat - 1;
            getSeatPosInContainerElem();
            getSeatContainerElemIdx();
        }

        public int getRoute() {
            return this.route;
        }

        public int getCoach() {
            if (this.coach == 0) {
                this.coach = getSeatIdxOnRoute() / param.SEAT_NUM + 1;
            }
            return this.coach;
        }

        public int getSeat() {
            if (this.seat == 0) {
                this.seat = getSeatIdxOnRoute() - (param.SEAT_NUM * (coach - 1)) + 1;
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
}