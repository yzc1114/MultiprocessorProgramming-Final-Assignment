package ticketingsystem.impls;

import ticketingsystem.Ticket;
import ticketingsystem.TicketingDS;
import ticketingsystem.TicketingSystem;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ImplCommon implements TicketingSystem {

    protected TicketingDS.TicketingDSParam param;

    protected final static int S_JUST_WROTE = 0;
    protected final int[] status;
    protected final VarHandle statusVH;

    public ImplCommon(TicketingDS.TicketingDSParam param) {
        this.param = param;

        status = new int[param.ROUTE_NUM];
        for (int i = 0; i < param.ROUTE_NUM; i++) {
            status[i] = S_JUST_WROTE;
        }
        statusVH = MethodHandles.arrayElementVarHandle(int[].class);
    }

    protected void readStatus(int route) {
        statusVH.getVolatile(this.status, route - 1);
    }

    protected void writeStatus(int route) {
        statusVH.setVolatile(this.status, route - 1, S_JUST_WROTE);
    }

    // for thread mapping id
    private final AtomicInteger currMappedThreadID = new AtomicInteger(0);
    private final ThreadLocal<Integer> mappedThreadID = new ThreadLocal<>();
    private final ThreadLocal<Integer> currTid = new ThreadLocal<>();
    private final AtomicInteger nextTidRegion = new AtomicInteger(0);
    private final ThreadLocal<Integer> currTidRegion = new ThreadLocal<>();
    private static final int tidRegionSpan = 10_000_000;


    protected void initThreadLocal() {
        this.currTid.set(0);
        this.currTidRegion.set(0);
    }

    protected int getCurrThreadNextTid() {
        if (this.currTidRegion.get() == null) {
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

    protected int getMappedThreadID() {
        Integer mapped;
        if ((mapped = mappedThreadID.get()) != null) {
            return mapped;
        }
        mapped = currMappedThreadID.getAndIncrement();
        mappedThreadID.set(mapped);
        return mapped;
    }


    protected class Seat {
        protected final int seatIdx;

        protected int route;
        protected int coach;
        protected int seat;

        Seat(int seatIdx) {
            this.seatIdx = seatIdx;
        }

        Seat(int route, int coach, int seat) {
            this.route = route;
            this.coach = coach;
            this.seat = seat;
            this.seatIdx = (param.COACH_NUM * param.SEAT_NUM * (route - 1)) + (param.SEAT_NUM * (coach - 1)) + getSeat() - 1;
        }

        public int getRoute() {
            if (this.route == 0) {
                this.route = (getSeatIdx() / (param.COACH_NUM * param.SEAT_NUM)) + 1;
            }
            return this.route;
        }

        public int getCoach() {
            if (this.coach == 0) {
                this.coach = (getSeatIdx() - (param.COACH_NUM * param.SEAT_NUM * (getRoute() - 1))) / param.SEAT_NUM + 1;
            }
            return this.coach;
        }

        public int getSeat() {
            if (this.seat == 0) {
                this.seat = (getSeatIdx() - (param.COACH_NUM * param.SEAT_NUM * (route - 1)) - (param.SEAT_NUM * (coach - 1))) + 1;
            }
            return this.seat;
        }

        public int getSeatIdx() {
            return this.seatIdx;
        }
    }

    protected class CoachSeatPair {
        protected int coach;
        protected int seat;
        protected int seatIdx;

        public CoachSeatPair(int seatIdx) {
            this.coach = seatIdx / param.SEAT_NUM + 1;
            this.seat = seatIdx - (coach - 1) * param.SEAT_NUM + 1;
            this.seatIdx = seatIdx;
        }

        public CoachSeatPair(int coach, int seat) {
            this.coach = coach;
            this.seat = seat;
            this.seatIdx = (coach - 1) * param.SEAT_NUM + seat - 1;
        }
    }

    protected boolean isParamsInvalid(int routeNum, int departure, int arrival) {
        return !checkRoute(routeNum) || !checkDepartureArrival(departure, arrival);
    }

    protected boolean checkRoute(int routeNum) {
        return routeNum >= 1 && routeNum <= param.ROUTE_NUM;
    }

    protected boolean checkDepartureArrival(int departure, int arrival) {
        return departure >= 1
                && departure <= param.STATION_NUM
                && arrival >= 1
                && arrival <= param.STATION_NUM
                && arrival > departure;
    }

    protected boolean checkCoachSeatNum(int coachNum, int seatNum) {
        return coachNum >= 1 && coachNum <= param.COACH_NUM
                && seatNum >= 1 && seatNum <= param.SEAT_NUM;
    }

    protected static Ticket buildTicket(long tid,
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
}
