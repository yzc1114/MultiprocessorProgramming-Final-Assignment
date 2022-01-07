package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.util.Arrays;

public class ThreadLocalViewHelper {

    private final TicketingDS.TicketingDSParam param;
    protected int[][] threadLocalView;

    public ThreadLocalViewHelper(TicketingDS.TicketingDSParam param) {
        this.param = param;
        this.threadLocalView = new int[param.THREAD_NUM][param.STATION_NUM];
        initThreadLocalView();
    }

    protected void initThreadLocalView() {
        for (int i = 0; i < param.THREAD_NUM; i++) {
            threadLocalView[i] = new int[param.STATION_NUM];
            Arrays.fill(threadLocalView[i], param.SEAT_NUM * param.COACH_NUM);
        }
    }

    protected void updateView(int mappedTheadID, int route, int departure, int arrival, boolean add) {
        int offset = add ? 1 : -1;
        for (int i = departure - 1; i <= arrival - 1; i++) {
            threadLocalView[mappedTheadID][i] += offset;
        }
    }

    public int inquiryHelper(int route, int departure, int arrival) {
        int res = param.SEAT_NUM * param.COACH_NUM;
        for (int i = 0; i < param.THREAD_NUM; i++) {
            int min = Integer.MAX_VALUE;
            for (int j = departure - 1; j <= arrival - 1; j++) {
                min = Math.min(threadLocalView[i][j], min);
            }
            res -= min;
        }
        return param.SEAT_NUM * param.COACH_NUM - res;
    }
}