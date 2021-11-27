package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.util.BitSet;

/**
 * ImplOne数据存储结构，继承ImplWithStationOnTop
 * 购买和退票使用route级别的synchronized关键字，将车次级别的购票和退票行为进行可线性化。
 */
public class ImplOne extends ImplWithStationOnTop {
    public ImplOne(TicketingDS.TicketingDSParam param) {
        super(param);
    }

    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }
        BitSet[] station2seats = data[route - 1];
        int seatIdx;
        synchronized (station2seats) {
//        synchronized (this) {
            seatIdx = doBuyTicket(station2seats, departure, arrival);
            writeStatus(route);
        }
        if (seatIdx == -1) {
            return null;
        }
        CoachSeatPair p = new CoachSeatPair(seatIdx);
        return buildTicket(getCurrThreadNextTid(), passenger, route, p.coach, p.seat, departure, arrival);
    }

    public boolean refundTicket(Ticket ticket) {
        if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
            return false;
        }
        BitSet[] station2seats = data[ticket.route - 1];
        synchronized (station2seats) {
//        synchronized (this) {
            boolean res = doRefundTicket(station2seats, ticket);
            writeStatus(ticket.route);
            return res;
        }
    }
}
