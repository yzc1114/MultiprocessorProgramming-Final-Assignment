package ticketingsystem.impls;

import ticketingsystem.Ticket;
import ticketingsystem.TicketingDS;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 考虑了数据局部性，基于ImplFour的实现。
 */
public class ImplEleven extends ImplFour {

    protected int partsCount;

    protected int partLength;

    public ImplEleven(TicketingDS.TicketingDSParam param) {
        super(param);
        partsCount = param.COACH_NUM;
        partLength = param.COACH_NUM * param.SEAT_NUM / partsCount;
    }

    @Override
    protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }

        int boughtIdx = tryBuyFromRefund(route, departure, arrival);
        if (boughtIdx == -1) {
            int currPart = getMappedThreadID() % partsCount;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            readStatus(route);
            for (int i = 0; i < partsCount; i++) {
                int left = (route - 1) * param.COACH_NUM * param.SEAT_NUM + currPart * partLength;
                // int right = route * COACH_NUM * SEAT_NUM - 1;
                int right = Math.min(route * param.COACH_NUM * param.SEAT_NUM - 1, (route - 1) * param.COACH_NUM * param.SEAT_NUM + (currPart + 1) * partLength);
                boughtIdx = tryBuyWithInRange(route, departure, arrival, left, right, random);
                if (boughtIdx != -1) {
                    break;
                }
                currPart = (currPart + 1) % partsCount;
            }
        }

        if (boughtIdx == -1) {
            return null;
        }
        Seat s = new Seat(boughtIdx);
        return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);

    }
}