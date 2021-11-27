package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 在实现七的基础上，增加了优先购买刚退票的位置的功能。
 */
public class ImplEight extends ImplSeven {
    ThreadLocal<Map<Integer, List<Integer>>> justRefundSeatContainerElemIdxes;

    public ImplEight(TicketingDS.TicketingDSParam param) {
        super(param);
        justRefundSeatContainerElemIdxes = new ThreadLocal<>();
    }

    protected int tryBuyFromRefund(int route, int departure, int arrival) {
        Map<Integer, List<Integer>> mapSeatIdxes = justRefundSeatContainerElemIdxes.get();
        List<Integer> seatIdxes = mapSeatIdxes == null ? null : mapSeatIdxes.get(route);
        int currIdx;
        int boughtSeatIdxOnRoute = -1;
        while (seatIdxes != null && seatIdxes.size() > 0) {
            currIdx = seatIdxes.remove(seatIdxes.size() - 1);
            boughtSeatIdxOnRoute = trySetOccupied(route, currIdx, departure, arrival, true);
            if (boughtSeatIdxOnRoute != -1) {
                break;
            }
        }
        return boughtSeatIdxOnRoute;
    }

    @Override
    protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }

        int boughtSeatIdxOnRoute = tryBuyFromRefund(route, departure, arrival);

        if (boughtSeatIdxOnRoute == -1) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            boughtSeatIdxOnRoute = tryBuyWithInRange(route, departure, arrival, 0, arrayLength - 1, random);
        }
        if (boughtSeatIdxOnRoute == -1) {
            // 无余票
            return null;
        }
        Seat s = new Seat(route, boughtSeatIdxOnRoute);
        return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);
    }

    @Override
    protected boolean doRefundTicket(Ticket ticket) {
        if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
            return false;
        }
        Seat seat = new Seat(ticket.route, ticket.coach, ticket.seat);
        boolean suc = freeOccupiedForSpecificSeat(ticket.route, seat.getSeatContainerElemIdx(), seat.getSeatPosInContainerElem(), ticket.departure, ticket.arrival, true);
        if (suc) {
            if (justRefundSeatContainerElemIdxes.get() == null) {
                justRefundSeatContainerElemIdxes.set(new HashMap<>());
                for (int i = 0; i < param.ROUTE_NUM; i++) {
                    justRefundSeatContainerElemIdxes.get().put(i + 1, new ArrayList<>(longArray[i].length));
                }
            }
            if (ticket.arrival - ticket.departure >= param.STATION_NUM / 2)
                justRefundSeatContainerElemIdxes.get().get(ticket.route).add(seat.seatContainerElemIdx);
        }
        return suc;
    }
}