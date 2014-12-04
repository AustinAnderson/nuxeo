package org.nuxeo.ecm.core.schema.types.constraints;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

/**
 * Some usefull utils for Constraint API.
 *
 * @since 7.1
 */
public final class ConstraintUtils {

    public static final String DATE_FORMAT = "yyyy-MM-dd";

    private ConstraintUtils() {
    }

    /**
     * @return a date formatter xsd compliant : {@value #DATE_FORMAT}
     *
     * @since 7.1
     */
    public static SimpleDateFormat formatter() {
        return new SimpleDateFormat(DATE_FORMAT);
    }

    /**
     * Supports {@link Date}, {@link Calendar}, {@link Number} and
     * {@link String} formatted as YYYY-MM-DD
     * 
     * @param object Any object
     * @return a date represented as number of milliseconds since january 1 1970
     *         if the object is supported, null otherwise.
     *
     * @since 7.1
     */
    public static Long objectToTimeMillis(Object object) {
        Long timeValue = null;
        if (object == null) {
            return null;
        }
        if (object instanceof Date) {
            timeValue = ((Date) object).getTime();
        } else if (object instanceof Calendar) {
            timeValue = ((Calendar) object).getTimeInMillis();
        } else if (object instanceof Number) {
            timeValue = ((Number) object).longValue();
        } else {
            SimpleDateFormat dateParser = ConstraintUtils.formatter();
            try {
                timeValue = dateParser.parse(object.toString()).getTime();
            } catch (ParseException e) {
                return null;
            }
        }
        return timeValue;
    }

    /**
     * Supports any object which toString method return a numeric as String.
     * 
     * @param object Any object
     * @return a BigDecimal if the object represent a number, null otherwise.
     *
     * @since 7.1
     */
    public static BigDecimal objectToBigDecimal(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return new BigDecimal(object.toString());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Supports any object which toString method return a positive numeric as
     * String.
     * 
     * @param object Any object
     * @return a positive long value (rounded if needed) if the object represent
     *         a positive numeric, null otherwise.
     *
     * @since 7.1
     */
    public static Long objectToPostiveLong(Object object) {
        if (object == null) {
            return null;
        }
        try {
            Long result = Long.parseLong(object.toString());
            if (result >= 0) {
                return result;
            } else {
                return null;
            }
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Constraint> T getConstraint(
            Collection<Constraint> constraints, Class<T> constraintClass) {
        for (Constraint constraint : constraints) {
            if (constraint.getClass().equals(constraintClass)) {
                return (T) constraint;
            }
        }
        return null;
    }

}
