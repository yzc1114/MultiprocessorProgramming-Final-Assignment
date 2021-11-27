package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

/**
 * 使用向量指令加速查询的ImplSeven
 */
public class ImplSixteen extends ImplTwelve {
    public ImplSixteen(TicketingDS.TicketingDSParam param) {
        super(param);
    }

    public int doInquiry(int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return -1;
        }
        // 保证内存可见性
        readStatus(route);
        long[] masks = dep2arr2posMasks[departure - 1][arrival - 1];
        int res = VectorizedHelper.longMultiVectorsMasked(longArray[route - 1], masks);
        return param.COACH_NUM * param.SEAT_NUM - res;
    }


}
