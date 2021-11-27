package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

public class ImplThirteen extends ImplTwelve {
    public ImplThirteen(TicketingDS.TicketingDSParam param) {
        super(param);
        threadLocalViewHelper = new ThreadLocalViewHelper(param);
    }

    protected ThreadLocalViewHelper threadLocalViewHelper;

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
        Ticket ticket = super.doBuyTicket(passenger, route, departure, arrival);
        threadLocalViewHelper.updateView(getMappedThreadID(), route, departure, arrival, false);
        writeStatus(route);
        return ticket;
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