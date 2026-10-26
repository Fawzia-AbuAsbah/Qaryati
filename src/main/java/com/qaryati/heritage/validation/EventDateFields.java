package com.qaryati.heritage.validation;

import com.qaryati.heritage.entity.DatePrecision;
import java.time.LocalDate;

public interface EventDateFields {
    DatePrecision getDatePrecision();
    LocalDate getEventDate();
    Integer getYearFrom();
    Integer getYearTo();
}
