package ticketingsystem.impls;

import ticketingsystem.Ticket;
import ticketingsystem.TicketingDS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 在方案三的基础上修改购票策略。
 * 将本线程刚刚退票的那些座位记录下来，认为这些座位空闲下来的概率更高，在接下来购票时首先检查这些座位情况。
 */
public class ImplFour extends ImplUsingOneArray {
    ThreadLocal<Map<Integer, List<Integer>>> justRefundSeatIdxes;

    public ImplFour(TicketingDS.TicketingDSParam param) {
        super(param);
        justRefundSeatIdxes = new ThreadLocal<>();
    }

    protected int tryBuyFromRefund(int route, int departure, int arrival) {
        Map<Integer, List<Integer>> mapSeatIdxes = justRefundSeatIdxes.get();
        List<Integer> seatIdxes = mapSeatIdxes == null ? null : mapSeatIdxes.get(route);
        int currIdx = -1;
        boolean buyResult = false;
        while (seatIdxes != null && seatIdxes.size() > 0) {
            currIdx = seatIdxes.remove(seatIdxes.size() - 1);
            buyResult = setOccupiedInverted(route, currIdx, departure, arrival, true, true);
            if (buyResult) {
                break;
            }
        }
        if (buyResult) {
            return currIdx;
        }
        return -1;
    }

    @Override
    protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }
        readStatus(route);
        int boughtIdx = tryBuyFromRefund(route, departure, arrival);

        if (boughtIdx == -1) {
            int left = (route - 1) * param.COACH_NUM * param.SEAT_NUM;
            int right = route * param.COACH_NUM * param.SEAT_NUM - 1;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            boughtIdx = tryBuyWithInRange(route, departure, arrival, left, right, random);
        }
        if (boughtIdx == -1) {
            // 无余票
            return null;
        }
        Seat s = new Seat(boughtIdx);
        return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);
    }

    protected boolean doRefundTicket(Ticket ticket) {
        if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
            return false;
        }
        Seat s = new Seat(ticket.route, ticket.coach, ticket.seat);
        boolean suc = setOccupiedInverted(ticket.route, s.getSeatIdx(), ticket.departure, ticket.arrival, false, true);
        if (suc) {
            if (justRefundSeatIdxes.get() == null) {
                justRefundSeatIdxes.set(new HashMap<>());
                for (int i = 0; i < param.ROUTE_NUM; i++) {
                    justRefundSeatIdxes.get().put(i + 1, new ArrayList<>(param.COACH_NUM * param.SEAT_NUM));
                }
            }
            if (ticket.arrival - ticket.departure >= 5)
                justRefundSeatIdxes.get().get(s.route).add(s.seatIdx);
        }
        return suc;
    }
}