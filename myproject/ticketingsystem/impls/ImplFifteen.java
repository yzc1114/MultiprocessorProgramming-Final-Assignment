package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 继承MultiArray的实现，在inquiry时使用jdk17的向量化指令加速（若是intel cpu，则是intel AVX指令集加速）
 */
public class ImplFifteen extends ImplUsingMultiArray {
    public ImplFifteen(TicketingDS.TicketingDSParam param) {
        super(param);
        initMasks();
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return -1;
        }
        // 保证内存可见性
        readStatus(route);
        int mask = dep2arrOnesMasks[departure - 1][arrival - 1];
        int res = VectorizedHelper.intVectorMasked(route2intArray[route - 1], mask);
        return param.SEAT_NUM * param.COACH_NUM - res;
    }

    @Override
    protected Ticket doBuyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int boughtIdx = tryBuyWithInRange(route, departure, arrival, 0, param.SEAT_NUM * param.COACH_NUM - 1, random);
        if (boughtIdx == -1) {
            // 无余票
            return null;
        }
        Seat s = new Seat(route, boughtIdx);
        return buildTicket(getCurrThreadNextTid(), passenger, route, s.getCoach(), s.getSeat(), departure, arrival);
    }
}
