package com.gupaoedu.framework.conver;

public class ConverFactory {

    public static Conver getConver(Class<?> type) {
        if (type == String.class) {
            return new StringConver();
        } else if (type == Integer.class) {
            return new IntegerConver();
        } else if (type == Double.class) {
            return new DoubleConver();
        } else if (type == int.class) {
            return new IntegerConver();
        } else if (type == double.class) {
            return new DoubleConver();
        }
        return null;
    }
}
