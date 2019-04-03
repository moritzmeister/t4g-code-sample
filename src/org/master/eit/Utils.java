package org.master.eit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class Utils {

    /**
     * Evaluates the average over an ArrayList of float values.
     *
     * @param times ArrayList to compute the average on.
     * @return Average over the input ArrayList
     */
    public static float calculateAverage(ArrayList<Float> times) {
        float sum = 0.0f;
        if(!times.isEmpty()) {
            for (float time : times) {
                sum += time;
            }
            return sum / (float) times.size();
        }
        return sum;
    }

    /**
     * Evaluates the standard deviation over an ArrayList of float values
     *
     * @param times ArrayList to compute the standard deviation on.
     * @param average Pre-computed average for the computation.
     * @return Standard deviation over the times ArrayList.
     */
    public static float calculateStd (ArrayList<Float> times, float average)
    {
        // Step 1:
        float temp = 0;

        for (int i = 0; i < times.size(); i++)
        {
            float val = times.get(i);

            // Step 2:
            float squrDiffToMean = (float) Math.pow(val - average, 2);

            // Step 3:
            temp += squrDiffToMean;
        }

        // Step 4:
        float meanOfDiffs = temp / (float) (times.size());

        // Step 5:
        return (float) Math.sqrt(meanOfDiffs);
    }

    /**
     *
     * @param hashMap
     * @return
     */
    public static ArrayList<Float> mergeClients (ConcurrentHashMap<Integer, ArrayList<Float>> hashMap) {
        ArrayList<Float> output = new ArrayList<Float>();

        for (int i : hashMap.keySet()) {
            output.addAll(hashMap.get(i));
        }

        return output;
    }

    public static void printPercentiles (ArrayList<Float> times, String statName) {
        Collections.sort(times);

        System.out.print(statName + " time percentiles [ms]: ");
        System.out.print("\n");
        for (int p=1; p<=100; p++) {
            System.out.print(p+","+times.get(times.size()*p/100-1));
            if (p!=100) {
                System.out.print("\n");
            }
        }
        System.out.println();
    }
}
