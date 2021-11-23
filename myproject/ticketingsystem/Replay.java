package ticketingsystem;

import java.io.*;
import java.util.*;


public class Replay {
    static int threadNum;
    static List<String> methodList = new ArrayList<String>();

    /**********Manually Modified ***********/
    static boolean isPosttime = true;
    static boolean detail = false;
    final static int routenum = 3;
    final static int coachnum = 3;
    final static int seatnum = 3;
    final static int stationnum = 3;

    static int debugMode = 1;

    static ArrayList<HistoryLine> history = new ArrayList<HistoryLine>();
    static TicketingDS object;

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

        ;
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

        ;
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

    }

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
        historyList.add(tl);
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
                if (parseline(historyList, scanner.nextLine()) == false) {
                    scanner.close();
                    System.out.println("Error in parsing line " + i);
                    return false;
                }
                i++;
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            System.out.println(e);
        }
        return true;
    }

    private static boolean checkline(ArrayList<HistoryLine> historyList, int index) {
        HistoryLine line = historyList.get(index);

        if (debugMode == 1) {
            if (index == 54) {
                System.out.println("Debugging line " + index + " ");
            }
        }


        for (int i = 0; i < methodList.size(); i++) {
            if (line.operationName.equals(methodList.get(i))) {
                boolean flag = execute(methodList.get(i), line, index);
//		System.out.println("Line " + index + " executing " + methodList.get(i) + " res: " + flag + " tid = " + line.tid);
                return flag;
            }
        }
        return false;

    }


    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws InterruptedException {
        if (args.length != 4) {
            System.out.println("The parameter list of VeriLin is threadNum, historyFile, isPosttime(0/1), failedTrace.");
            return;
        }
        threadNum = Integer.parseInt(args[0]);
        String fileName = args[1];
        if (Integer.parseInt(args[2]) == 0) {
            isPosttime = false;
        } else if (Integer.parseInt(args[2]) == 1) {
            isPosttime = true;
        } else {
            System.out.println("The parameter list of VeriLin is threadNum, historyFile, isPosttime(0/1), failedTrace.");
            return;
        }
        String ft = args[3];
        long startMs, endMs;
        readHistory(history, fileName);
        initialization();
        startMs = System.currentTimeMillis();
        if (!isPosttime) {
            hl_Comparator_1 com1 = new hl_Comparator_1();
            Collections.sort(history, com1);
        } else {
            hl_Comparator_2 com2 = new hl_Comparator_2();
            Collections.sort(history, com2);
        }

        writeHistoryToFile(history, ft);

        for (int i = 0; i < history.size(); i++) {
            if (!checkline(history, i)) {
                System.out.println("checkLine returns FALSE in line " + i);
                break;
            }
        }
        endMs = System.currentTimeMillis();
        System.out.println("checking time = " + (endMs - startMs));

    }
}
