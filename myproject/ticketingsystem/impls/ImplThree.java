package ticketingsystem.impls;

import ticketingsystem.Ticket;
import ticketingsystem.TicketingDS;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 继承ImplUsingOneArray，只更改buyTicket逻辑
 * 用随机的方式找可以买票的座位。
 */
public class ImplThree extends ImplUsingOneArray {
    public ImplThree(TicketingDS.TicketingDSParam param) {
        super(param);
    }

    @Override
    protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }
        int left = (route - 1) * param.COACH_NUM * param.SEAT_NUM;
        int right = route * param.COACH_NUM * param.SEAT_NUM - 1;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int boughtIdx = tryBuyWithInRange(route, departure, arrival, left, right, random);
        if (boughtIdx == -1) {
            // 无余票
            return null;
        }
        Seat s = new Seat(boughtIdx);
        return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);
    }
}