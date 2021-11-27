package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 类似于ImplUsingOneArray的存储结构，但是使用route的多个数组存储。
 */
public abstract class ImplUsingMultiArray extends ImplWithOneIntStationMasks {
    protected VarHandle vh;
    protected int[][] route2intArray;

    public ImplUsingMultiArray(TicketingDS.TicketingDSParam param) {
        super(param);
        initDataArray();
        initMasks();
    }

    protected class Seat {
        protected final int seatIdx;
        protected final int route;
        protected int coach;
        protected int seat;

        Seat(int route, int seatIdx) {
            this.route = route;
            this.seatIdx = seatIdx;
        }

        Seat(int route, int coach, int seat) {
            this.route = route;
            this.coach = coach;
            this.seat = seat;
            this.seatIdx = (param.SEAT_NUM * (coach - 1)) + getSeat() - 1;
        }

        public int getRoute() {
            return this.route;
        }

        public int getCoach() {
            if (this.coach == 0) {
                this.coach = getSeatIdx() / param.SEAT_NUM + 1;
            }
            return this.coach;
        }

        public int getSeat() {
            if (this.seat == 0) {
                this.seat = getSeatIdx() - (param.SEAT_NUM * (coach - 1)) + 1;
            }
            return this.seat;
        }

        public int getSeatIdx() {
            return this.seatIdx;
        }
    }

    protected void initDataArray() {
        route2intArray = new int[param.ROUTE_NUM][param.COACH_NUM * param.SEAT_NUM];
        vh = MethodHandles.arrayElementVarHandle(int[].class);
    }

    protected boolean setOccupiedInverted(int route, int seatIdx, int departure, int arrival, boolean occupied, boolean tryWithLoop) {
        int expectedSeatInfo, newSeatInfo;
        for (; ; ) {
            expectedSeatInfo = route2intArray[route - 1][seatIdx];
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
                newSeatInfo &= ~dep2arrOnesMasks[departure - 1][arrival - 1];
            }
            boolean fail = !vh.compareAndSet(route2intArray[route - 1], seatIdx, expectedSeatInfo, newSeatInfo);
            if (!fail) {
                return true;
            }
            if (!tryWithLoop) {
                return false;
            }
        }
    }

    protected int tryBuyWithInRange(int route, int departure, int arrival, int left, int right, ThreadLocalRandom random) {
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
        if (buyResult) {
            return currIdx;
        }
        return -1;
    }

    protected boolean checkSeatInfo(int seatInfo, int departure, int arrival, boolean occupied) {
        int mask = dep2arrOnesMasks[departure - 1][arrival - 1];
        if (occupied) {
            return mask == (seatInfo & mask);
        } else {
            return 0 == (seatInfo & mask);
        }
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return -1;
        }
        // 保证内存可见性
        readStatus(route);
        int res = 0;
        for (int i = 0; i < param.COACH_NUM * param.SEAT_NUM; i++) {
            // 保证可见性后使用plain的方式读取即可
            if (checkSeatInfo(route2intArray[route][i], departure, arrival, false)) {
                // 当前座位idx为i，如果检查从departure 到 arrival之间的位数都为false，即可证明该座位的该区间没有被占用，可以增加余票数量。
                res++;
            }
        }
        return res;
    }

    abstract protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival);

    @Override
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

    protected boolean doRefundTicket(Ticket ticket) {
        if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
            return false;
        }
        Seat s = new Seat(ticket.route, ticket.coach, ticket.seat);
        return setOccupiedInverted(ticket.route, s.getSeatIdx(), ticket.departure, ticket.arrival, false, true);
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        boolean res = doRefundTicket(ticket);
        if (res) {
            writeStatus(ticket.route);
        }
        return res;
    }
}
