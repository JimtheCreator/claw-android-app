package utils;

import java.util.HashMap;
import java.util.Map;

public class WatchListPlanLimits {

    public static Map<String, Integer> getWatchlistLimits() {
        Map<String, Integer> limits = new HashMap<>();
        limits.put("test_drive", 1);
        limits.put("starter_weekly", 3);
        limits.put("starter_monthly", 6);
        limits.put("pro_weekly", -1);
        limits.put("pro_monthly", -1);
        limits.put("free", 1);
        return limits;
    }

}
