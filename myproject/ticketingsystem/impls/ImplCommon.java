package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ImplCommon {

    protected TicketingDS.TicketingDSParam param;

    protected final VarHandle statusVH;
    protected boolean[][] route2statusForEachThread;

    protected void initStatus() {
        route2statusForEachThread = new boolean[param.ROUTE_NUM][param.THREAD_NUM];
        for (int i = 0; i < route2statusForEachThread.length; i++) {
            route2statusForEachThread[i] = new boolean[param.THREAD_NUM];
        }
    }

    protected void readStatus(int route) {
        for (int i = 0; i < param.THREAD_NUM; i++) {
            statusVH.getAcquire(this.route2statusForEachThread[route - 1], i);
        }
    }

    protected void writeStatus(int route) {
        statusVH.setRelease(this.route2statusForEachThread[route - 1], getMappedThreadID(), false);
    }

    public ImplCommon(TicketingDS.TicketingDSParam param) {
        this.param = param;

        statusVH = MethodHandles.arrayElementVarHandle(boolean[].class);
        initStatus();
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

    protected class CoachSeatPair {
        public int coach;
        public int seat;
        public int seatIdx;

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

    abstract public Ticket buyTicket(String passenger, int route, int departure, int arrival);

    abstract public int inquiry(int route, int departure, int arrival);

    abstract public boolean refundTicket(Ticket ticket);
}
