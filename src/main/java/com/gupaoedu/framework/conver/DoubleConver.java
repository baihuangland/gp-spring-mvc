package com.gupaoedu.framework.conver;


public class DoubleConver implements Conver {


    @Override
    public Object cover(String value) {


        try {
            return Double.parseDouble(value);

        } catch (RuntimeException e) {
            e.printStackTrace();
            throw new RuntimeException(value + "CAN NOT CAST TO DOUBLE");
        }
    }
}
