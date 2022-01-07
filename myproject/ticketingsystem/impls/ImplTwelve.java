package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 考虑了数据局部性，基于ImplEight的实现。
 */
public class ImplTwelve extends ImplEight {
    protected int partsCount;
    protected int partLength;

    public ImplTwelve(TicketingDS.TicketingDSParam param) {
        super(param);
        partsCount = param.COACH_NUM;
        partLength = arrayLength / param.COACH_NUM;
    }

    protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }

        int boughtSeatIdxOnRoute = tryBuyFromRefund(route, departure, arrival);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (boughtSeatIdxOnRoute == -1) {
            int currPart = getMappedThreadID() % partsCount;
            long[] seatsForRoute = longArray[route - 1];
            for (int i = 0; i < partsCount; i++) {
                int left = currPart * partLength;
                // int right = route * COACH_NUM * SEAT_NUM - 1;
                int right = Math.min(seatsForRoute.length - 1, (currPart + 1) * partLength);
                boughtSeatIdxOnRoute = tryBuyWithInRange(route, departure, arrival, left, right, random);
                if (boughtSeatIdxOnRoute != -1) {
                    break;
                }
                currPart = (currPart + 1) % partsCount;
            }

        }
        if (boughtSeatIdxOnRoute == -1) {
            // 无余票
            return null;
        }
        Seat s = new Seat(route, boughtSeatIdxOnRoute);
        return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);

    }
}