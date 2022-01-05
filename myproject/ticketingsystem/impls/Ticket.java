package ticketingsystem.impls;

import java.util.Objects;

public class Ticket {
    public long tid;
    public String passenger;
    public int route;
    public int coach;
    public int seat;
    public int departure;
    public int arrival;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return tid == ticket.tid && route == ticket.route && coach == ticket.coach && seat == ticket.seat && departure == ticket.departure && arrival == ticket.arrival && passenger.equals(ticket.passenger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tid, passenger, route, coach, seat, departure, arrival);
    }
}
