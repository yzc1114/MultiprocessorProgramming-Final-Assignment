package ticketingsystem;

public class Sample {
    public static void main(String[] args) {
        DepArr[] res = extractEmptyParts(0b1101100011);
        for (DepArr re : res) {
            if (re == null) {
                continue;
            }
            System.out.println(re.toString());
        }
    }

    protected static class DepArr {
        int dep;
        int arr;

        public DepArr(int dep, int arr) {
            this.dep = dep;
            this.arr = arr;
        }

        public boolean hold(int otherDep, int otherArr) {
            return dep <= otherDep && arr >= otherArr;
        }

        @Override
        public String toString() {
            return "DepArr{" +
                    "dep=" + dep +
                    ", arr=" + arr +
                    '}';
        }
    }

    protected static DepArr[] extractEmptyParts(int seatInfo) {
        int startEmpty = -1;
        int endEmpty = -1;
        DepArr[] emptyParts = new DepArr[10 / 2];
        int emptyPartsIdx = 0;
        for (int i = 0; i < 10; i++) {
            int o = (seatInfo & (1 << i));
            if (o == 0) {
                if (startEmpty == -1) {
                    startEmpty = i;
                }
            } else {
                if (startEmpty != -1) {
                    endEmpty = i - 1;
                    if (endEmpty == startEmpty) {
                        startEmpty = -1;
                        continue;
                    }
                    emptyParts[emptyPartsIdx++] = new DepArr(startEmpty, endEmpty);
                    startEmpty = -1;
                    endEmpty = -1;
                }
            }
        }
        if (startEmpty != -1) {
            emptyParts[emptyPartsIdx] = new DepArr(startEmpty, 10 - 1);
        }
        return emptyParts;
    }

}
