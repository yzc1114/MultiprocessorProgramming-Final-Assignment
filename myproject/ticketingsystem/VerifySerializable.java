package ticketingsystem;

import java.io.*;
import java.util.*;

public class VerifySerializable {
    final static int routenum = 3;
    final static int coachnum = 3;
    final static int seatnum = 3;
    final static int stationnum = 3;
    static int threadNum;
    static List<String> methodList = new ArrayList<String>();
    /**********Manually Modified ***********/
    static boolean isPosttime = true;
    static boolean detail = false;
    static int debugMode = 1;

    static ArrayList<HistoryLine> history = new ArrayList<HistoryLine>();
    static TicketingDS object;
    private static Map<TicketBoughtRecord, List<ticketingsystem.impls.Ticket>> boughtTickets;

    private static boolean parseline(ArrayList<HistoryLine> historyList, String line) {
        Scanner linescanner = new Scanner(line);
        if (line.equals("")) {
            linescanner.close();
            return true;
        }
        HistoryLine tl = new HistoryLine();
        tl.pretime = linescanner.nextLong();
        tl.posttime = linescanner.nextLong();
        tl.threadid = linescanner.nextInt();
        tl.operationName = linescanner.next();
        tl.tid = linescanner.nextLong();
        tl.passenger = linescanner.next();
        tl.route = linescanner.nextInt();
        tl.coach = linescanner.nextInt();
        tl.departure = linescanner.nextInt();
        tl.arrival = linescanner.nextInt();
        tl.seat = linescanner.nextInt();
        tl.res = linescanner.next();
        if (!tl.operationName.equals("inquiry")) {
            // 筛掉inquiry历史
            historyList.add(tl);
        }
        linescanner.close();
        return true;
    }

