package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.util.BitSet;

/**
 * ImplWithStationOnTop，抽象类
 * 数据结构：按照车次分别建立数据结构 Route(1,2,3,4,5):
 * 每个站点建立数据 station(1,2,3,4,5,6,7,8,9,10)：
 * 使用位向量，涵盖所有车厢的所有座位，1表示座位被所占用：i.e. [1, 1, 1, 0, 1, 0, 1]
 * 车厢概念被消除，仅保留座位概念。通过取模方式计算车厢号码。
 * 并未实现购买和退票方法，由子类实现。
 */
public abstract class ImplWithStationOnTop extends ImplCommon {
    protected final BitSet[][] data;

    public ImplWithStationOnTop(TicketingDS.TicketingDSParam param) {
        super(param);
        data = new BitSet[param.ROUTE_NUM][param.STATION_NUM];
        for (int i = 0; i < param.ROUTE_NUM; i++) {
            for (int j = 0; j < param.STATION_NUM; j++) {
                data[i][j] = new BitSet(param.SEAT_NUM * param.COACH_NUM);
            }
        }
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        // 这一步保证了内存可见性
        readStatus(route);
        if (isParamsInvalid(route, departure, arrival)) {
            return -1;
        }
        BitSet[] station2seats = data[route - 1];
        BitSet seatsMerged = (BitSet) station2seats[departure - 1].clone();
        for (int i = departure - 1; i <= arrival - 2; i++) {
            try {
                seatsMerged.or(station2seats[i]);
                if (seatsMerged.size() != station2seats[i].size()) {
                    throw new Exception();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return param.SEAT_NUM * param.COACH_NUM - seatsMerged.cardinality();
    }

    protected int doBuyTicket(BitSet[] station2seats, int departure, int arrival) {
        BitSet seatsMerged = (BitSet) station2seats[departure - 1].clone();
        for (int i = departure - 1; i <= arrival - 2; i++) {
            seatsMerged.or(station2seats[i]);
        }
        int seatIdx = seatsMerged.previousClearBit(param.SEAT_NUM * param.COACH_NUM - 1);
        if (seatIdx == -1) {
            // 无票
            return seatIdx;
        }
        for (int i = departure - 1; i <= arrival - 2; i++) {
            station2seats[i].set(seatIdx);
        }
        return seatIdx;
    }

    protected boolean doRefundTicket(BitSet[] station2seats, Ticket ticket) {
        // 检查座位所有departure到arrival的站是否全部被占用
        CoachSeatPair p = new CoachSeatPair(ticket.coach, ticket.seat);
        for (int i = ticket.departure - 1; i <= ticket.arrival - 2; i++) {
            if (!station2seats[i].get(p.seatIdx)) {
                return false;
            }
        }
        for (int i = ticket.departure - 1; i <= ticket.arrival - 2; i++) {
            station2seats[i].set(p.seatIdx, false);
        }
        return true;
    }
}