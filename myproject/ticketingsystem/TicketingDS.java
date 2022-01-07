package ticketingsystem;

import ticketingsystem.impls.ImplCommon;
import ticketingsystem.impls.ImplFifteen;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;

public class TicketingDS implements TicketingSystem {

    public TicketingDSParam param;

    private ThreadLocal<HashSet<Long>> soldTickIds;
    private ImplCommon actualImpl;

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        if (routenum <= 0 || coachnum <= 0 || seatnum <= 0 || stationnum <= 0 || threadnum <= 0) {
            System.out.println("TicketingDS init params are invalid, plz check your input");
            assert false;
        }
        param = new TicketingDSParam(routenum, coachnum, seatnum, stationnum, threadnum);
        // TODO 这里指定默认实现方式。
        setDefaultImpl();
    }

    public static void main(String[] args) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    }

    private void setDefaultImpl() {
        try {
            switchImplType(ImplType.Fifteen);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException | IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void switchImplType(ImplType type) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, IOException {
        if (this.actualImpl instanceof Closeable) {
            ((Closeable) this.actualImpl).close();
        }
        this.actualImpl = type.implClass.getConstructor(TicketingDSParam.class).newInstance(param);
        this.soldTickIds = new ThreadLocal<>();
    }

    public Class<? extends ImplCommon> getImplClass() {
        return actualImpl.getClass();
    }

    private void initThreadLocal() {
        this.soldTickIds.set(new HashSet<>());
    }

    public Ticket transform(ticketingsystem.impls.Ticket t) {
        Ticket n = new Ticket();
        n.route = t.route;
        n.departure = t.departure;
        n.arrival = t.arrival;
        n.coach = t.coach;
        n.seat = t.seat;
        n.passenger = t.passenger;
        n.tid = t.tid;
        return n;
    }

    public ticketingsystem.impls.Ticket transform(Ticket t) {
        ticketingsystem.impls.Ticket n = new ticketingsystem.impls.Ticket();
        n.route = t.route;
        n.departure = t.departure;
        n.arrival = t.arrival;
        n.coach = t.coach;
        n.seat = t.seat;
        n.passenger = t.passenger;
        n.tid = t.tid;
        return n;
    }

    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        ticketingsystem.impls.Ticket boughtTicket = actualImpl.buyTicket(passenger, route, departure, arrival);
        if (boughtTicket != null) {
            if (this.soldTickIds.get() == null) {
                initThreadLocal();
            }
            soldTickIds.get().add(boughtTicket.tid);
            return transform(boughtTicket);
        }
        return null;
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        return actualImpl.inquiry(route, departure, arrival);
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        if (soldTickIds.get() == null) {
            initThreadLocal();
        }
        if (!soldTickIds.get().remove(ticket.tid)) {
            // 无效票
            return false;
        }
        return actualImpl.refundTicket(transform(ticket));
    }

    public enum ImplType {
        //        One(ImplOne.class),
//        Two(ImplTwo.class),
//        Three(ImplThree.class),
//        Four(ImplFour.class),
//        Five(ImplFive.class),
//        Six(ImplSix.class),
//        Seven(ImplSeven.class),
//        Eight(ImplEight.class),
//        Nine(ImplNine.class),
//        Ten(ImplTen.class),
//        Eleven(ImplEleven.class),
//        Twelve(ImplTwelve.class),
//        Thirteen(ImplThirteen.class),
//        Fourteen(ImplFourteen.class),
        Fifteen(ImplFifteen.class);
        //        Sixteen(ImplSixteen.class);
        //        Seventeen(ImplSevenTeen.class);
        private final Class<? extends ImplCommon> implClass;

        ImplType(Class<? extends ImplCommon> implClass) {
            this.implClass = implClass;
        }
    }

    public static class TicketingDSParam {
        public final int ROUTE_NUM;
        public final int COACH_NUM;
        public final int SEAT_NUM;
        public final int STATION_NUM;
        public final int THREAD_NUM;

        public TicketingDSParam(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
            ROUTE_NUM = routenum;
            COACH_NUM = coachnum;
            SEAT_NUM = seatnum;
            STATION_NUM = stationnum;
            THREAD_NUM = threadnum;
        }
    }

}