    private static void initialization() {
        object = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadNum);
        methodList.add("refundTicket");
        methodList.add("buyTicket");
        methodList.add("inquiry");
    }

    private static boolean execute(String methodName, HistoryLine line, int line_num) {
        Ticket ticket = new Ticket();
        boolean flag = false;
        ticket.tid = line.tid;
        ticket.passenger = line.passenger;
        ticket.route = line.route;
        ticket.coach = line.coach;
        ticket.departure = line.departure;
        ticket.arrival = line.arrival;
        ticket.seat = line.seat;
        if (methodName.equals("buyTicket")) {
            if (line.res.equals("false")) {
                int num = object.inquiry(ticket.route, ticket.departure, ticket.arrival);
                if (num == 0)
                    return true;
                else {
                    System.out.println("Error: TicketSoldOut" + " " + line.pretime + " " + line.posttime + " " + line.threadid + " " + line.route + " " + line.departure + " " + line.arrival);
                    System.out.println("RemainTicket" + " " + num + " " + line.route + " " + line.departure + " " + line.arrival);
                    return false;
                }
            }
            Ticket ticket1 = new Ticket();
            ticket1 = object.buyTicket(ticket.passenger, ticket.route, ticket.departure, ticket.arrival);
            if (ticket1 != null && line.res.equals("true") &&
                    ticket.passenger == ticket1.passenger && ticket.route == ticket1.route &&
                    ticket.coach == ticket1.coach && ticket.departure == ticket1.departure &&
                    ticket.arrival == ticket1.arrival && ticket.seat == ticket1.seat) {
                return true;
            } else {
                System.out.println("Error: Ticket is bought" + " " + line.pretime + " " + line.posttime + " " + line.threadid + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
                return false;
            }
        } else if (methodName.equals("refundTicket")) {
            flag = object.refundTicket(ticket);
            if ((flag && line.res.equals("true")) || (!flag && line.res.equals("false")))
                return true;
            else {
                System.out.println("Error: Ticket is refunded" + " " + line.pretime + " " + line.posttime + " " + line.threadid + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
                return false;
            }
        } else if (methodName.equals("inquiry")) {
            int num = object.inquiry(line.route, line.departure, line.arrival);
            if (num == line.seat)
                return true;
            else {
                System.out.println("Error: RemainTicket" + " " + line.pretime + " " + line.posttime + " " + line.threadid + " " + line.route + " " + line.departure + " " + line.arrival + " " + line.seat);
                System.out.println("Real RemainTicket is" + " " + line.seat + " " + ", Expect RemainTicket is" + " " + num + ", " + line.route + " " + line.departure + " " + line.arrival);
                return false;
            }
        }
        System.out.println("No match method name");
        return false;
    }

    /***********************VeriLin*************** */

    private static void writeHistoryToFile(ArrayList<HistoryLine> historyList, String filename) {
        try {
            System.setOut(new PrintStream(new FileOutputStream(filename)));
            writeHistory(historyList);
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        } catch (FileNotFoundException e) {
            System.out.println(e);
        }
    }

    private static void writeHistory(ArrayList<HistoryLine> historyList) {
        for (int i = 0; i < historyList.size(); i++) {
            writeline(historyList, i);
        }
    }

    private static void writeline(ArrayList<HistoryLine> historyList, int line) {
        HistoryLine tl = historyList.get(line);
        System.out.println(tl.pretime + " " + tl.posttime + " " + tl.threadid + " " + tl.operationName + " " + tl.tid + " " + tl.passenger + " " + tl.route + " " + tl.coach + " " + tl.departure + " " + tl.arrival + " " + tl.seat);
    }

    private static boolean readHistory(ArrayList<HistoryLine> historyList, String filename) {
        try {
            Scanner scanner = new Scanner(new File(filename));
            int i = 0;
            while (scanner.hasNextLine()) {
                if (!parseline(historyList, scanner.nextLine())) {
                    scanner.close();
                    System.out.println("Error in parsing line " + i);
                    return false;
                }
                i++;
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return true;
    }

    private static boolean checkline(ArrayList<HistoryLine> historyList, int index) {
        HistoryLine line = historyList.get(index);


        for (int i = 0; i < methodList.size(); i++) {
            if (line.operationName.equals(methodList.get(i))) {
                boolean flag = execute(methodList.get(i), line, index);
//		System.out.println("Line " + index + " executing " + methodList.get(i) + " res: " + flag + " tid = " + line.tid);
                return flag;
            }
        }
        return false;

    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 3) {
            System.out.println("The parameter list of VeriLin is threadNum, historyFile, failedTrace.");
            return;
        }
        threadNum = Integer.parseInt(args[0]);
        String fileName = args[1];
        isPosttime = true;
        String ft = args[2];
        readHistory(history, fileName);
        hl_Comparator_2 com2 = new hl_Comparator_2();
        history.sort(com2);

        writeHistoryToFile(history, ft);

        doSerializableTest();
    }

    private static void doSerializableTest() {
        boughtTickets = new HashMap<>();
        List<HistoryLine> currHistorySeq = new ArrayList<>();
        boolean res = dfs(currHistorySeq, new HashSet<>());
        System.out.println("doSerializableTest res = " + res);
    }

    private static boolean dfs(List<HistoryLine> currHistorySeq, Set<Integer> visited) {
        if (visited.size() == history.size()) {
            return true;
        }
        for (int i = 0; i < history.size(); i++) {
            if (visited.contains(i)) {
                continue;
            }
            HistoryLine leftHistoryLine = history.get(i);
            if (!checkValidHistorySequence(currHistorySeq, leftHistoryLine)) {
                continue;
            }
            currHistorySeq.add(leftHistoryLine);
            visited.add(i);
            if (executeHistorySeq(currHistorySeq)) {
                if (dfs(currHistorySeq, visited)) {
                    return true;
                }
            }
            visited.remove(i);
            currHistorySeq.remove(currHistorySeq.size() - 1);
        }
        return false;
    }

    private static boolean executeHistorySeq(List<HistoryLine> lines) {
        boughtTickets.clear();
        SerialTicketingSystem o = new TicketingDSSerialCancelableImplOne(new TicketingDS.TicketingDSParam(routenum, coachnum, seatnum, stationnum, threadNum));
        for (HistoryLine line : lines) {
            if (!executeOneHistory(o, line)) {
                return false;
            }
        }
        return true;
    }

    private static boolean executeOneHistory(SerialTicketingSystem o, HistoryLine line) {
        ticketingsystem.impls.Ticket ticket = new ticketingsystem.impls.Ticket();
        ticket.tid = line.tid;
        ticket.passenger = line.passenger;
        ticket.route = line.route;
        ticket.coach = line.coach;
        ticket.departure = line.departure;
        ticket.arrival = line.arrival;
        ticket.seat = line.seat;

        TicketBoughtRecord r = new TicketBoughtRecord();
        r.passenger = ticket.passenger;
        r.departure = ticket.departure;
        r.arrival = ticket.arrival;
        r.route = ticket.route;

        if (line.operationName.equals("buyTicket")) {
            ticketingsystem.impls.Ticket res = o.buyTicket(ticket.passenger, ticket.route, ticket.departure, ticket.arrival);
            boolean ok = res != null && line.res.equals("true") &&
                    ticket.passenger.equals(res.passenger) && ticket.route == res.route &&
                    ticket.departure == res.departure &&
                    ticket.arrival == res.arrival;
            if (ok) {
                if (boughtTickets.containsKey(r)) {
                    boughtTickets.get(r).add(res);
                } else {
                    List<ticketingsystem.impls.Ticket> list = new ArrayList<>();
                    list.add(res);
                    boughtTickets.put(r, list);
                }
            }
            return ok;
        } else {
            // 只有买票和退票
            List<ticketingsystem.impls.Ticket> list = boughtTickets.get(r);
            if (list == null || list.size() == 0) {
                // System.out.println("refund non exists bought ticket");
                return false;
            }
            ticketingsystem.impls.Ticket t = list.remove(list.size() - 1);
            if (t == null) {
                // System.out.println("refund non exists bought ticket");
                return false;
            }
            boolean flag = o.refundTicket(t);
            return (flag && line.res.equals("true")) || (!flag && line.res.equals("false"));
        }
    }

    // 将一个新的line插入到dfs的序列末尾后，能否存在满足history中存在可线性化可能的序列
    private static boolean checkValidHistorySequence(List<HistoryLine> lines, HistoryLine newLine) {
        // 从后向前找。找到一个history line 的post time早于newLine的pre time时，截止，返回true。
        // 如果遍历到头，也返回true。
        // 如果向前找时，找到一个history line 的 pre time 早于 newLine的post time，则认为是不可线性化的序列，返回false。
        for (int i = lines.size() - 1; i >= 0; i--) {
            HistoryLine curr = lines.get(i);
            if (curr.posttime < newLine.pretime) {
                return true;
            }
            if (newLine.posttime < curr.pretime) {
                return false;
            }
            // 这时就是说，curr与newLine存在overlap，再向前遍历。
        }
        return true;
    }

    public interface SerialTicketingSystem {
        ticketingsystem.impls.Ticket buyTicket(String passenger, int route, int departure, int arrival);

        int inquiry(int route, int departure, int arrival);

        boolean refundTicket(ticketingsystem.impls.Ticket ticket);
    }

    public static class hl_Comparator_1 implements Comparator<HistoryLine> {
        @Override
        public int compare(HistoryLine hl1, HistoryLine hl2) {
            if (hl1.pretime - hl2.pretime > 0)
                return 1;
            else if (hl1.pretime - hl2.pretime == 0)
                return 0;
            else
                return -1;
        }
    }

    public static class hl_Comparator_2 implements Comparator<HistoryLine> {
        @Override
        public int compare(HistoryLine hl1, HistoryLine hl2) {
            if (hl1.posttime - hl2.posttime > 0)
                return 1;
            else if (hl1.posttime - hl2.posttime == 0)
                return 0;
            else
                return -1;
        }
    }

    public static class HistoryLine {
        long pretime;
        long posttime;
        int threadid;
        String operationName;
        long tid;
        String passenger;
        int route;
        int coach;
        int seat;
        int departure;
        int arrival;
        String res;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HistoryLine that = (HistoryLine) o;
            return pretime == that.pretime && posttime == that.posttime && threadid == that.threadid && tid == that.tid && route == that.route && coach == that.coach && seat == that.seat && departure == that.departure && arrival == that.arrival && operationName.equals(that.operationName) && passenger.equals(that.passenger) && res.equals(that.res);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pretime, posttime, threadid, operationName, tid, passenger, route, coach, seat, departure, arrival, res);
        }
    }

    private static class TicketBoughtRecord {
        public String passenger;
        public int route;
        public int departure;
        public int arrival;

        public TicketBoughtRecord() {
        }

        public TicketBoughtRecord(String passenger, int route, int departure, int arrival) {
            this.passenger = passenger;
            this.route = route;
            this.departure = departure;
            this.arrival = arrival;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TicketBoughtRecord that = (TicketBoughtRecord) o;
            return route == that.route && departure == that.departure && arrival == that.arrival && passenger.equals(that.passenger);
        }

        @Override
        public int hashCode() {
            return Objects.hash(passenger, route, departure, arrival);
        }
    }

    public static class TicketingDSSerialCancelableImplOne extends ticketingsystem.impls.ImplOne implements SerialTicketingSystem {

        public TicketingDSSerialCancelableImplOne(TicketingDS.TicketingDSParam param) {
            super(param);
        }

        public ticketingsystem.impls.Ticket outerDoBuyTicket(String passenger, int route, int departure, int arrival) {
            if (isParamsInvalid(route, departure, arrival)) {
                return null;
            }
            int seatIdx;
            seatIdx = doBuyTicket(data[route - 1], departure, arrival);
            writeStatus(route);
            if (seatIdx == -1) {
                return null;
            }
            CoachSeatPair p = new CoachSeatPair(seatIdx);
            return buildTicket(getCurrThreadNextTid(), passenger, route, p.coach, p.seat, departure, arrival);
        }

        public boolean outerDoRefundTicket(ticketingsystem.impls.Ticket ticket) {
            if (isParamsInvalid(ticket.route, ticket.departure, ticket.arrival)) {
                return false;
            }

            boolean res = doRefundTicket(data[ticket.route - 1], ticket);
            writeStatus(ticket.route);
            return res;
        }

        public ticketingsystem.impls.Ticket buyTicket(String passenger, int route, int departure, int arrival) {
            return outerDoBuyTicket(passenger, route, departure, arrival);
        }

        public boolean refundTicket(ticketingsystem.impls.Ticket ticket) {
            return outerDoRefundTicket(ticket);
        }

    }

}
