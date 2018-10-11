package com.iexec.core.utils;

import java.util.Calendar;
import java.util.Date;

public class DateTimeUtils {

    private DateTimeUtils(){
            throw new UnsupportedOperationException();
    }

    public static Date addMinutesToDate(Date date, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, minutes);
        return calendar.getTime();
    }

}
