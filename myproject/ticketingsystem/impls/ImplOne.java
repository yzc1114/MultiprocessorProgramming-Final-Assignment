package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

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
        int seatIdx;
        synchronized (data[route - 1]) {
            seatIdx = doBuyTicket(data[route - 1], departure, arrival);
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
        synchronized (data[ticket.route - 1]) {
            boolean res = doRefundTicket(data[ticket.route - 1], ticket);
            writeStatus(ticket.route);
            return res;
        }
    }
}
