package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 继承于ImplSix，它使用了view的方式，优化inquiry速度
 */
public class ImplNine extends ImplSix {

    protected ViewHelper viewHelper;
    protected AtomicInteger currMappedThreadID = new AtomicInteger(0);
    protected ThreadLocal<Integer> mappedThreadID = new ThreadLocal<>();

    public ImplNine(TicketingDS.TicketingDSParam param) {
        super(param);
        this.viewHelper = new ViewHelper(param);
    }

    protected int getMappedThreadID() {
        Integer mapped;
        if ((mapped = mappedThreadID.get()) != null) {
            return mapped;
        }
        mapped = currMappedThreadID.getAndIncrement();
        mappedThreadID.set(mapped);
        return mapped;
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return -1;
        }
        return viewHelper.readEmptyView(route, departure, arrival);
    }

    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return null;
        }
        viewHelper.setView(route, departure, arrival, true);
        return buildTicket(getCurrThreadNextTid(), passenger, route, 0, 0, departure, arrival);
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
            return false;
        }
        return viewHelper.setView(ticket.route, ticket.departure, ticket.arrival, false);
    }

}