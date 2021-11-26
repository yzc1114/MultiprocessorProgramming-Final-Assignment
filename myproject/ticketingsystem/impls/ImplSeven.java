package ticketingsystem.impls;

import ticketingsystem.Ticket;
import ticketingsystem.TicketingDS;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 继承于ImplSix，它的购票策略不使用热点数据的策略，直接采取随机的方式。
 */
public class ImplSeven extends ImplSix {
    public ImplSeven(TicketingDS.TicketingDSParam param) {
        super(param);
    }

    protected int tryBuyWithInRange(int route, int departure, int arrival, int left, int right, ThreadLocalRandom random) {
        int startIdx = random.nextInt(left, right + 1);
//            int startIdx = left;

        int currIdx = startIdx;
        int boughtSeatIdxOnRoute = -1;
        while (currIdx >= left) {
            boughtSeatIdxOnRoute = trySetOccupied(route, currIdx, departure, arrival, true);
            if (boughtSeatIdxOnRoute != -1) {
                break;
            }
            currIdx--;
        }
        if (boughtSeatIdxOnRoute == -1) {
            currIdx = startIdx + 1;
            while (currIdx <= right) {
                boughtSeatIdxOnRoute = trySetOccupied(route, currIdx, departure, arrival, true);
                if (boughtSeatIdxOnRoute != -1) {
                    break;
                }
                currIdx++;
            }
        }
        return boughtSeatIdxOnRoute;
    }

    @Override
    protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int boughtSeatIdxOnRoute = tryBuyWithInRange(route, departure, arrival, 0, arrayLength - 1, random);
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
        return freeOccupiedForSpecificSeat(ticket.route, seat.getSeatContainerElemIdx(), seat.getSeatPosInContainerElem(), ticket.departure, ticket.arrival, true);
    }

}
