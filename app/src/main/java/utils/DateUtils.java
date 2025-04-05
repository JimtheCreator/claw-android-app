package utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    public static String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM", Locale.ENGLISH);
        return sdf.format(new Date());
    }
}

