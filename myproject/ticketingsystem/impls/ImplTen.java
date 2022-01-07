package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用后台线程，维护当前从departure到arrival的较精确空闲座位。
 */
public class ImplTen extends ImplUsingOneArray implements Closeable {
    protected AtomicBoolean shutdownThreadPool = new AtomicBoolean(false);
    // route -> departure -> arrival -> ConcurrentHashMap
    protected Map<Integer, Boolean>[][][] availableSeats = new Map[param.ROUTE_NUM][param.STATION_NUM][param.STATION_NUM];
    protected BlockingQueue<Integer>[] rePuts = new BlockingQueue[param.THREAD_NUM];
    protected AtomicInteger currMappedThreadID = new AtomicInteger(0);
    protected ThreadLocal<Integer> mappedThreadID = new ThreadLocal<>();

    public ImplTen(TicketingDS.TicketingDSParam param) {
        super(param);
        initAvailableSeats();
        initThreadPool();
    }

    @Override
    public void close() throws IOException {
        shutdownThreadPool.set(true);
    }

    protected void initAvailableSeats() {
        for (int i = 0; i < availableSeats.length; i++) {
            availableSeats[i] = new Map[param.STATION_NUM][param.STATION_NUM];
            for (int j = 0; j < param.STATION_NUM - 1; j++) {
                availableSeats[i][j] = new Map[param.STATION_NUM];
                for (int k = j + 1; k < param.STATION_NUM; k++) {
                    availableSeats[i][j][k] = new ConcurrentHashMap<>();
                }
            }
        }
        for (int seatIdx = 0; seatIdx < intArray.length; seatIdx++) {
            Seat s = new Seat(seatIdx);

            for (int dep = 0; dep < param.STATION_NUM - 1; dep++) {
                for (int arr = dep + 1; arr < param.STATION_NUM; arr++) {
                    availableSeats[s.getRoute() - 1][dep][arr].put(s.getSeatIdx(), true);
                }
            }
        }
    }

    protected DepArr[] extractEmptyParts(int seatInfo) {
        int startEmpty = -1;
        DepArr[] emptyParts = new DepArr[param.STATION_NUM / 2];
        int emptyPartsIdx = 0;
        for (int i = 0; i < param.STATION_NUM - 1; i++) {
            int o = (seatInfo & (1 << i));
            if (o == 0) {
                if (startEmpty == -1) {
                    startEmpty = i;
                }
            } else {
                if (startEmpty != -1) {
                    emptyParts[emptyPartsIdx++] = new DepArr(startEmpty, i);
                    startEmpty = -1;
                }
            }
        }
        if (startEmpty != -1) {
            emptyParts[emptyPartsIdx] = new DepArr(startEmpty, param.STATION_NUM);
        }
        return emptyParts;
    }

    protected void rePutAvailableSeat(int seatIdx) {
        Seat s = new Seat(seatIdx);
        int seatInfo = (Integer) vh.getOpaque(intArray, seatIdx);
        DepArr[] emptyParts = extractEmptyParts(seatInfo);
        for (int dep = 0; dep < param.STATION_NUM - 1; dep++) {
            nextArr:
            for (int arr = dep + 1; arr < param.STATION_NUM; arr++) {
                for (DepArr emptyPart : emptyParts) {
                    if (emptyPart == null) {
                        break;
                    }
                    if (emptyPart.hold(dep, arr)) {
                        availableSeats[s.getRoute() - 1][dep][arr].put(seatIdx, true);
                        continue nextArr;
                    }
                }
                // cannot hold
                availableSeats[s.getRoute() - 1][dep][arr].remove(seatIdx);
            }
        }
    }

    protected void initThreadPool() {
        for (int i = 0; i < rePuts.length; i++) {
            rePuts[i] = new LinkedBlockingQueue<>();
        }
        for (int i = 0; i < param.THREAD_NUM; i++) {
            int ic = i;
            new Thread(() -> {
                while (!shutdownThreadPool.get()) {
                    try {
                        Integer seatIdx = rePuts[ic].poll(10, TimeUnit.MILLISECONDS);
                        if (seatIdx == null) {
                            continue;
                        }
                        rePutAvailableSeat(seatIdx);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();
        }
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

    protected void submitToThreadPool(int seatIdx) {
        int mapped = getMappedThreadID();
        rePuts[mapped].offer(seatIdx);
    }

    @Override
    protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival) {
        return null;
    }

    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }

        // first try to use dep arr available seats
        boolean buyResult = false;
        int boughtIdx = -1;
        for (int seatIdx : availableSeats[route - 1][departure - 1][arrival - 1].keySet()) {
            buyResult = setOccupiedInverted(route, seatIdx, departure, arrival, true, true);
            if (buyResult) {
                boughtIdx = seatIdx;
                break;
            }
        }

        if (!buyResult) {
            int left = (route - 1) * param.COACH_NUM * param.SEAT_NUM;
            int right = route * param.COACH_NUM * param.SEAT_NUM - 1;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            boughtIdx = tryBuyWithInRange(route, departure, arrival, left, right, random);
        }
        if (boughtIdx == -1) {
            // 无余票
            return null;
        }
        submitToThreadPool(boughtIdx);
        Seat s = new Seat(boughtIdx);
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
            submitToThreadPool(s.seatIdx);
        }
        return suc;
    }

    protected class DepArr {
        int dep;
        int arr;

        public DepArr(int dep, int arr) {
            this.dep = dep;
            this.arr = arr;
        }

        public boolean hold(int otherDep, int otherArr) {
            return dep <= otherDep && arr >= otherArr;
        }
    }
}