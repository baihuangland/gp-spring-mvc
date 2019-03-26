package com.gupaoedu.framework.conver;


public class IntegerConver implements Conver {


    @Override
    public Object cover(String value) {


        try {
            return Integer.parseInt(value);

        } catch (RuntimeException e) {
            e.printStackTrace();
            throw new RuntimeException(value + "CAN NOT CAST TO INTEGER");
        }
    }
}
