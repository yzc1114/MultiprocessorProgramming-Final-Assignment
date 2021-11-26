package ticketingsystem.impls;

import ticketingsystem.Ticket;
import ticketingsystem.TicketingDS;

import java.util.BitSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ImplTwo数据存储结构与ImplOne一致，继承了ImplWithStationOnTop。
 * 购买和退票使用route级别的ReentrantLock锁，将车次级别的购票和退票行为进行可线性化。
 * 查询速度较快，写入速度较慢
 */
public class ImplTwo extends ImplWithStationOnTop {
    private final ReentrantLock[] locks;

    public ImplTwo(TicketingDS.TicketingDSParam param) {
        super(param);
        locks = new ReentrantLock[param.ROUTE_NUM];
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