package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

class ViewHelper {
    protected AtomicReference<int[]>[] views = null;
    private TicketingDS.TicketingDSParam param;

    public ViewHelper(TicketingDS.TicketingDSParam param) {
        this.param = param;
        initViews();
    }

    protected void initViews() {
        views = new AtomicReference[param.ROUTE_NUM];
        for (int i = 0; i < param.ROUTE_NUM; i++) {
            int[] view = new int[param.STATION_NUM];
            Arrays.fill(view, param.COACH_NUM * param.SEAT_NUM);
            views[i] = new AtomicReference<>(view);
        }
    }

    protected int readEmptyView(int route, int departure, int arrival) {
        int[] view = views[route - 1].getOpaque();
        int min = Integer.MAX_VALUE;
        for (int i = departure - 1; i <= arrival - 1; i++) {
            min = Math.min(view[i], min);
        }
        return min;
    }

    protected boolean setView(int route, int departure, int arrival, boolean toBeOccupied) {
        int offset = toBeOccupied ? -1 : 1;
        AtomicReference<int[]> view = views[route - 1];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int backoff = 2;
        int backoffMax = 32;
        int[] origin = null;
        while (true) {
            if (origin == null) {
                origin = view.getPlain();
            }
            int[] cloned = origin.clone();
            for (int i = departure - 1; i <= arrival - 1; i++) {
                cloned[i] += offset;
                if (cloned[i] < 0) {
                    return false;
                }
            }
            int[] exchange = view.compareAndExchange(origin, cloned);
            if (origin == exchange) {
                return true;
            }
            origin = exchange;
            try {
                Thread.sleep(0, backoff);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (backoff < backoffMax) {
                backoff *= 2;
            }
        }

    }

}
