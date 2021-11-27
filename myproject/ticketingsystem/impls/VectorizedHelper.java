package ticketingsystem.impls;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class VectorizedHelper {
    static final VectorSpecies<Integer> INTEGER_VECTOR_SPECIES = IntVector.SPECIES_PREFERRED;
    static final VectorSpecies<Long> LONG_VECTOR_SPECIES = LongVector.SPECIES_PREFERRED;

    // 向量化的整数数组运算。
    // 这个运算对于data中的每个元素，对它与mask进行相与，计算相与之后不为0的元素个数。
    // 实际上就是计算出了座位不为空的元素个数。
    static int intVectorMasked(int[] data, int mask) {
        int i = 0;
        int upperBound = INTEGER_VECTOR_SPECIES.loopBound(data.length);
        int sum = 0;
        for (; i < upperBound; i += INTEGER_VECTOR_SPECIES.length()) {
            var va = IntVector.fromArray(INTEGER_VECTOR_SPECIES, data, i);
            sum += va.and(mask).compare(VectorOperators.NE, 0).trueCount();
        }
        for (; i < data.length; i++) {
            if ((data[i] & mask) != 0) {
                sum++;
            }
        }
        return sum;
    }

    static int longMultiVectorsMasked(long[] data, long[] masks) {
        int i = 0;
        int upperBound = LONG_VECTOR_SPECIES.loopBound(data.length);
        int sum = 0;
        for (; i < upperBound; i += LONG_VECTOR_SPECIES.length()) {
            var va = LongVector.fromArray(LONG_VECTOR_SPECIES, data, i);
            for (long mask : masks) {
                sum += va.and(mask).compare(VectorOperators.NE, 0).trueCount();
            }
        }
        for (; i < data.length; i++) {
            for (long mask : masks) {
                if ((data[i] & mask) != 0) {
                    sum++;
                }
            }
        }
        return sum;
    }

    public static void main(String[] args) {
        int[] data = new int[32];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        for (int datum : data) {
            System.out.println(Integer.toBinaryString(datum));
        }
        int res = intVectorMasked(data, 0b111);
        System.out.println(res);
    }


}
