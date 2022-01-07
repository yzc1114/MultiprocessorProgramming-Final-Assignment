package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

public class ImplFourteen extends ImplEleven {
    protected ThreadLocalViewHelper threadLocalViewHelper;

    public ImplFourteen(TicketingDS.TicketingDSParam param) {
        super(param);
        this.threadLocalViewHelper = new ThreadLocalViewHelper(param);
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        if (isParamsInvalid(route, departure, arrival)) {
            return -1;
        }
        // 保证内存可见性
        readStatus(route);
        return threadLocalViewHelper.inquiryHelper(route, departure, arrival);
    }

    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        readStatus(route);
        Ticket t = doBuyTicket(passenger, route, departure, arrival);
        threadLocalViewHelper.updateView(getMappedThreadID(), route, departure, arrival, false);
        writeStatus(route);
        return t;
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        boolean res = super.refundTicket(ticket);
        if (res) {
            threadLocalViewHelper.updateView(getMappedThreadID(), ticket.route, ticket.departure, ticket.arrival, true);
            writeStatus(ticket.route);
        }
        return res;
    }
}