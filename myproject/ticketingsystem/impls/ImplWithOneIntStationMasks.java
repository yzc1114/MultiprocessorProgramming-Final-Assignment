package ticketingsystem.impls;

import ticketingsystem.TicketingDS;

public abstract class ImplWithOneIntStationMasks extends ImplCommon {

    protected int[] stationMasks;
    protected int[][] dep2arrOnesMasks;

    protected void initMasks() {
        int maxStation = 32;
        stationMasks = new int[maxStation];
        for (int i = 0; i < maxStation; i++) {
            stationMasks[i] = 1 << i;
        }
        dep2arrOnesMasks = new int[maxStation][];
        for (int i = 0; i < dep2arrOnesMasks.length; i++) {
            dep2arrOnesMasks[i] = new int[maxStation];
        }
        for (int i = 0; i < maxStation; i++) {
            for (int j = i + 1; j < maxStation; j++) {
                int l = j - i;
                int ones = ((~0) >>> (maxStation - l));
                ones <<= i;
                dep2arrOnesMasks[i][j] = ones;
            }
        }
    }

    public ImplWithOneIntStationMasks(TicketingDS.TicketingDSParam param) {
        super(param);
    }
}
